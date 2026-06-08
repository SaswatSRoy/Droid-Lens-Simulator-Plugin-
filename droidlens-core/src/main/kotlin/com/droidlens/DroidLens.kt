package com.droidlens

import android.app.Application
import androidx.lifecycle.ViewModel
import com.droidlens.analysis.BaselineManager
import com.droidlens.analysis.JankDetectionWorker
import com.droidlens.analysis.RegressionDetector
import com.droidlens.config.DroidLensConfig
import com.droidlens.db.DroidLensDatabase
import com.droidlens.instrumentation.ComposeRecompositionTracker
import com.droidlens.instrumentation.FrameInstrumentation
import com.droidlens.instrumentation.MemoryWatcher
import com.droidlens.instrumentation.ObjectWatcher
import com.droidlens.instrumentation.StartupTracer
import com.droidlens.llm.DiagnosisPipeline
import com.droidlens.llm.LLMInferenceEngine
import com.droidlens.model.DiagnosisResult
import com.droidlens.model.FrameMetrics
import com.droidlens.model.HeapMetrics
import com.droidlens.model.SessionReport
import com.droidlens.model.StartupMetrics
import com.droidlens.overlay.DroidLensOverlay
import com.droidlens.overlay.OverlayPermissionHelper
import com.droidlens.overlay.OverlayViewModel
import kotlinx.coroutines.flow.StateFlow
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.droidlens.model.RegressionType
import java.io.File

/**
 * Main entry point for the Droid Lens library.
 */
object DroidLens {  // Creates a Singleton ( single object) preventing clashing of sessions and multiple objects creation
    private var isInitialized = false
    private var currentConfig = DroidLensConfig.default()
    private var isSessionRunning = false
    private var sessionStartMs: Long = 0L
    private var lastExportedJson: File? = null
    private var lastExportedMd: File? = null
    private lateinit var appContext: Application

    private lateinit var frameInstrumentation: FrameInstrumentation
    private lateinit var regressionDetector: RegressionDetector
    private lateinit var jankWorker: JankDetectionWorker
    private lateinit var objectWatcher: ObjectWatcher
    private lateinit var memoryWatcher: MemoryWatcher
    private lateinit var llmEngine: LLMInferenceEngine
    private lateinit var diagnosisPipeline: DiagnosisPipeline
    private var overlayViewModel: OverlayViewModel? = null
    private var overlay: DroidLensOverlay? = null
    private lateinit var baselineManager: BaselineManager

    /**
     * Initializes Droid Lens with the provided configuration.
     */
    fun init(application: Application, config: DroidLensConfig = DroidLensConfig.default()) {
        if (isInitialized) return
        this.currentConfig = config
        this.appContext = application

        // Mark Application.onCreate() start for startup tracing
        StartupTracer.markOnCreateStart()
        
        // Instrumentation
        frameInstrumentation = FrameInstrumentation(config.frameJankThresholdMs)
        objectWatcher = ObjectWatcher()
        memoryWatcher = MemoryWatcher(
            objectWatcher = objectWatcher,
            memoryGrowthThresholdMb = config.memoryGrowthThresholdMb,
            sessionName = config.sessionName
        )

        // Analysis & Persistence
        baselineManager = BaselineManager(
            context = application,
            frameInstrumentation = frameInstrumentation,
            memoryWatcher = memoryWatcher,
            recompositionTracker = ComposeRecompositionTracker
        )

        regressionDetector = RegressionDetector(
            context = application,
            frameInstrumentation = frameInstrumentation,
            recompositionTracker = ComposeRecompositionTracker,
            memoryWatcher = memoryWatcher,
            baselineManager = baselineManager,
            frameJankThresholdMs = config.frameJankThresholdMs,
            memoryGrowthThresholdMb = config.memoryGrowthThresholdMb,
            startupRegressionThresholdMs = config.startupRegressionThresholdMs
        )

        jankWorker = JankDetectionWorker(
            jankEvents = frameInstrumentation.jankEvents,
            regressionDetector = regressionDetector,
            sessionName = config.sessionName,
            jankThresholdMs = config.frameJankThresholdMs
        )

        // LLM Inference Pipeline
        val db = DroidLensDatabase.getInstance(application)
        llmEngine = LLMInferenceEngine(application, config)
        diagnosisPipeline = DiagnosisPipeline(
            context = application,
            config = config,
            regressionDetector = regressionDetector,
            issueDao = db.detectedIssueDao(),
            llmEngine = llmEngine
        )

        // Overlay
        overlayViewModel = OverlayViewModel(
            frameInstrumentation = frameInstrumentation,
            memoryWatcher = memoryWatcher,
            diagnosisPipeline = diagnosisPipeline,
            issueDao = db.detectedIssueDao(),
            sessionName = config.sessionName,
            llmReady = llmEngine.isReady
        )

        this.isInitialized = true
        
        jankWorker.start()
        memoryWatcher.start(application)
        regressionDetector.start()
        diagnosisPipeline.start()
        
        // Load latest baseline for this session
        baselineManager.loadLatestBaseline(config.sessionName)

        // Auto-show overlay if enabled
        if (config.overlayEnabled) {
            showOverlay()
        }

        // Mark Application.onCreate() end for startup tracing
        StartupTracer.markOnCreateEnd(application)
    }

