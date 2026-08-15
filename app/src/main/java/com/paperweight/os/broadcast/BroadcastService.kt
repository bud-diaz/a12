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
import com.paperweight.os.data.db.entity.StationProfileEntity
import com.paperweight.os.di.ServiceLocator
import com.paperweight.os.server.LanAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BroadcastService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("Broadcast engine starting…"))
        val services = ServiceLocator.get(this)
        services.broadcastEngine.start()
        services.embeddedHttpServer.startServer()
        publishLanUrl()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val services = ServiceLocator.get(this)
        services.broadcastEngine.start()
        services.embeddedHttpServer.startServer()
        return START_STICKY
    }

    override fun onDestroy() {
        ServiceLocator.get(this).embeddedHttpServer.stopServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publishLanUrl() {
        val services = ServiceLocator.get(this)
        serviceScope.launch {
            val port = services.appPreferences.serverPort.first()
            val ip = LanAddress.currentIpv4(this@BroadcastService)
            val lanUrl = ip?.let { "http://$it:$port" }
            val stationName = services.appPreferences.stationName.first()
            val existing = services.stationRepository.getProfile()
            val now = System.currentTimeMillis()
            services.stationRepository.upsertProfile(
                (existing ?: StationProfileEntity(
                    stationName = stationName,
                    localPort = port,
                    createdAt = now,
                    updatedAt = now,
                )).copy(localPort = port, lanUrl = lanUrl, updatedAt = now),
            )
        }
    }

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
