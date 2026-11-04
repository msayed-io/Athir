package com.athir.datatransfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class TransferService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Athir::TransferWakeLock").apply {
            setReferenceCounted(false)
            acquire(1000 * 60 * 60) // 1 hour max
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Athir::TransferWifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val fileName = intent?.getStringExtra(EXTRA_FILE_NAME) ?: "جارٍ نقل الملفات…"
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val speedStr = intent?.getStringExtra(EXTRA_SPEED) ?: ""

        val notification = buildNotification(fileName, progress, speedStr)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    fun updateNotification(fileName: String, progress: Int, speedStr: String) {
        val notification = buildNotification(fileName, progress, speedStr)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(fileName: String, progress: Int, speedStr: String): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TransferService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (speedStr.isNotBlank()) "$progress% · $speedStr" else "$progress%"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(fileName)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إلغاء", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "نقل ملفات أثير"
            val descriptionText = "إشعارات نقل وتبادل الملفات المباشر"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            wakeLock?.release()
        } catch (ignored: Exception) {}
        try {
            wifiLock?.release()
        } catch (ignored: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "athir_transfer_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.athir.datatransfer.START_TRANSFER"
        const val ACTION_UPDATE = "com.athir.datatransfer.UPDATE_TRANSFER"
        const val ACTION_STOP = "com.athir.datatransfer.STOP_TRANSFER"

        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_SPEED = "extra_speed"

        fun start(context: Context, fileName: String) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_PROGRESS, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, fileName: String, progress: Int, speedStr: String) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_SPEED, speedStr)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
