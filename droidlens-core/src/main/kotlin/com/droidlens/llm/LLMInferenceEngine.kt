package com.droidlens.llm

import android.content.Context
import android.util.Log
import com.droidlens.config.DroidLensConfig
import com.droidlens.model.DiagnosisResult
import com.droidlens.model.Regression
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

/**
 * Wraps MediaPipe's [LlmInference] to provide on-device LLM diagnosis for performance regressions.
 *
 * Key design decisions:
 * - **Sequential processing**: Uses a [Channel] with UNLIMITED capacity and a single consumer
 *   coroutine to ensure regressions are processed one at a time (LlmInference is not thread-safe)
 * - **30-second timeout**: Prevents indefinite blocking if inference stalls
 * - **Distinct error handling**: Maps each failure mode to a specific [DiagnosisResult.Failure] message
 * - **State exposure**: [isReady] StateFlow lets the overlay show "AI Ready" vs "AI Loading..."
 *
 * @param context Application context for MediaPipe initialization
 * @param config DroidLens configuration containing model path and LLM settings
 */
class LLMInferenceEngine(
    private val context: Context,
    private val config: DroidLensConfig
) {
    companion object {
        private const val TAG = "DroidLens-LLM"
        private const val INFERENCE_TIMEOUT_MS = 30_000L
        private const val MAX_TOKENS = 512
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.1f
        private const val RANDOM_SEED = 42
    }

    private var llmInference: LlmInference? = null

    private val _isReady = MutableStateFlow(false)

    /**
     * Whether the LLM engine is initialized and ready to process regressions.
     * The overlay can observe this to show "AI Ready" or "AI Loading..." status.
     */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Internal request for the processing queue.
     */
    private data class DiagnosisRequest(
        val regression: Regression,
        val callback: (DiagnosisResult) -> Unit
    )

    // Channel-based queue: unlimited capacity, single consumer
    private val requestChannel = Channel<DiagnosisRequest>(Channel.UNLIMITED)

    init {
        // Start the single consumer coroutine
        scope.launch {
            for (request in requestChannel) {
                val result = executeInference(request.regression)
                request.callback(result)
            }
        }
    }

    /**
     * Initializes the MediaPipe LlmInference engine.
     * Should be called on a background thread — this is a heavy operation.
     */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                if (config.cloudApiKey.isNotBlank()) {
                    Log.d(TAG, "Cloud API Key provided. Bypassing local MediaPipe initialization.")
                    _isReady.value = true
                    return@withContext
                }

                val modelPath = resolveModelPath()
                if (modelPath == null) {
                    Log.w(TAG, "Model file not found. LLM inference will use fallback mode.")
                    _isReady.value = false
                    return@withContext
                }

                Log.d(TAG, "Initializing LlmInference with model: $modelPath")

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .setTopK(TOP_K)
                    .setTemperature(TEMPERATURE)
                    .setRandomSeed(RANDOM_SEED)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                _isReady.value = true
                Log.d(TAG, "LlmInference initialized successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize LlmInference: ${e.message}", e)
                _isReady.value = false
            }
        }
    }

    /**
     * Submits a regression for LLM diagnosis.
     *
     * If the engine is not ready, returns [DiagnosisResult.Failure] immediately.
     * Otherwise, queues the regression for sequential processing.
     *
     * This method is safe to call from any coroutine — back-pressure is handled
     * by the unlimited channel buffer.
     */
    suspend fun diagnose(regression: Regression): DiagnosisResult {
        if (llmInference == null && config.cloudApiKey.isBlank()) {
            return DiagnosisResult.Failure(
                regressionType = regression.type,
                error = "LLM engine not initialized. Model may be missing or device incompatible."
            )
        }

        return withContext(Dispatchers.IO) {
            // Use a CompletableDeferred-like pattern via Channel
            val resultChannel = Channel<DiagnosisResult>(1)

            requestChannel.send(DiagnosisRequest(regression) { result ->
                resultChannel.trySend(result)
            })

            resultChannel.receive()
        }
    }

    /**
     * Executes inference for a single regression. Called sequentially by the consumer coroutine.
     */
    private suspend fun executeInference(regression: Regression): DiagnosisResult {
        if (config.cloudApiKey.isNotBlank()) {
            return runCloudInference(regression)
        }

        val inference = llmInference ?: return DiagnosisResult.Failure(
            regressionType = regression.type,
            error = "LLM engine not initialized."
        )

        return try {
            withTimeout(INFERENCE_TIMEOUT_MS.milliseconds) {
                withContext(Dispatchers.IO) {
                    val prompt = PromptBuilder.buildPrompt(regression)
                    Log.d(TAG, "Running local inference for ${regression.type::class.simpleName}...")

                    val rawOutput = inference.generateResponse(prompt)
                    Log.d(TAG, "Inference complete. Raw output length: ${rawOutput.length}")

                    if (rawOutput.isBlank()) {
                        return@withContext DiagnosisResult.Failure(
                            regressionType = regression.type,
                            error = "LLM returned empty response."
                        )
                    }

                    val (explanation, suggestion) = PromptBuilder.parseResponse(rawOutput)
                    val confidence = PromptBuilder.parseConfidence(rawOutput)

                    DiagnosisResult.Success(
                        regressionType = regression.type,
                        explanation = explanation,
                        suggestion = suggestion,
                        confidence = confidence
                    )
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Inference timed out after ${INFERENCE_TIMEOUT_MS}ms", e)
            DiagnosisResult.Failure(
                regressionType = regression.type,
                error = "LLM inference timed out after ${INFERENCE_TIMEOUT_MS / 1000}s. " +
                        "The model may be too large for this device."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            DiagnosisResult.Failure(
                regressionType = regression.type,
                error = "LLM inference failed: ${e.message}"
            )
        }
    }

    private suspend fun runCloudInference(regression: Regression): DiagnosisResult {
        return try {
            withTimeout(INFERENCE_TIMEOUT_MS.milliseconds) {
                withContext(Dispatchers.IO) {
                    val prompt = PromptBuilder.buildPrompt(regression)
                    Log.d(TAG, "Running Cloud inference for ${regression.type::class.simpleName}...")

                    val isGemini = config.cloudApiEndpoint.contains("googleapis")
                    
                    val urlStr = if (isGemini) {
                        "${config.cloudApiEndpoint}?key=${config.cloudApiKey}"
                    } else {
                        config.cloudApiEndpoint
                    }
                    
                    val url = URL(urlStr)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("User-Agent", "DroidLens/1.0")
                    connection.setRequestProperty("HTTP-Referer", "https://github.com/droidlens")
                    connection.setRequestProperty("X-Title", "Droid Lens Profiler")
                    
                    if (!isGemini) {
                        connection.setRequestProperty("Authorization", "Bearer ${config.cloudApiKey.trim()}")
                        // Disable auto-redirects to prevent Android from silently dropping the Authorization header
                        connection.instanceFollowRedirects = false
                    }
                    
                    connection.doOutput = true

                    val jsonPayload = if (isGemini) {
                        JSONObject().apply {
                            put("contents", org.json.JSONArray().put(JSONObject().apply {
                                put("parts", org.json.JSONArray().put(JSONObject().apply {
                                    put("text", prompt)
                                }))
                            }))
                        }
                    } else {
                        JSONObject().apply {
                            put("model", config.cloudApiModel)
                            put("messages", org.json.JSONArray().put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            }))
                        }
                    }

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonPayload.toString())
                    }

                    if (connection.responseCode in 200..299) {
                        val responseBody = InputStreamReader(connection.inputStream).readText()
                        val root = JSONObject(responseBody)
                        
                        val text = if (isGemini) {
                            val candidates = root.optJSONArray("candidates")
                            candidates?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text") ?: ""
                        } else {
                            val choices = root.optJSONArray("choices")
                            choices?.optJSONObject(0)
                                ?.optJSONObject("message")
                                ?.optString("content") ?: ""
                        }

                        if (text.isBlank()) {
                            return@withContext DiagnosisResult.Failure(regression.type, "Cloud LLM returned empty response.")
                        }

                        val (explanation, suggestion) = PromptBuilder.parseResponse(text)
                        val confidence = PromptBuilder.parseConfidence(text)

                        DiagnosisResult.Success(regression.type, explanation, suggestion, confidence)
                    } else {
                        val errorBody = InputStreamReader(connection.errorStream).readText()
                        Log.e(TAG, "Cloud API Error: ${connection.responseCode} - $errorBody")
                        DiagnosisResult.Failure(regression.type, "Cloud API Error: ${connection.responseCode} - $errorBody")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud Inference failed: ${e.message}", e)
            DiagnosisResult.Failure(regression.type, "Cloud inference failed: ${e.message}")
        }
    }

    /**
     * Resolves the model path from config, checking both the configured path and
     * the default download location in filesDir.
     */
    private fun resolveModelPath(): String? {
        // Priority 1: Explicitly configured path (e.g., /data/local/tmp/...)
        if (config.llmModelPath.isNotBlank()) {
            val configFile = File(config.llmModelPath)
            if (configFile.exists() && configFile.length() > 0) {
                Log.d(TAG, "Using configured model path: ${config.llmModelPath}")
                return config.llmModelPath
            }
            Log.w(TAG, "Configured model path not found: ${config.llmModelPath}")
        }

        // Priority 2: Downloaded model in filesDir
        val downloadedModel = File(context.filesDir, "models/gemma-2b-it-cpu-int4.bin")
        if (downloadedModel.exists() && downloadedModel.length() > 0) {
            Log.d(TAG, "Using downloaded model: ${downloadedModel.absolutePath}")
            return downloadedModel.absolutePath
        }

        Log.w(TAG, "No model file found at configured path or default download location")
        return null
    }

    /**
     * Releases the LlmInference resources.
     * Must be called when the engine is no longer needed to prevent memory leaks.
     */
    fun close() {
        try {
            requestChannel.close()
            llmInference?.close()
            llmInference = null
            _isReady.value = false
            Log.d(TAG, "LLM engine closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LLM engine: ${e.message}", e)
        }
    }
}
