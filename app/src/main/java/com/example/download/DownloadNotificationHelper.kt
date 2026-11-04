package com.example.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.DownloadEntity

/**
 * Handles top status bar notifications for active and completed downloads,
 * as well as the gentle haptic vibration upon download completion.
 */
object DownloadNotificationHelper {

    const val CHANNEL_PROGRESS_ID = "madar_download_progress_channel"
    const val CHANNEL_COMPLETE_ID = "madar_download_complete_channel"
    const val FOREGROUND_SUMMARY_NOTIFICATION_ID = 9001
    private const val PROGRESS_NOTIFICATION_BASE_ID = 10000
    private const val COMPLETE_NOTIFICATION_BASE_ID = 20000

    fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "التحميلات الجارية (شريط التقدم)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "يعرض نسبة تقدم وسرعة تحميل الملفات في شريط المهام العلوي"
                setShowBadge(false)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE_ID,
                "اكتمال التحميلات",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات وتنبيهات عند اكتمال تحميل الملفات أو توقفها"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 90, 60, 140)
            }

            manager.createNotificationChannel(progressChannel)
            manager.createNotificationChannel(completeChannel)
        }
    }

    /**
     * Triggers a gentle, distinct double-pulse vibration when a download completes.
     */
    @Suppress("DEPRECATION")
    fun triggerCompletionVibration(context: Context) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(
                        longArrayOf(0, 85, 55, 130),
                        -1
                    )
                    vibrator.vibrate(effect)
                } else {
                    vibrator.vibrate(180)
                }
            }
        }
    }

    /**
     * Builds the foreground service notification keeping background downloads alive
     * even when the user exits the browser.
     */
    fun buildForegroundServiceNotification(
        context: Context,
        activeCount: Int,
        primaryItem: DownloadEntity?
    ): Notification {
        ensureChannelsCreated(context)
        val openAppIntent = createOpenDownloadsPendingIntent(context)

        val title = when {
            primaryItem != null && activeCount == 1 -> "جاري تحميل: ${primaryItem.fileName}"
            activeCount > 1 -> "جاري تحميل $activeCount ملفات في الخلفية"
            else -> "محرك تحميل متصفح مدار نشط"
        }

        val contentText = if (primaryItem != null) {
            val sizeInfo = if (primaryItem.totalBytes > 0) {
                "${StorageHelper.formatBytes(primaryItem.downloadedBytes)} / ${StorageHelper.formatBytes(primaryItem.totalBytes)} (${primaryItem.progressPercent}%)"
            } else {
                StorageHelper.formatBytes(primaryItem.downloadedBytes)
            }
            val speedInfo = StorageHelper.formatSpeed(primaryItem.speedBytesPerSec)
            "$sizeInfo • $speedInfo"
        } else {
            "يستمر التحميل حتى عند الخروج من المتصفح"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText("متصفح مدار")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (primaryItem != null) {
            val indeterminate = primaryItem.totalBytes <= 0
            builder.setProgress(100, primaryItem.progressPercent, indeterminate)

            // Pause Action inside Status Bar Notification
            val pauseIntent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_PAUSE_DOWNLOAD
                putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, primaryItem.id)
            }
            val pausePendingIntent = PendingIntent.getService(
                context,
                primaryItem.id.toInt(),
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "إيقاف مؤقت",
                pausePendingIntent
            )
        }

        return builder.build()
    }

    /**
     * Posts or updates an individual download progress notification in the top status bar.
     */
    fun showProgressNotification(context: Context, item: DownloadEntity) {
        if (!canPostNotifications(context)) return
        ensureChannelsCreated(context)

        val sizeInfo = if (item.totalBytes > 0) {
            "${StorageHelper.formatBytes(item.downloadedBytes)} / ${StorageHelper.formatBytes(item.totalBytes)} (${item.progressPercent}%)"
        } else {
            StorageHelper.formatBytes(item.downloadedBytes)
        }
        val speedInfo = StorageHelper.formatSpeed(item.speedBytesPerSec)

        val pauseIntent = Intent(context, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_PAUSE_DOWNLOAD
            putExtra(DownloadForegroundService.EXTRA_DOWNLOAD_ID, item.id)
        }
        val pausePendingIntent = PendingIntent.getService(
            context,
            item.id.toInt() + 5000,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item.fileName)
            .setContentText("$sizeInfo • $speedInfo")
            .setSubText("جاري التحميل - متصفح مدار")
            .setProgress(100, item.progressPercent, item.totalBytes <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .addAction(android.R.drawable.ic_media_pause, "إيقاف مؤقت", pausePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(PROGRESS_NOTIFICATION_BASE_ID + item.id.toInt(), notification)
        }
    }

    fun cancelProgressNotification(context: Context, downloadId: Long) {
        runCatching {
            NotificationManagerCompat.from(context)
                .cancel(PROGRESS_NOTIFICATION_BASE_ID + downloadId.toInt())
        }
    }

    /**
     * Posts a high-priority completion notification in the top status bar when a file finishes downloading.
     */
    fun showCompletedNotification(context: Context, item: DownloadEntity) {
        cancelProgressNotification(context, item.id)
        if (!canPostNotifications(context)) return
        ensureChannelsCreated(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("اكتمل التحميل: ${item.fileName}")
            .setContentText("الحجم: ${StorageHelper.formatBytes(item.downloadedBytes)} • حُفظ في ${item.customFolderName}")
            .setSubText("متصفح مدار")
            .setAutoCancel(true)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(COMPLETE_NOTIFICATION_BASE_ID + item.id.toInt(), notification)
        }
    }

    /**
     * Posts a failure notification if a download encounters an error.
     */
    fun showFailedNotification(context: Context, item: DownloadEntity, reason: String) {
        cancelProgressNotification(context, item.id)
        if (!canPostNotifications(context)) return
        ensureChannelsCreated(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETE_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("تعذر تحميل: ${item.fileName}")
            .setContentText(reason)
            .setSubText("متصفح مدار")
            .setAutoCancel(true)
            .setContentIntent(createOpenDownloadsPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(COMPLETE_NOTIFICATION_BASE_ID + item.id.toInt(), notification)
        }
    }

    private fun createOpenDownloadsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_DOWNLOADS_TAB", true)
        }
        return PendingIntent.getActivity(
            context,
            801,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
