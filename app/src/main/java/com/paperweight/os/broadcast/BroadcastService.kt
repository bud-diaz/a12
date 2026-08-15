package com.paperweight.os.broadcast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.paperweight.os.R
import com.paperweight.os.di.ServiceLocator

class BroadcastService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("Broadcast engine starting…"))
        ServiceLocator.get(this).broadcastEngine.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceLocator.get(this).broadcastEngine.start()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Paperweight broadcast",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps the local Paperweight broadcast engine running."
                },
            )
        }
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Paperweight broadcast")
        .setContentText(text)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private companion object {
        const val CHANNEL_ID = "paperweight_broadcast"
        const val NOTIFICATION_ID = 404
    }
}
