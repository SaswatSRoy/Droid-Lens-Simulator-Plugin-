package com.droidlens.config

/**
 * Configuration for the Droid Lens library.
 * Use the [DroidLensConfig.Builder] or the [DroidLensConfig.build] DSL to create an instance.
 */
data class DroidLensConfig(
    val frameJankThresholdMs: Long = 16L,
    val recompositionSpikeMultiplier: Float = 3.0f,
    val memoryGrowthThresholdMb: Float = 50.0f,
    val startupRegressionThresholdMs: Long = 200L,
    val overlayEnabled: Boolean = true,
    val overlayPosition: OverlayPosition = OverlayPosition.TOP_RIGHT,
    val llmEnabled: Boolean = true,
    val llmModelPath: String = "",
    val llmDownloadUrl: String = "",
    val llmModelSha256: String = "",
    val maxIssuesInOverlay: Int = 5,
    val sessionName: String = "default",
    val exportEnabled: Boolean = false,
    val exportPath: String = "",
    val cloudApiKey: String = "",
    val cloudApiEndpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
    val cloudApiModel: String = "gemini-1.5-flash"
) {
    companion object {
        /**
         * Create a [DroidLensConfig] using a DSL.
         */
        fun build(block: Builder.() -> Unit): DroidLensConfig {
            return Builder().apply(block).build()
        }

        /**
         * Returns a [DroidLensConfig] with default values.
         */
        fun default(): DroidLensConfig = DroidLensConfig()
    }

    class Builder {
        private var frameJankThresholdMs: Long = 16L
        private var recompositionSpikeMultiplier: Float = 3.0f
        private var memoryGrowthThresholdMb: Float = 50.0f
        private var startupRegressionThresholdMs: Long = 200L
        private var overlayEnabled: Boolean = true
        private var overlayPosition: OverlayPosition = OverlayPosition.TOP_RIGHT
        private var llmEnabled: Boolean = true
        private var llmModelPath: String = ""
        private var llmDownloadUrl: String = ""
        private var llmModelSha256: String = ""
        private var maxIssuesInOverlay: Int = 5
        private var sessionName: String = "default"
        private var exportEnabled: Boolean = false
        private var exportPath: String = ""
        private var cloudApiKey: String = ""
        private var cloudApiEndpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        private var cloudApiModel: String = "gemini-1.5-flash"

        fun frameJankThreshold(ms: Long) = apply { this.frameJankThresholdMs = ms }
        fun recompositionSpikeMultiplier(multiplier: Float) = apply { this.recompositionSpikeMultiplier = multiplier }
        fun memoryGrowthThreshold(mb: Float) = apply { this.memoryGrowthThresholdMb = mb }
        fun startupRegressionThreshold(ms: Long) = apply { this.startupRegressionThresholdMs = ms }
        fun overlayEnabled(enabled: Boolean) = apply { this.overlayEnabled = enabled }
        fun overlayPosition(position: OverlayPosition) = apply { this.overlayPosition = position }
        fun llmEnabled(enabled: Boolean) = apply { this.llmEnabled = enabled }
        fun llmModelPath(path: String) = apply { this.llmModelPath = path }
        fun llmDownloadUrl(url: String) = apply { this.llmDownloadUrl = url }
        fun llmModelSha256(sha256: String) = apply { this.llmModelSha256 = sha256 }
        fun maxIssuesInOverlay(max: Int) = apply { this.maxIssuesInOverlay = max }
        fun sessionName(name: String) = apply { this.sessionName = name }
        fun exportEnabled(enabled: Boolean) = apply { this.exportEnabled = enabled }
        fun exportPath(path: String) = apply { this.exportPath = path }
        fun cloudApiKey(key: String) = apply { this.cloudApiKey = key }
        fun cloudApiEndpoint(endpoint: String) = apply { this.cloudApiEndpoint = endpoint }
        fun cloudApiModel(model: String) = apply { this.cloudApiModel = model }

        fun build(): DroidLensConfig = DroidLensConfig(
            frameJankThresholdMs = frameJankThresholdMs,
            recompositionSpikeMultiplier = recompositionSpikeMultiplier,
            memoryGrowthThresholdMb = memoryGrowthThresholdMb,
            startupRegressionThresholdMs = startupRegressionThresholdMs,
            overlayEnabled = overlayEnabled,
            overlayPosition = overlayPosition,
            llmEnabled = llmEnabled,
            llmModelPath = llmModelPath,
            llmDownloadUrl = llmDownloadUrl,
            llmModelSha256 = llmModelSha256,
            maxIssuesInOverlay = maxIssuesInOverlay,
            sessionName = sessionName,
            exportEnabled = exportEnabled,
            exportPath = exportPath,
            cloudApiKey = cloudApiKey,
            cloudApiEndpoint = cloudApiEndpoint,
            cloudApiModel = cloudApiModel
        )
    }
}
