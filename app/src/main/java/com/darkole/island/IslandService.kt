
package com.darkole.island

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class IslandService : Service() {

    private lateinit var overlayManager: IslandOverlayManager

    override fun onCreate() {
        super.onCreate()
        overlayManager = IslandOverlayManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_HIDE -> {
                overlayManager.hide()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SHOW, null -> {
                startIslandForeground()
                overlayManager.show()
            }
        }

        return START_STICKY
    }

    private fun startIslandForeground() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Darkole Island")
            .setContentText("Плавающий остров активен")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Darkole Island",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Работа плавающего острова"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (::overlayManager.isInitialized) {
            overlayManager.hide()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "com.darkole.island.SHOW"
        const val ACTION_HIDE = "com.darkole.island.HIDE"

        private const val CHANNEL_ID = "darkole_island_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
