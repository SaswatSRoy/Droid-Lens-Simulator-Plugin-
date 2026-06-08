package com.droidlens.app

import android.app.Application
import com.droidlens.DroidLens
import com.droidlens.config.DroidLensConfig
import com.droidlens.config.OverlayPosition

class DroidLensDemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Droid Lens with custom configuration
        val config = DroidLensConfig.build {
            frameJankThreshold(16L)
            recompositionSpikeMultiplier(3.0f)
            overlayPosition(OverlayPosition.TOP_RIGHT)
            llmEnabled(true)
            // Values are securely read from local.properties via BuildConfig
            cloudApiKey(BuildConfig.OPENROUTER_API_KEY)
            cloudApiEndpoint(BuildConfig.OPENROUTER_API_ENDPOINT)
            cloudApiModel(BuildConfig.OPENROUTER_MODEL)

            
            // Enable exporting JSON/Markdown reports
            exportEnabled(true)
        }

        DroidLens.init(this, config)
        
        // Optionally start a session immediately
        DroidLens.startSession("demo-startup")
    }
}