    /**
     * Starts a new profiling session.
     */
    fun startSession(sessionName: String = "default") {
        if (!isInitialized) return
        regressionDetector.clearSession()
        isSessionRunning = true
        sessionStartMs = System.currentTimeMillis()
        frameInstrumentation.start()
    }

    /**
     * Stops the current profiling session and returns a report.
     */
    fun stopSession(): SessionReport {
        val duration = if (sessionStartMs > 0) System.currentTimeMillis() - sessionStartMs else 0L
        isSessionRunning = false
        frameInstrumentation.stop()

        // Clean up overlay on session stop
        overlay?.destroy()
        overlay = null
        
        val currentRegressions = regressionDetector.currentSessionRegressions

        var report = SessionReport(
            sessionName = currentConfig.sessionName,
            durationMs = duration,
            totalJankFrames = frameInstrumentation.metrics.value.jankFrameCount,
            avgFrameTimeMs = frameInstrumentation.metrics.value.avgFrameTimeMs,
            recompositionSpikes = currentRegressions.map { it.type }.filterIsInstance<RegressionType.RecompositionSpike>(),
            memoryLeaks = currentRegressions.map { it.type }.filterIsInstance<RegressionType.MemoryLeak>(),
            detectedRegressions = currentRegressions,
            llmDiagnoses = diagnosisPipeline.allDiagnoses.value,
            exportedJsonPath = null
        )

        if (currentConfig.exportEnabled) {
            val (jsonFile, mdFile) = SessionExporter.exportSession(appContext, report, currentConfig)
            lastExportedJson = jsonFile
            lastExportedMd = mdFile
            report = report.copy(exportedJsonPath = jsonFile.absolutePath)
        }
        
        return report
    }

