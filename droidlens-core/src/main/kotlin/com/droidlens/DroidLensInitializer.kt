package com.droidlens

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.droidlens.instrumentation.StartupTracer

/**
 * Stub class returned by [DroidLensInitializer].
 * Jetpack Startup requires a non-void return type from [Initializer.create].
 */
class DroidLensStub internal constructor()

/**
 * Jetpack Startup [Initializer] that runs before Application.onCreate().
 *
 * This is the earliest code-execution point available in the Android lifecycle
 * (via ContentProvider). It records the process start timestamp so that
 * [StartupTracer] can measure total startup time from the earliest possible moment.
 *
 * Declared in AndroidManifest.xml via:
 * ```xml
 * <provider android:name="androidx.startup.InitializationProvider" ...>
 *     <meta-data android:name="com.droidlens.DroidLensInitializer"
 *                android:value="androidx.startup" />
 * </provider>
 * ```
 */
class DroidLensInitializer : Initializer<DroidLensStub> {

    companion object {
        private const val TAG = "DroidLens-Init"
    }

    override fun create(context: Context): DroidLensStub {
        // Record the earliest possible timestamp
        StartupTracer.markProcessStart()
        Log.d(TAG, "DroidLensInitializer: process start timestamp recorded")
        return DroidLensStub()
    }

    /**
     * No dependencies on other Initializers.
     */
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}
