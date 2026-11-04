package com.example.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Foreground Service that keeps the download engine running in the background
 * even if the user presses Home, switches apps, or closes the browser screen.
 */
class DownloadForegroundService : Service() {

    companion object {
        const val ACTION_START_OR_UPDATE = "com.example.download.action.START_OR_UPDATE"
        const val ACTION_PAUSE_DOWNLOAD = "com.example.download.action.PAUSE_DOWNLOAD"
        const val ACTION_STOP_SERVICE = "com.example.download.action.STOP_SERVICE"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        fun startOrUpdate(context: Context) {
            runCatching {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.stopService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = DownloadEngine.getInstance(applicationContext)

        when (intent?.action) {
            ACTION_PAUSE_DOWNLOAD -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
                if (downloadId != -1L) {
                    engine.pauseDownload(downloadId)
                }
            }
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val activeDownloads = engine.getActiveSnapshots()
        val notification = DownloadNotificationHelper.buildForegroundServiceNotification(
            context = applicationContext,
            activeCount = activeDownloads.size.coerceAtLeast(1),
            primaryItem = activeDownloads.firstOrNull()
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    DownloadNotificationHelper.FOREGROUND_SUMMARY_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(
                    DownloadNotificationHelper.FOREGROUND_SUMMARY_NOTIFICATION_ID,
                    notification
                )
            }
        }

        if (activeDownloads.isEmpty() && intent?.action != ACTION_START_OR_UPDATE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // START_STICKY ensures the OS keeps the background download service alive if the UI is closed
        return START_STICKY
    }
}
