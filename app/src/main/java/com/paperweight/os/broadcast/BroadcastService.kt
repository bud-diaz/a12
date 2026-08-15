package com.paperweight.os.broadcast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.paperweight.os.R
import com.paperweight.os.data.db.entity.StationProfileEntity
import com.paperweight.os.di.ServiceLocator
import com.paperweight.os.server.LanAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BroadcastService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        foreground(isMicLive = false, text = "Broadcast engine starting…")
        val services = ServiceLocator.get(this)
        services.broadcastEngine.start()
        observeForegroundType(services.broadcastEngine)
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

    private fun observeForegroundType(engine: BroadcastEngine) {
        serviceScope.launch {
            engine.state
                .map { it.isMicLive to (it.nowPlayingTitle ?: "Broadcast engine running") }
                .distinctUntilChanged()
                .collect { (isMicLive, title) ->
                    val text = if (isMicLive) "Live mic active" else title
                    foreground(isMicLive = isMicLive, text = text)
                }
        }
    }

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

    private fun foreground(isMicLive: Boolean, text: String) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(text),
            foregroundType(isMicLive),
        )
    }

    private fun foregroundType(isMicLive: Boolean): Int {
        var type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        if (isMicLive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return type
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
