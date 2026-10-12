package com.campusverse.app.data.local.ai

enum class AiEngineMode(val displayName: String, val description: String) {
    CLOUD("Cloud AI", "Online Gemini Pro via CampusVerse backend"),
    LOCAL("Local AI", "Private on-device inference (no internet needed)"),
    AUTO("Auto", "Cloud AI when online; instant offline fallback")
}

sealed interface ModelDownloadStatus {
    data object NotDownloaded : ModelDownloadStatus
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : ModelDownloadStatus
    data class Installed(val fileSizeBytes: Long) : ModelDownloadStatus
    data object Loading : ModelDownloadStatus
    data object Loaded : ModelDownloadStatus
    data class Error(val message: String) : ModelDownloadStatus
}

data class LocalModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedSizeBytes: Long,
    val parameterCount: String,
    val quantization: String,
    val format: String = "GGUF",
    val isRecommended: Boolean = false
) {
    val displaySize: String
        get() {
            val mb = expectedSizeBytes.toDouble() / (1024 * 1024)
            return "%.0f MB".format(mb)
        }
}

object LocalModelCatalog {
    val SMOL_LM_360M = LocalModelInfo(
        id = "smollm2-360m-instruct-q4",
        name = "SmolLM2 360M Instruct",
        description = "Recommended for mobile. Fast inference with accurate reasoning for study concepts, notes, and quiz generation.",
        fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
        expectedSizeBytes = 270_590_880L,
        parameterCount = "360M",
        quantization = "Q4_K_M",
        isRecommended = true
    )

    val SMOL_LM_360M_Q3 = LocalModelInfo(
        id = "smollm2-360m-instruct-q3",
        name = "SmolLM2 360M Compact",
        description = "Lightweight quantization for devices with constrained memory or slower download speeds.",
        fileName = "SmolLM2-360M-Instruct-Q3_K_M.gguf",
        downloadUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q3_K_M.gguf",
        expectedSizeBytes = 234_686_880L,
        parameterCount = "360M",
        quantization = "Q3_K_M",
        isRecommended = false
    )

    val QWEN_05B = LocalModelInfo(
        id = "qwen2.5-0.5b-instruct-q4",
        name = "Qwen 2.5 0.5B Instruct",
        description = "High-accuracy multilingual model for deep academic problem-solving.",
        fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        expectedSizeBytes = 491_400_032L,
        parameterCount = "0.5B",
        quantization = "Q4_K_M",
        isRecommended = false
    )

    val ALL_MODELS: List<LocalModelInfo> = listOf(
        SMOL_LM_360M,
        SMOL_LM_360M_Q3,
        QWEN_05B
    )

    fun getById(id: String): LocalModelInfo? = ALL_MODELS.find { it.id == id }
    val DEFAULT_MODEL: LocalModelInfo = SMOL_LM_360M
}
