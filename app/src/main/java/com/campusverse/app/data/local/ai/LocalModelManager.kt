package com.campusverse.app.data.local.ai

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class LocalModelManager(
    private val context: Context? = null,
    private val baseDir: File = context?.filesDir ?: File(System.getProperty("java.io.tmpdir"), "campusverse_models_dir"),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {
    val modelsDir: File by lazy {
        File(baseDir, "models").apply {
            if (!exists()) mkdirs()
        }
    }

    private val _modelStatuses = MutableStateFlow<Map<String, ModelDownloadStatus>>(emptyMap())
    val modelStatuses: StateFlow<Map<String, ModelDownloadStatus>> = _modelStatuses.asStateFlow()

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    init {
        refreshStatuses()
    }

    fun refreshStatuses() {
        val initialMap = mutableMapOf<String, ModelDownloadStatus>()
        val currentStatuses = _modelStatuses.value
        for (model in LocalModelCatalog.ALL_MODELS) {
            val file = File(modelsDir, model.fileName)
            val current = currentStatuses[model.id]
            if (current is ModelDownloadStatus.Loaded) {
                initialMap[model.id] = ModelDownloadStatus.Loaded
            } else if (current is ModelDownloadStatus.Downloading) {
                initialMap[model.id] = current
            } else if (file.exists() && file.length() > 0 && isValidGguf(file)) {
                initialMap[model.id] = ModelDownloadStatus.Installed(file.length())
            } else {
                initialMap[model.id] = ModelDownloadStatus.NotDownloaded
            }
        }
        _modelStatuses.value = initialMap
    }

    fun isModelInstalled(modelId: String): Boolean {
        val status = _modelStatuses.value[modelId]
        return status is ModelDownloadStatus.Installed || status is ModelDownloadStatus.Loaded
    }

    fun getModelFile(modelId: String): File? {
        val model = LocalModelCatalog.getById(modelId) ?: return null
        val file = File(modelsDir, model.fileName)
        return if (file.exists() && isValidGguf(file)) file else null
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(modelsDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Throwable) {
            -1L
        }
    }

    fun downloadModel(modelInfo: LocalModelInfo) {
        if (activeDownloadJobs.containsKey(modelInfo.id)) {
            return
        }

        val job = scope.launch(ioDispatcher) {
            val tempFile = File(modelsDir, "${modelInfo.fileName}.tmp")
            val targetFile = File(modelsDir, modelInfo.fileName)

            try {
                _modelStatuses.update {
                    it + (modelInfo.id to ModelDownloadStatus.Downloading(0L, modelInfo.expectedSizeBytes, 0))
                }

                if (tempFile.exists()) {
                    tempFile.delete()
                }

                executeDownload(
                    urlStr = modelInfo.downloadUrl,
                    targetTempFile = tempFile,
                    expectedSize = modelInfo.expectedSizeBytes,
                    modelId = modelInfo.id
                )

                // Verify file integrity
                if (!isValidGguf(tempFile)) {
                    tempFile.delete()
                    throw IllegalStateException("Downloaded file failed GGUF integrity check")
                }

                // Atomic rename
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                _modelStatuses.update {
                    it + (modelInfo.id to ModelDownloadStatus.Installed(targetFile.length()))
                }
            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                _modelStatuses.update {
                    it + (modelInfo.id to ModelDownloadStatus.NotDownloaded)
                }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                _modelStatuses.update {
                    it + (modelInfo.id to ModelDownloadStatus.Error(e.message ?: "Download failed"))
                }
            } finally {
                activeDownloadJobs.remove(modelInfo.id)
            }
        }

        activeDownloadJobs[modelInfo.id] = job
    }

    private suspend fun executeDownload(
        urlStr: String,
        targetTempFile: File,
        expectedSize: Long,
        modelId: String
    ) = withContext(ioDispatcher) {
        var currentUrl = urlStr
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            var redirects = 0
            val maxRedirects = 6
            while (redirects < maxRedirects) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "CampusVerse-Android-LLM/1.0")
                }

                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect without Location header")
                    currentUrl = location
                    connection.disconnect()
                    redirects++
                } else if (responseCode == 200 || responseCode == 206) {
                    break
                } else {
                    throw IllegalStateException("HTTP Error $responseCode: ${connection.responseMessage}")
                }
            }

            val conn = connection ?: throw IllegalStateException("Failed to open connection")
            val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else expectedSize

            inputStream = conn.inputStream
            outputStream = FileOutputStream(targetTempFile)

            val buffer = ByteArray(65536) // 64 KB buffer
            var bytesDownloaded = 0L
            var lastUpdateMs = System.currentTimeMillis()

            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                currentCoroutineContext().ensureActive()
                outputStream.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdateMs >= 250) {
                    val percent = if (totalBytes > 0) {
                        ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                    } else 0

                    _modelStatuses.update {
                        it + (modelId to ModelDownloadStatus.Downloading(bytesDownloaded, totalBytes, percent))
                    }
                    lastUpdateMs = now
                }
            }
            outputStream.flush()
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    fun cancelDownload(modelId: String) {
        activeDownloadJobs[modelId]?.cancel()
        activeDownloadJobs.remove(modelId)
    }

    fun deleteModel(modelId: String): Boolean {
        cancelDownload(modelId)
        val model = LocalModelCatalog.getById(modelId) ?: return false
        val file = File(modelsDir, model.fileName)
        val temp = File(modelsDir, "${model.fileName}.tmp")
        if (temp.exists()) temp.delete()
        val deleted = if (file.exists()) file.delete() else true
        _modelStatuses.update {
            it + (modelId to ModelDownloadStatus.NotDownloaded)
        }
        return deleted
    }

    fun setStatus(modelId: String, status: ModelDownloadStatus) {
        _modelStatuses.update {
            it + (modelId to status)
        }
    }

    companion object {
        fun isValidGguf(file: File): Boolean {
            if (!file.exists() || file.length() < 4) return false
            return try {
                FileInputStream(file).use { fis ->
                    val magic = ByteArray(4)
                    val read = fis.read(magic)
                    // Magic bytes for GGUF: 0x47, 0x47, 0x55, 0x46 ('G', 'G', 'U', 'F')
                    read == 4 &&
                            magic[0] == 0x47.toByte() &&
                            magic[1] == 0x47.toByte() &&
                            magic[2] == 0x55.toByte() &&
                            magic[3] == 0x46.toByte()
                }
            } catch (e: Exception) {
                false
            }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 MB"
            val mb = bytes.toDouble() / (1024 * 1024)
            return if (mb >= 1024) {
                "%.2f GB".format(mb / 1024)
            } else {
                "%.1f MB".format(mb)
            }
        }
    }
}
