package com.droidlens.llm

/**
 * Represents the progress of a model file download.
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    /**
     * Download progress as a percentage (0-100).
     * Returns -1 if total size is unknown.
     */
    val percentComplete: Int
        get() = if (totalBytes > 0) {
            ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            -1
        }

    companion object {
        fun initial() = DownloadProgress(bytesDownloaded = 0, totalBytes = -1)
        fun complete() = DownloadProgress(bytesDownloaded = 0, totalBytes = 0, isComplete = true)
        fun error(message: String) = DownloadProgress(bytesDownloaded = 0, totalBytes = -1, error = message)
    }
}
