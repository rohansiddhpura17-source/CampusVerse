package com.campusverse.app.ui.screens.student.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.local.ai.AiEngineMode
import com.campusverse.app.data.local.ai.LocalLlmEngine
import com.campusverse.app.data.local.ai.LocalModelCatalog
import com.campusverse.app.data.local.ai.LocalModelInfo
import com.campusverse.app.data.local.ai.LocalModelManager
import com.campusverse.app.data.local.ai.ModelDownloadStatus
import com.campusverse.app.data.model.AiStudyQueryResponse
import com.campusverse.app.data.repository.NetworkStudentRepository
import com.campusverse.app.domain.student.StudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val sender: String, // "USER" or "AI"
    val text: String,
    val isErrorOrUnavailable: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val engine: AiEngineMode? = null,
    val modelName: String? = null,
    val isFallback: Boolean = false
)

data class AiStudyAssistantUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            sender = "AI",
            text = "Hello! I am your CampusVerse AI Study Assistant. I support both Cloud AI and on-device Local AI inference (offline mode). Ask me anything about Data Structures, Algorithms, Operating Systems, Database Systems, Computer Networks, or request a summary of complex topics.",
            engine = AiEngineMode.AUTO
        )
    ),
    val isLoading: Boolean = false,
    val selectedMode: String = "EXPLAIN",
    val lastQuery: String? = null,
    val engineMode: AiEngineMode = AiEngineMode.AUTO,
    val modelStatuses: Map<String, ModelDownloadStatus> = emptyMap(),
    val activeModelId: String? = null,
    val isModelManagerOpen: Boolean = false,
    val availableStorageBytes: Long = -1L,
    val statusMessage: String? = null,
    val suggestedTopics: List<String> = listOf(
        "Dijkstra Shortest Path",
        "Database Normalization (1NF to BCNF)",
        "Process Scheduling Algorithms",
        "TCP 3-Way Handshake"
    )
)

