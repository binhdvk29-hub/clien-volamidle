package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var lockRefreshRunnable: Runnable? = null

    companion object {
        const val CHANNEL_ID = "volam_idle_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.action.START_KEEP_ALIVE"
        const val ACTION_STOP = "com.example.action.STOP_KEEP_ALIVE"
        private const val WAKELOCK_DURATION_MS = 12 * 60 * 60 * 1000L
        private const val LOCK_RENEW_INTERVAL_MS = 15 * 60 * 1000L

        fun start(context: Context) {
            try {
                val intent = Intent(context, KeepAliveService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, KeepAliveService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        acquireWifiLock()
        scheduleLockRefresh()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cancelLockRefresh()
            releaseWifiLock()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        acquireWifiLock()
        scheduleLockRefresh()

        val notification = buildNotification("Đang duy trì kết nối 24/7 (Chống ngắt & tiết kiệm pin)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun scheduleLockRefresh() {
        if (lockRefreshRunnable != null) return
        val r = object : Runnable {
            override fun run() {
                acquireWakeLock(forceRenew = true)
                acquireWifiLock()
                refreshHandler.postDelayed(this, LOCK_RENEW_INTERVAL_MS)
            }
        }
        lockRefreshRunnable = r
        refreshHandler.postDelayed(r, LOCK_RENEW_INTERVAL_MS)
    }

    private fun cancelLockRefresh() {
        lockRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        lockRefreshRunnable = null
    }

    private fun acquireWakeLock(forceRenew: Boolean = false) {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "VoLamIdle::KeepAliveWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (forceRenew || wakeLock?.isHeld == false) {
                wakeLock?.acquire(WAKELOCK_DURATION_MS)
            }
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock == null || wifiLock?.isHeld == false) {
            try {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifiManager?.createWifiLock(lockMode, "VoLamIdle::LowLatencyWifiLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } catch (_: Exception) {}
        }
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
        wifiLock = null
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Võ Lâm Idle Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì kết nối mạng cho các tài khoản Võ Lâm Idle"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Võ Lâm Idle Manager")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        cancelLockRefresh()
        releaseWifiLock()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
