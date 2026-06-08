package com.droidlens.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Handles SYSTEM_ALERT_WINDOW (DRAW_OVER_APPS) permission checks and fallback notifications.
 *
 * When overlay permission is not granted, shows a notification with an action
 * that opens the system overlay permission settings.
 */
object OverlayPermissionHelper {

    private const val TAG = "DroidLens-Permission"
    private const val CHANNEL_ID = "droidlens_overlay_channel"
    private const val NOTIFICATION_ID = 9001

    /**
     * Returns true if the app has SYSTEM_ALERT_WINDOW permission.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Creates an Intent to open the overlay permission settings for this app.
     */
    fun createOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Shows a notification prompting the user to grant overlay permission.
     * The notification has an action that opens the system overlay settings.
     */
    fun showPermissionNotification(context: Context) {
        createNotificationChannel(context)

        val permissionIntent = createOverlayPermissionIntent(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            permissionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("DroidLens — Overlay Permission Required")
            .setContentText("Tap to grant overlay permission for real-time performance monitoring.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "DroidLens needs \"Display over other apps\" permission to show " +
                        "the floating performance overlay. Tap this notification to open settings."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_manage,
                "Grant Permission",
                pendingIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Permission notification shown")
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted on Android 13+
            Log.w(TAG, "Cannot show notification: ${e.message}")
        }
    }

    /**
     * Dismisses the permission notification if it's currently showing.
     */
    fun dismissPermissionNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DroidLens Overlay",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for DroidLens overlay permission requests"
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }
}
