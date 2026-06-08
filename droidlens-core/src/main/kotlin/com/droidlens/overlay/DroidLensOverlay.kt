package com.droidlens.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.droidlens.config.OverlayPosition
import com.droidlens.overlay.ui.DroidLensOverlayContent

/**
 * Manages the floating overlay window drawn via [WindowManager].
 *
 * This overlay is drawn in a **separate ComposeView** — it is NOT part of the host app's
 * Compose hierarchy. It uses its own [LifecycleOwner] and [SavedStateRegistryOwner]
 * to satisfy Compose's requirements.
 *
 * Key design:
 * - Uses [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] for drawing over other apps
 * - Supports drag-to-reposition via [MotionEvent] handling
 * - Cleanup via [destroy] removes the view and nulls all references
 */
class DroidLensOverlay(
    private val context: Context,
    private val overlayPosition: OverlayPosition,
    private val viewModel: OverlayViewModel,
    private val jankThresholdMs: Long = 16L
) {
    companion object {
        private const val TAG = "DroidLens-Overlay"
    }

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    // Custom LifecycleOwner for the ComposeView (required since we're outside an Activity)
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    /**
     * Shows the overlay. Returns false if overlay permission is not granted.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show(): Boolean {
        if (isShowing) {
            Log.d(TAG, "Overlay already showing")
            return true
        }

        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted")
            OverlayPermissionHelper.showPermissionNotification(context)
            return false
        }

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // Create layout params
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = mapPositionToGravity(overlayPosition)
                x = 16
                y = 100
            }

            // Create lifecycle owner
            lifecycleOwner = OverlayLifecycleOwner().apply {
                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            }

            // Create ComposeView
            composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)

                setContent {
                    DroidLensOverlayContent(
                        viewModel = viewModel,
                        jankThresholdMs = jankThresholdMs,
                        onClose = { hide() }
                    )
                }
            }

            // Set up drag-to-reposition
            setupDragListener()

            // Add to WindowManager
            windowManager?.addView(composeView, layoutParams)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            isShowing = true

            // Dismiss permission notification if it was showing
            OverlayPermissionHelper.dismissPermissionNotification(context)

            Log.d(TAG, "Overlay shown at position: $overlayPosition")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay: ${e.message}", e)
            destroy()
            return false
        }
    }

    /**
     * Hides the overlay (keeps resources alive for re-showing).
     */
    fun hide() {
        if (!isShowing) return
        try {
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            composeView?.let { windowManager?.removeView(it) }
            isShowing = false
            Log.d(TAG, "Overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding overlay: ${e.message}", e)
        }
    }

    /**
     * Destroys the overlay completely, releasing all resources.
     * Must be called when DroidLens stops or the process is dying.
     */
    fun destroy() {
        try {
            if (isShowing) {
                composeView?.let {
                    try {
                        windowManager?.removeViewImmediate(it)
                    } catch (_: Exception) { /* View might not be attached */ }
                }
            }
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            lifecycleOwner = null
            composeView = null
            layoutParams = null
            windowManager = null
            isShowing = false
            Log.d(TAG, "Overlay destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying overlay: ${e.message}", e)
        }
    }

    /**
     * Returns whether the overlay is currently visible.
     */
    fun isVisible(): Boolean = isShowing

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        composeView?.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // Only start dragging after a 10px threshold to avoid accidental drags
                    if (!isDragging && (dx * dx + dy * dy > 100)) {
                        isDragging = true
                    }

                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try {
                            windowManager?.updateViewLayout(composeView, params)
                        } catch (_: Exception) { /* View might not be attached */ }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // It was a tap, not a drag — toggle minimise
                        viewModel.toggleMinimised()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun mapPositionToGravity(position: OverlayPosition): Int {
        return when (position) {
            OverlayPosition.TOP_RIGHT -> Gravity.TOP or Gravity.END
            OverlayPosition.TOP_LEFT -> Gravity.TOP or Gravity.START
            OverlayPosition.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
            OverlayPosition.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
        }
    }

    /**
     * Custom [LifecycleOwner] and [SavedStateRegistryOwner] for the overlay ComposeView.
     * Required because Compose needs a lifecycle to manage recomposition and disposal.
     */
    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        init {
            savedStateRegistryController.performRestore(null)
        }

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }
}