    /**
     * Triggers a share intent for the generated session reports (JSON and Markdown).
     */
    fun shareReport(context: Context) {
        val json = lastExportedJson
        val md = lastExportedMd

        if (json == null || md == null || !json.exists() || !md.exists()) {
            return
        }

        val authority = "${context.packageName}.droidlens.fileprovider"
        val jsonUri = FileProvider.getUriForFile(context, authority, json)
        val mdUri = FileProvider.getUriForFile(context, authority, md)

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(jsonUri, mdUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_report_title)))
    }

    /**
     * Watches a ViewModel for potential leaks.
     */
    fun watchViewModel(viewModel: ViewModel) {
        if (!isInitialized) return
        viewModel.addCloseable {
            objectWatcher.watch(viewModel, "ViewModel: ${viewModel.javaClass.simpleName}")
        }
    }

    /**
     * Returns the flow of real-time frame metrics.
     */
    fun getFrameMetrics(): StateFlow<FrameMetrics> {
        if (!isInitialized) throw IllegalStateException("DroidLens not initialized")
        return frameInstrumentation.metrics
    }

    /**
     * Returns the flow of real-time heap metrics.
     */
    fun getHeapMetrics(): StateFlow<HeapMetrics> {
        if (!isInitialized) throw IllegalStateException("DroidLens not initialized")
        return memoryWatcher.heapMetrics
    }

    /**
     * Captures a performance baseline for the current session name.
     */
    fun captureBaseline() {
        if (!isInitialized) return
        baselineManager.captureBaseline(currentConfig.sessionName)
    }

    /**
     * Clears the baseline for the current session name.
     */
    fun clearBaseline() {
        if (!isInitialized) return
        baselineManager.clearBaseline(currentConfig.sessionName)
    }

    /**
     * Shows the floating overlay UI.
     * Checks SYSTEM_ALERT_WINDOW permission first.
     * If not granted, shows a notification prompting the user to grant it.
     */
    fun showOverlay() {
        if (!isInitialized) return

        val vm = overlayViewModel ?: return

        if (!OverlayPermissionHelper.canDrawOverlays(appContext)) {
            OverlayPermissionHelper.showPermissionNotification(appContext)
            return
        }

        if (overlay == null) {
            overlay = DroidLensOverlay(
                context = appContext,
                overlayPosition = currentConfig.overlayPosition,
                viewModel = vm,
                jankThresholdMs = currentConfig.frameJankThresholdMs
            )
        }

        overlay?.show()
    }

    /**
     * Hides the floating overlay UI.
     */
    fun hideOverlay() {
        if (!isInitialized) return
        overlay?.hide()
    }

    /**
     * Returns the flow of LLM diagnosis results.
     * Emits Pending → Success/Failure for each detected regression.
     */
    fun getDiagnosisFlow(): StateFlow<DiagnosisResult?> {
        if (!isInitialized) throw IllegalStateException("DroidLens not initialized")
        return diagnosisPipeline.latestDiagnosis
    }

    /**
     * Returns whether the LLM engine is ready for inference.
     * False when: LLM disabled, model not found, initialization failed.
     */
    fun getLlmStatus(): StateFlow<Boolean> {
        if (!isInitialized) throw IllegalStateException("DroidLens not initialized")
        return llmEngine.isReady
    }

    /**
     * Returns all diagnosis results from the current session.
     */
    fun getAllDiagnoses(): StateFlow<List<DiagnosisResult>> {
        if (!isInitialized) throw IllegalStateException("DroidLens not initialized")
        return diagnosisPipeline.allDiagnoses
    }

    /**
     * Cleans up LLM resources. Call when the app is being destroyed.
     */
    fun shutdown() {
        if (!isInitialized) return
        overlay?.destroy()
        overlay = null
        diagnosisPipeline.stop()
    }

    /**
     * Returns the flow of startup metrics.
     * Emits null until at least Application.onCreate() has been measured.
     */
    fun getStartupMetrics(): StateFlow<StartupMetrics?> {
        return StartupTracer.metrics
    }

    /**
     * Returns true if a profiling session is currently active.
     */
    fun isRunning(): Boolean = isSessionRunning

    /**
     * Returns the current session report without stopping the session.
     */
    fun getSessionReport(): SessionReport {
        // Current report generation logic will go here
        return SessionReport(
            sessionName = currentConfig.sessionName,
            durationMs = 0L,
            totalJankFrames = 0,
            avgFrameTimeMs = 0f,
            recompositionSpikes = emptyList(),
            memoryLeaks = emptyList(),
            detectedRegressions = emptyList(),
            llmDiagnoses = emptyList(),
            exportedJsonPath = null
        )
    }
}