class AiStudyAssistantViewModel(
    private val repository: StudentRepository = NetworkStudentRepository.instance,
    private val context: Context? = null,
    private val injectedModelManager: LocalModelManager? = null,
    private val injectedLocalLlmEngine: LocalLlmEngine? = null
) : ViewModel() {

    private val modelManager: LocalModelManager? = injectedModelManager ?: context?.let { LocalModelManager(it) }
    private val localLlmEngine: LocalLlmEngine? = injectedLocalLlmEngine ?: context?.let { LocalLlmEngine(it) }

    private val _uiState = MutableStateFlow(AiStudyAssistantUiState())
    val uiState: StateFlow<AiStudyAssistantUiState> = _uiState.asStateFlow()

    init {
        observeModelStatuses()
        checkAndPrepareActiveModel()
    }

    private fun observeModelStatuses() {
        modelManager?.let { manager ->
            viewModelScope.launch {
                manager.modelStatuses.collect { statuses ->
                    val storage = manager.getAvailableStorageBytes()
                    _uiState.update { current ->
                        current.copy(
                            modelStatuses = statuses,
                            availableStorageBytes = storage
                        )
                    }
                }
            }
        }
    }

    private fun checkAndPrepareActiveModel() {
        val manager = modelManager ?: return
        manager.refreshStatuses()
        val savedId = loadActiveModelId()
        val candidateModel = (savedId?.let { LocalModelCatalog.getById(it) }?.takeIf { manager.isModelInstalled(it.id) })
            ?: LocalModelCatalog.ALL_MODELS.find { manager.isModelInstalled(it.id) }

        if (candidateModel != null) {
            _uiState.update { it.copy(activeModelId = candidateModel.id) }
            if (localLlmEngine?.isReady() != true) {
                loadModel(candidateModel.id)
            }
        } else {
            _uiState.update { it.copy(activeModelId = LocalModelCatalog.DEFAULT_MODEL.id) }
        }
    }

    fun setMode(mode: String) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun setEngineMode(engineMode: AiEngineMode) {
        _uiState.update { it.copy(engineMode = engineMode) }
    }

    fun setOpenModelManager(open: Boolean) {
        if (open) {
            modelManager?.refreshStatuses()
            val storage = modelManager?.getAvailableStorageBytes() ?: -1L
            _uiState.update { it.copy(isModelManagerOpen = true, availableStorageBytes = storage) }
        } else {
            _uiState.update { it.copy(isModelManagerOpen = false) }
        }
    }

    fun downloadModel(modelId: String) {
        val manager = modelManager ?: return
        val model = LocalModelCatalog.getById(modelId) ?: return
        manager.downloadModel(model)
    }

    fun cancelDownload(modelId: String) {
        modelManager?.cancelDownload(modelId)
    }

    fun deleteModel(modelId: String) {
        if (_uiState.value.activeModelId == modelId) {
            localLlmEngine?.unload()
        }
        modelManager?.deleteModel(modelId)
    }

    private var activeLoadJob: kotlinx.coroutines.Job? = null

    fun loadModel(modelId: String) {
        val manager = modelManager ?: return
        val engine = localLlmEngine ?: return
        val model = LocalModelCatalog.getById(modelId) ?: return

        if (activeLoadJob?.isActive == true) {
            _uiState.update { it.copy(statusMessage = "Activation already in progress...") }
            return
        }

        if (engine.isReady() && engine.getLoadedModelId() == modelId) {
            manager.setStatus(modelId, ModelDownloadStatus.Loaded)
            _uiState.update {
                it.copy(
                    activeModelId = modelId,
                    statusMessage = "Ready: ${model.name} is loaded in memory."
                )
            }
            return
        }

        activeLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Checking model: ${model.name}...") }
            kotlinx.coroutines.delay(100)

            _uiState.update { it.copy(statusMessage = "Resolving model file on device storage...") }
            val file = manager.getModelFile(modelId)
            if (file == null || !LocalModelManager.isValidGguf(file)) {
                manager.setStatus(modelId, ModelDownloadStatus.Error("Model file missing or corrupted"))
                _uiState.update {
                    it.copy(statusMessage = "Activation failed: Model file missing or corrupted.")
                }
                return@launch
            }

            _uiState.update { it.copy(statusMessage = "Initializing inference runtime...") }
            manager.setStatus(modelId, ModelDownloadStatus.Loading)
            kotlinx.coroutines.delay(100)

            _uiState.update { it.copy(statusMessage = "Loading ${model.name} into memory...") }
            val result = engine.loadModel(model, file)

            if (result.isSuccess) {
                // Update statuses across all catalog entries
                for (other in LocalModelCatalog.ALL_MODELS) {
                    if (other.id == modelId) {
                        manager.setStatus(modelId, ModelDownloadStatus.Loaded)
                    } else if (manager.isModelInstalled(other.id)) {
                        val otherFile = manager.getModelFile(other.id)
                        manager.setStatus(other.id, ModelDownloadStatus.Installed(otherFile?.length() ?: 0L))
                    }
                }

                saveActiveModelId(modelId)

                _uiState.update {
                    it.copy(
                        activeModelId = modelId,
                        statusMessage = "Ready: ${model.name} loaded and active for on-device inference."
                    )
                }
            } else {
                manager.setStatus(modelId, ModelDownloadStatus.Installed(file.length()))
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _uiState.update {
                    it.copy(statusMessage = "Activation failed: $errorMsg")
                }
            }
        }
    }

    private fun saveActiveModelId(modelId: String) {
        try {
            context?.getSharedPreferences("campusverse_local_ai", Context.MODE_PRIVATE)
                ?.edit()
                ?.putString("active_model_id", modelId)
                ?.apply()
        } catch (_: Exception) {}
    }

    private fun loadActiveModelId(): String? {
        return try {
            context?.getSharedPreferences("campusverse_local_ai", Context.MODE_PRIVATE)
                ?.getString("active_model_id", null)
        } catch (_: Exception) {
            null
        }
    }

    fun retry() {
        val last = _uiState.value.lastQuery
        if (!last.isNullOrBlank() && !_uiState.value.isLoading) {
            submitQuery(last)
        }
    }

    fun submitQuery(query: String) {
        if (query.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(sender = "USER", text = query.trim())
        val updatedMessages = _uiState.value.messages + userMessage

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                isLoading = true,
                lastQuery = query.trim(),
                statusMessage = null
            )
        }

        viewModelScope.launch {
            when (_uiState.value.engineMode) {
                AiEngineMode.LOCAL -> executeLocalQuery(query.trim(), isFallback = false)
                AiEngineMode.CLOUD -> executeCloudQuery(query.trim(), allowFallback = false)
                AiEngineMode.AUTO -> executeCloudQuery(query.trim(), allowFallback = true)
            }
        }
    }

    private suspend fun executeLocalQuery(query: String, isFallback: Boolean) {
        val manager = modelManager
        val activeId = _uiState.value.activeModelId ?: LocalModelCatalog.DEFAULT_MODEL.id
        val modelInfo = LocalModelCatalog.getById(activeId) ?: LocalModelCatalog.DEFAULT_MODEL

        // Check if model file is installed
        val modelFile = manager?.getModelFile(activeId)
        if (modelFile == null || manager?.isModelInstalled(activeId) != true) {
            val errorMsg = ChatMessage(
                sender = "AI",
                text = "Local AI model (${modelInfo.name}) is not downloaded. Open the Model Manager to download the on-device model (${modelInfo.displaySize}) for offline study.",
                isErrorOrUnavailable = true,
                engine = AiEngineMode.LOCAL,
                modelName = modelInfo.name,
                isFallback = isFallback
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + errorMsg,
                    isLoading = false,
                    isModelManagerOpen = true
                )
            }
            return
        }

        val engine = localLlmEngine
        if (engine == null) {
            val errorMsg = ChatMessage(
                sender = "AI",
                text = "Local AI engine is not initialized on this environment.",
                isErrorOrUnavailable = true,
                engine = AiEngineMode.LOCAL
            )
            _uiState.update { it.copy(messages = it.messages + errorMsg, isLoading = false) }
            return
        }

        // Ensure loaded
        if (!engine.isReady() || engine.getLoadedModelId() != activeId) {
            val loadResult = engine.loadModel(modelInfo, modelFile)
            if (loadResult.isFailure) {
                val errorMsg = ChatMessage(
                    sender = "AI",
                    text = "Failed to load ${modelInfo.name}: ${loadResult.exceptionOrNull()?.message}",
                    isErrorOrUnavailable = true,
                    engine = AiEngineMode.LOCAL,
                    modelName = modelInfo.name,
                    isFallback = isFallback
                )
                _uiState.update { it.copy(messages = it.messages + errorMsg, isLoading = false) }
                return
            }
        }

        // Run local inference
        val result = engine.generateResponse(
            query = query,
            mode = _uiState.value.selectedMode
        )

        val aiMessage = if (result.isSuccess) {
            val responseText = result.getOrThrow()
            ChatMessage(
                sender = "AI",
                text = if (isFallback) "⚡ [Offline Fallback — Local On-Device AI]\n\n$responseText" else responseText,
                engine = AiEngineMode.LOCAL,
                modelName = modelInfo.name,
                isFallback = isFallback
            )
        } else {
            ChatMessage(
                sender = "AI",
                text = "Local inference error: ${result.exceptionOrNull()?.message ?: "Unknown error"}",
                isErrorOrUnavailable = true,
                engine = AiEngineMode.LOCAL,
                modelName = modelInfo.name,
                isFallback = isFallback
            )
        }

        _uiState.update {
            it.copy(
                messages = it.messages + aiMessage,
                isLoading = false
            )
        }
    }

    private suspend fun executeCloudQuery(query: String, allowFallback: Boolean) {
        try {
            val result = repository.askAiStudyAssistant(
                query = query,
                mode = _uiState.value.selectedMode
            )
            val response: AiStudyQueryResponse? = result.getOrNull()

            if (response != null && response.available && !response.response.isNullOrBlank()) {
                val aiMessage = ChatMessage(
                    sender = "AI",
                    text = response.response,
                    engine = AiEngineMode.CLOUD,
                    modelName = "Gemini Pro"
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages + aiMessage,
                        isLoading = false,
                        suggestedTopics = response.suggestedTopics?.ifEmpty { it.suggestedTopics } ?: it.suggestedTopics
                    )
                }
            } else {
                // Cloud returned unavailable or error
                if (allowFallback && hasLocalModelReadyOrInstalled()) {
                    executeLocalQuery(query, isFallback = true)
                } else {
                    val fallbackText = if (response?.message != null) {
                        response.message
                    } else if (allowFallback) {
                        "Cloud AI is currently unavailable. No local model is installed yet. Open Model Manager to download a model for offline use."
                    } else {
                        "Cloud AI Study Assistant is currently unavailable on this server."
                    }
                    val aiMessage = ChatMessage(
                        sender = "AI",
                        text = fallbackText,
                        isErrorOrUnavailable = true,
                        engine = AiEngineMode.CLOUD
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + aiMessage,
                            isLoading = false
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Network connection error, timeout, or offline
            if (allowFallback && hasLocalModelReadyOrInstalled()) {
                executeLocalQuery(query, isFallback = true)
            } else {
                val errorMsg = ChatMessage(
                    sender = "AI",
                    text = if (allowFallback) {
                        "Network connection unavailable. To study offline, open Model Manager and download a local on-device model."
                    } else {
                        "Unable to connect to Cloud AI service: ${e.message}"
                    },
                    isErrorOrUnavailable = true,
                    engine = AiEngineMode.CLOUD
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages + errorMsg,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun hasLocalModelReadyOrInstalled(): Boolean {
        if (localLlmEngine?.isReady() == true) return true
        val manager = modelManager ?: return false
        return LocalModelCatalog.ALL_MODELS.any { manager.isModelInstalled(it.id) }
    }

    fun restartChat() {
        _uiState.update {
            AiStudyAssistantUiState(
                engineMode = it.engineMode,
                modelStatuses = it.modelStatuses,
                activeModelId = it.activeModelId,
                availableStorageBytes = it.availableStorageBytes
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        localLlmEngine?.unload()
    }
}
