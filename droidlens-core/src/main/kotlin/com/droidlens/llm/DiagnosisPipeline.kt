package com.droidlens.llm

import android.content.Context
import android.util.Log
import com.droidlens.analysis.RegressionDetector
import com.droidlens.config.DroidLensConfig
import com.droidlens.db.DetectedIssueDao
import com.droidlens.model.DiagnosisResult
import com.droidlens.model.Regression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Wires the end-to-end diagnosis pipeline:
 *
 * ```
 * RegressionDetector.regressions (SharedFlow)
 *   → Emit DiagnosisResult.Pending immediately
 *   → LLMInferenceEngine.diagnose() (or FallbackDiagnosisProvider)
 *   → DetectedIssueDao.updateLlmDiagnosis()
 *   → _latestDiagnosis StateFlow → OverlayViewModel
 * ```
 *
 * Design constraints:
 * - Runs entirely on background threads
 * - Never blocks the main thread
 * - Back-pressure handled by LLMInferenceEngine's internal channel queue
 * - Immediately emits [DiagnosisResult.Pending] so the overlay shows "Analyzing..."
 */
class DiagnosisPipeline(
    private val context: Context,
    private val config: DroidLensConfig,
    private val regressionDetector: RegressionDetector,
    private val issueDao: DetectedIssueDao,
    private val llmEngine: LLMInferenceEngine
) {
    companion object {
        private const val TAG = "DroidLens-Pipeline"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _latestDiagnosis = MutableStateFlow<DiagnosisResult?>(null)

    /**
     * The latest diagnosis result. Observed by the overlay to display diagnosis information.
     *
     * Emits:
     * - [DiagnosisResult.Pending] immediately when a regression is received
     * - [DiagnosisResult.Success] when LLM or fallback diagnosis completes
     * - [DiagnosisResult.Failure] if diagnosis fails
     */
    val latestDiagnosis: StateFlow<DiagnosisResult?> = _latestDiagnosis.asStateFlow()

    private val _allDiagnoses = MutableStateFlow<List<DiagnosisResult>>(emptyList())

    /**
     * All diagnosis results from the current session, ordered by most recent first.
     */
    val allDiagnoses: StateFlow<List<DiagnosisResult>> = _allDiagnoses.asStateFlow()

    /**
     * Whether the pipeline is using LLM or fallback mode.
     */
    val isLlmMode: Boolean
        get() = config.llmEnabled && llmEngine.isReady.value

    /**
     * Starts the pipeline — begins collecting regressions and processing them.
     *
     * This method returns immediately. All processing happens in background coroutines.
     */
    fun start() {
        Log.d(TAG, "Starting diagnosis pipeline (llmEnabled=${config.llmEnabled})")

        // Initialize LLM engine if enabled
        if (config.llmEnabled) {
            scope.launch(Dispatchers.IO) {
                try {
                    llmEngine.initialize()
                    Log.d(TAG, "LLM engine initialized: ready=${llmEngine.isReady.value}")
                } catch (e: Exception) {
                    Log.e(TAG, "LLM engine initialization failed, falling back to rule-based", e)
                }
            }
        }

        // Collect regressions and process them
        scope.launch {
            regressionDetector.regressions.collect { regression ->
                processRegression(regression)
            }
        }
    }

    /**
     * Processes a single regression through the pipeline.
     */
    private fun processRegression(regression: Regression) {
        // Step 1: Emit Pending immediately so the overlay shows "Analyzing..."
        val pending = DiagnosisResult.Pending(regression.type)
        _latestDiagnosis.value = pending
        Log.d(TAG, "Processing regression: ${regression.type::class.simpleName}")

        // Step 2: Run diagnosis in background (LLM or fallback)
        scope.launch(Dispatchers.IO) {
            val result = try {
                if (config.llmEnabled && llmEngine.isReady.value) {
                    // Use LLM inference
                    Log.d(TAG, "Using LLM for diagnosis")
                    llmEngine.diagnose(regression)
                } else {
                    // Use rule-based fallback
                    Log.d(TAG, "Using fallback for diagnosis (llmEnabled=${config.llmEnabled}, ready=${llmEngine.isReady.value})")
                    FallbackDiagnosisProvider.diagnose(regression)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Diagnosis failed: ${e.message}", e)
                DiagnosisResult.Failure(
                    regressionType = regression.type,
                    error = "Diagnosis pipeline error: ${e.message}"
                )
            }

            // Step 3: Update Room database
            try {
                if (result is DiagnosisResult.Success) {
                    // Find the most recent undiagnosed issue matching this regression type
                    val undiagnosed = issueDao.getUndiagnosedIssues()
                    val matchingIssue = undiagnosed
                        .filter { it.regressionType == regression.type::class.java.simpleName }
                        .maxByOrNull { it.detectedAtMs }

                    if (matchingIssue != null) {
                        issueDao.updateLlmDiagnosis(
                            id = matchingIssue.id,
                            explanation = result.explanation,
                            suggestion = result.suggestion
                        )
                        Log.d(TAG, "Updated DB issue ${matchingIssue.id} with diagnosis")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update DB with diagnosis: ${e.message}", e)
            }

            // Step 4: Emit result to StateFlows
            _latestDiagnosis.value = result
            _allDiagnoses.value = listOf(result) + _allDiagnoses.value

            Log.d(TAG, "Diagnosis complete: ${result::class.simpleName}")
        }
    }

    /**
     * Stops the pipeline and releases resources.
     */
    fun stop() {
        llmEngine.close()
        Log.d(TAG, "Diagnosis pipeline stopped")
    }
}
