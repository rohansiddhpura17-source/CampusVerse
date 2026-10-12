package com.campusverse.app.data.local.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File

class LocalLlmEngine(
    private val context: Context,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        private const val TAG = "LocalLlmEngine"
        private const val DEFAULT_CONTEXT_SIZE = 2048
    }

    private val engineScope = CoroutineScope(defaultDispatcher + SupervisorJob())
    private val mutex = Mutex()

    private var llamaHelper: LlamaHelper? = null
    private var activeModelId: String? = null
    private var isModelLoaded: Boolean = false

    fun isReady(): Boolean = isModelLoaded && llamaHelper != null
    fun getLoadedModelId(): String? = activeModelId

    suspend fun loadModel(modelInfo: LocalModelInfo, modelFile: File): Result<Unit> = mutex.withLock {
        withContext(defaultDispatcher) {
            try {
                if (!modelFile.exists() || !LocalModelManager.isValidGguf(modelFile)) {
                    return@withContext Result.failure(
                        IllegalStateException("Model file does not exist or is not a valid GGUF binary: ${modelFile.name}")
                    )
                }

                if (isModelLoaded && activeModelId == modelInfo.id) {
                    return@withContext Result.success(Unit)
                }

                // Unload previous instance if any
                unloadInternal()

                val fileUriString = Uri.fromFile(modelFile).toString()
                Log.d(TAG, "Loading local GGUF model: ${modelInfo.name} from $fileUriString")

                val sharedFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 128)
                val helper = LlamaHelper(
                    contentResolver = context.contentResolver,
                    scope = engineScope,
                    sharedFlow = sharedFlow
                )
                llamaHelper = helper

                var loadSuccess = false
                var loadError: String? = null

                // Launch load
                val loadJob = launch {
                    helper.sharedFlow.collect { event ->
                        when (event) {
                            is LlamaHelper.LLMEvent.Loaded -> {
                                Log.i(TAG, "Model loaded successfully: ${event.path}")
                                loadSuccess = true
                            }
                            is LlamaHelper.LLMEvent.Error -> {
                                Log.e(TAG, "Error loading model: ${event.message}")
                                loadError = event.message
                            }
                            else -> Unit
                        }
                    }
                }

                helper.load(
                    fileUriString,
                    DEFAULT_CONTEXT_SIZE,
                    null,
                    { contextId: Long ->
                        Log.d(TAG, "Model loaded with context ID: $contextId")
                        loadSuccess = true
                    }
                )

                // Wait for Loaded or Error event (up to 45 seconds)
                val startTime = System.currentTimeMillis()
                while (!loadSuccess && loadError == null && (System.currentTimeMillis() - startTime) < 45000) {
                    kotlinx.coroutines.delay(100)
                }
                loadJob.cancel()

                if (loadSuccess) {
                    activeModelId = modelInfo.id
                    isModelLoaded = true
                    Result.success(Unit)
                } else {
                    val err = loadError ?: "Timed out loading model into RAM"
                    unloadInternal()
                    Result.failure(IllegalStateException(err))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during loadModel: ${e.message}", e)
                unloadInternal()
                Result.failure(e)
            }
        }
    }

    suspend fun generateResponse(
        query: String,
        mode: String,
        onToken: ((String) -> Unit)? = null
    ): Result<String> = mutex.withLock {
        withContext(defaultDispatcher) {
            val helper = llamaHelper ?: return@withContext Result.failure(
                IllegalStateException("Local AI model is not loaded. Please download and activate a model first.")
            )

            if (!isModelLoaded) {
                return@withContext Result.failure(
                    IllegalStateException("Local AI model is not currently ready.")
                )
            }

            try {
                val formattedPrompt = buildChatPrompt(query, mode)
                val fullResponseBuilder = StringBuilder()
                var errorOccurred: String? = null
                var isDone = false

                val collectorJob = launch {
                    helper.sharedFlow.collect { event ->
                        when (event) {
                            is LlamaHelper.LLMEvent.Started -> {
                                Log.d(TAG, "Inference started")
                            }
                            is LlamaHelper.LLMEvent.Ongoing -> {
                                val token = event.word
                                fullResponseBuilder.append(token)
                                onToken?.invoke(token)
                            }
                            is LlamaHelper.LLMEvent.Done -> {
                                Log.d(TAG, "Inference completed in ${event.duration}ms, tokens: ${event.tokenCount}")
                                isDone = true
                            }
                            is LlamaHelper.LLMEvent.Error -> {
                                Log.e(TAG, "Inference error: ${event.message}")
                                errorOccurred = event.message
                                isDone = true
                            }
                            else -> Unit
                        }
                    }
                }

                // Start inference
                helper.predict(
                    prompt = formattedPrompt,
                    null, // no multimodal image
                    true  // stream partial tokens
                )

                // Wait until Done or Error or timeout (60s)
                val startMs = System.currentTimeMillis()
                while (!isDone && (System.currentTimeMillis() - startMs) < 90000) {
                    kotlinx.coroutines.delay(50)
                }
                collectorJob.cancel()

                if (errorOccurred != null) {
                    Result.failure(Exception("Local inference error: $errorOccurred"))
                } else {
                    val cleaned = cleanResponse(fullResponseBuilder.toString())
                    Result.success(cleaned.ifBlank { "I was unable to generate an explanation for this query." })
                }
            } catch (e: CancellationException) {
                helper.stopPrediction()
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Inference exception: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    fun stopGeneration() {
        llamaHelper?.stopPrediction()
    }

    fun unload() {
        unloadInternal()
    }

    private fun unloadInternal() {
        try {
            llamaHelper?.stopPrediction()
            llamaHelper?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing llamaHelper: ${e.message}")
        } finally {
            llamaHelper = null
            activeModelId = null
            isModelLoaded = false
        }
    }

    private fun buildChatPrompt(query: String, mode: String): String {
        val systemInstruction = when (mode) {
            "EXPLAIN" -> """
                You are CampusVerse AI, an expert computer science and engineering professor. Explain the concept clearly and concisely:
                1. Core Definition
                2. How it works (step-by-step)
                3. Concrete Example
                4. Key Takeaways
            """.trimIndent()

            "SUMMARIZE" -> """
                You are CampusVerse AI, an academic study assistant. Summarize the following notes or topic into structured bullet points with key definitions, essential algorithms/formulas, and practical exam tips.
            """.trimIndent()

            "CONCEPT_QA" -> """
                You are CampusVerse AI, an academic examination specialist. Generate a practice quiz with 3 conceptual multiple-choice questions on this topic. Include 4 options (A, B, C, D) for each, clearly indicate the correct answer, and provide a brief explanation.
            """.trimIndent()

            "SOLVE_STEP_BY_STEP" -> """
                You are CampusVerse AI, a technical tutor. Provide a step-by-step problem-solving breakdown for this concept or question.
            """.trimIndent()

            else -> """
                You are CampusVerse AI, a knowledgeable, helpful, and concise university study assistant. Provide clear, accurate, and direct answers to academic questions.
            """.trimIndent()
        }

        // ChatML format compatible with SmolLM2 and Qwen2.5
        return buildString {
            append("<|im_start|>system\n")
            append(systemInstruction.trim())
            append("\n<|im_end|>\n")
            append("<|im_start|>user\n")
            append(query.trim())
            append("\n<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    private fun cleanResponse(raw: String): String {
        return raw
            .replace("<|im_end|>", "")
            .replace("<|endoftext|>", "")
            .replace("<|im_start|>", "")
            .trim()
    }
}
