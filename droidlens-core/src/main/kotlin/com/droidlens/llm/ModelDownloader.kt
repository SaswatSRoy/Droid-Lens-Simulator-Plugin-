package com.droidlens.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the LLM model file to the device's internal storage on first launch.
 *
 * Features:
 * - Progress reporting via [Flow<DownloadProgress>]
 * - SHA-256 checksum verification after download
 * - Resume support via HTTP Range header for interrupted downloads
 * - Thread-safe via [Mutex]
 *
 * Usage:
 * ```
 * val downloader = ModelDownloader(
 *     downloadUrl = "https://your-server.com/gemma-2b-it-cpu-int4.bin",
 *     expectedSha256 = "abc123..."
 * )
 * downloader.download(context).collect { progress ->
 *     // Update UI with progress
 * }
 * ```
 */
class ModelDownloader(
    private val downloadUrl: String,
    private val modelFileName: String = DEFAULT_MODEL_FILENAME,
    private val expectedSha256: String = ""
) {
    companion object {
        private const val TAG = "DroidLens-ModelDownloader"
        private const val DEFAULT_MODEL_FILENAME = "gemma-2b-it-cpu-int4.bin"
        private const val MODELS_DIR = "models"
        private const val BUFFER_SIZE = 8 * 1024 // 8KB buffer
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val PARTIAL_SUFFIX = ".partial"
    }

    private val downloadMutex = Mutex()

    /**
     * Returns the expected model file location within the app's internal storage.
     */
    fun getModelFile(context: Context): File {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        return File(modelsDir, modelFileName)
    }

    /**
     * Returns true if the model file exists and has a non-zero size.
     */
    fun isModelAvailable(context: Context): Boolean {
        val modelFile = getModelFile(context)
        return modelFile.exists() && modelFile.length() > 0
    }

    /**
     * Ensures the model is available, downloading it if necessary.
     * Returns the path to the model file.
     *
     * @throws IllegalStateException if the download URL is empty
     * @throws ModelDownloadException if the download fails
     */
    suspend fun ensureModelAvailable(context: Context): File {
        val modelFile = getModelFile(context)
        if (modelFile.exists() && modelFile.length() > 0) {
            Log.d(TAG, "Model already available at: ${modelFile.absolutePath}")
            return modelFile
        }

        if (downloadUrl.isBlank()) {
            throw IllegalStateException(
                "Model file not found and no download URL configured. " +
                "Either push the model via ADB to ${modelFile.absolutePath} " +
                "or configure a download URL in DroidLensConfig."
            )
        }

        // Collect the download flow to completion
        var lastError: String? = null
        download(context).collect { progress ->
            if (progress.error != null) {
                lastError = progress.error
            }
        }

        if (lastError != null) {
            throw ModelDownloadException("Model download failed: $lastError")
        }

        if (!modelFile.exists() || modelFile.length() == 0L) {
            throw ModelDownloadException("Model file missing after download completion")
        }

        return modelFile
    }

    /**
     * Downloads the model file with progress reporting.
     * Supports resume if a partial download exists.
     * Verifies SHA-256 checksum after download if [expectedSha256] is provided.
     */
    fun download(context: Context): Flow<DownloadProgress> = flow {
        downloadMutex.withLock {
            val modelsDir = File(context.filesDir, MODELS_DIR)
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }

            val modelFile = File(modelsDir, modelFileName)
            val partialFile = File(modelsDir, "$modelFileName$PARTIAL_SUFFIX")

            // If the model already exists and is valid, skip download
            if (modelFile.exists() && modelFile.length() > 0) {
                Log.d(TAG, "Model already exists, skipping download")
                emit(DownloadProgress.complete())
                return@withLock
            }

            try {
                val existingBytes = if (partialFile.exists()) partialFile.length() else 0L
                Log.d(TAG, "Starting download from: $downloadUrl (resuming from byte $existingBytes)")

                val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = "GET"

                    // Resume from where we left off
                    if (existingBytes > 0) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                }

                val responseCode = connection.responseCode
                val isResuming = responseCode == HttpURLConnection.HTTP_PARTIAL
                val totalBytes: Long

                if (isResuming) {
                    // Server supports range — content-length is the remaining bytes
                    totalBytes = existingBytes + connection.contentLengthLong
                    Log.d(TAG, "Resuming download from byte $existingBytes, total: $totalBytes")
                } else if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Fresh download (server may not support range, or existingBytes was 0)
                    totalBytes = connection.contentLengthLong
                    // If we had a partial file but server doesn't support range, start over
                    if (existingBytes > 0) {
                        partialFile.delete()
                    }
                    Log.d(TAG, "Starting fresh download, total: $totalBytes bytes")
                } else {
                    val errorMsg = "HTTP error $responseCode: ${connection.responseMessage}"
                    Log.e(TAG, errorMsg)
                    emit(DownloadProgress.error(errorMsg))
                    connection.disconnect()
                    return@withLock
                }

                emit(DownloadProgress(
                    bytesDownloaded = if (isResuming) existingBytes else 0,
                    totalBytes = totalBytes
                ))

                // Write to partial file
                connection.inputStream.use { inputStream ->
                    val outputStream = if (isResuming) {
                        FileOutputStream(partialFile, true) // append mode
                    } else {
                        FileOutputStream(partialFile, false) // overwrite
                    }

                    outputStream.use { out ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var downloaded = if (isResuming) existingBytes else 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            emit(DownloadProgress(
                                bytesDownloaded = downloaded,
                                totalBytes = totalBytes
                            ))
                        }

                        out.flush()
                    }
                }

                connection.disconnect()

                // Verify checksum if provided
                if (expectedSha256.isNotBlank()) {
                    Log.d(TAG, "Verifying SHA-256 checksum...")
                    val actualSha256 = computeSha256(partialFile)
                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        val errorMsg = "Checksum mismatch! Expected: $expectedSha256, Got: $actualSha256"
                        Log.e(TAG, errorMsg)
                        partialFile.delete()
                        emit(DownloadProgress.error(errorMsg))
                        return@withLock
                    }
                    Log.d(TAG, "Checksum verified successfully")
                }

                // Rename partial to final
                if (partialFile.renameTo(modelFile)) {
                    Log.d(TAG, "Model downloaded successfully to: ${modelFile.absolutePath}")
                    emit(DownloadProgress(
                        bytesDownloaded = modelFile.length(),
                        totalBytes = modelFile.length(),
                        isComplete = true
                    ))
                } else {
                    val errorMsg = "Failed to rename partial file to final model file"
                    Log.e(TAG, errorMsg)
                    emit(DownloadProgress.error(errorMsg))
                }

            } catch (e: Exception) {
                val errorMsg = "Download failed: ${e.message}"
                Log.e(TAG, errorMsg, e)
                emit(DownloadProgress.error(errorMsg))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Computes the SHA-256 hash of a file.
     */
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)

        file.inputStream().use { inputStream ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Deletes the model file and any partial downloads.
     */
    fun deleteModel(context: Context) {
        val modelsDir = File(context.filesDir, MODELS_DIR)
        File(modelsDir, modelFileName).delete()
        File(modelsDir, "$modelFileName$PARTIAL_SUFFIX").delete()
        Log.d(TAG, "Model files deleted")
    }
}

/**
 * Exception thrown when model download fails.
 */
class ModelDownloadException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
