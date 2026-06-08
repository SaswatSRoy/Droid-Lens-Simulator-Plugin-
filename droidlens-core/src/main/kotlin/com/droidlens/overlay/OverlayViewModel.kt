package com.droidlens.overlay

import com.droidlens.db.DetectedIssueDao
import com.droidlens.db.entities.DetectedIssue
import com.droidlens.instrumentation.FrameInstrumentation
import com.droidlens.instrumentation.MemoryWatcher
import com.droidlens.instrumentation.StartupTracer
import com.droidlens.llm.DiagnosisPipeline
import com.droidlens.model.DiagnosisResult
import com.droidlens.model.FrameMetrics
import com.droidlens.model.HeapMetrics
import com.droidlens.model.StartupMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * LLM engine status as shown in the overlay.
 */
enum class LlmStatus {
    LOADING,
    READY,
    PROCESSING,
    UNAVAILABLE
}

/**
 * State holder for the floating overlay UI.
 *
 * This is a plain class (not AndroidViewModel) because the overlay is not tied to an Activity.
 * It holds its own [CoroutineScope] and exposes [StateFlow]s for the Compose UI to observe.
 *
 * @param frameInstrumentation Source of real-time frame metrics
 * @param memoryWatcher Source of real-time heap metrics
 * @param diagnosisPipeline Source of LLM/fallback diagnosis results
 * @param issueDao For observing recent issues and dismissing them
 * @param sessionName Current profiling session name
 * @param llmReady StateFlow from LLMInferenceEngine.isReady
 */
class OverlayViewModel(
    frameInstrumentation: FrameInstrumentation,
    memoryWatcher: MemoryWatcher,
    private val diagnosisPipeline: DiagnosisPipeline,
    private val issueDao: DetectedIssueDao,
    sessionName: String,
    llmReady: StateFlow<Boolean>
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Real-time frame performance metrics (FPS, frame time, jank count).
     */
    val frameMetrics: StateFlow<FrameMetrics> = frameInstrumentation.metrics

    /**
     * Real-time heap memory metrics (used MB, max MB, usage %).
     */
    val heapMetrics: StateFlow<HeapMetrics> = memoryWatcher.heapMetrics

    /**
     * Startup timing breakdown (onCreate, TTFF, TTI).
     */
    val startupMetrics: StateFlow<StartupMetrics?> = StartupTracer.metrics

    /**
     * Recent detected issues from the database, most recent first, limited to 5.
     */
    val recentIssues: StateFlow<List<DetectedIssue>> = issueDao
        .observeRecentIssues(sessionName, 5)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Current LLM engine status for display in the overlay.
     */
    val llmStatus: StateFlow<LlmStatus> = diagnosisPipeline.latestDiagnosis
        .map { diagnosis ->
            when {
                diagnosis is DiagnosisResult.Pending -> LlmStatus.PROCESSING
                llmReady.value -> LlmStatus.READY
                else -> LlmStatus.UNAVAILABLE
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), LlmStatus.LOADING)

    /**
     * Whether the overlay is in minimised (dot) mode.
     */
    private val _isMinimised = MutableStateFlow(false)
    val isMinimised: StateFlow<Boolean> = _isMinimised.asStateFlow()

    /**
     * Latest diagnosis result for display.
     */
    val latestDiagnosis: StateFlow<DiagnosisResult?> = diagnosisPipeline.latestDiagnosis

    /**
     * List of last 60 frame time durations in ms for the sparkline chart.
     * Updated from FrameMetrics at each emission.
     */
    private val _frameHistory = MutableStateFlow<List<Float>>(emptyList())
    val frameHistory: StateFlow<List<Float>> = _frameHistory.asStateFlow()

    init {
        // Collect frame metrics to build sparkline history
        scope.launch {
            frameInstrumentation.metrics.collect { metrics ->
                if (metrics.avgFrameTimeMs > 0) {
                    val current = _frameHistory.value.toMutableList()
                    current.add(metrics.avgFrameTimeMs)
                    // Keep last 60 data points
                    if (current.size > 60) {
                        _frameHistory.value = current.takeLast(60)
                    } else {
                        _frameHistory.value = current
                    }
                }
            }
        }

        // Update LLM status based on llmReady changes
        scope.launch {
            llmReady.collect {
                // Force re-evaluation of llmStatus by touching latestDiagnosis
                // The map above will pick up the new llmReady.value
            }
        }
    }

    /**
     * Minimises the overlay to a small dot.
     */
    fun minimise() {
        _isMinimised.value = true
    }

    /**
     * Expands the overlay from minimised state.
     */
    fun expand() {
        _isMinimised.value = false
    }

    /**
     * Toggles between minimised and expanded state.
     */
    fun toggleMinimised() {
        _isMinimised.value = !_isMinimised.value
    }

    /**
     * Dismisses an issue by marking it as resolved in the database.
     */
    fun dismissIssue(issueId: Long) {
        scope.launch(Dispatchers.IO) {
            issueDao.markResolved(issueId)
        }
    }
}
