package com.paperweight.os.di

import android.content.Context
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.prefs.AppPreferences
import com.paperweight.os.data.prefs.SecurePreferences
import com.paperweight.os.data.repository.AnalyticsRepository
import com.paperweight.os.data.repository.BroadcastRepository
import com.paperweight.os.data.repository.ScheduleRepository
import com.paperweight.os.data.repository.StationRepository
import com.paperweight.os.data.repository.TokenRepository
import com.paperweight.os.data.repository.VaultRepository
import com.paperweight.os.broadcast.BroadcastEngine
import com.paperweight.os.reachability.ReachabilityRepository
import com.paperweight.os.server.EmbeddedHttpServer
import com.paperweight.os.vault.VaultIngestor

class ServiceLocator private constructor(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val appPreferences: AppPreferences by lazy { AppPreferences.create(appContext) }
    val securePreferences: SecurePreferences by lazy { SecurePreferences.create(appContext) }
    val vaultRepository: VaultRepository by lazy { VaultRepository(database.vaultDao()) }
    val vaultIngestor: VaultIngestor by lazy { VaultIngestor(vaultRepository, appPreferences) }
    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepository(database.scheduleDao()) }
    val tokenRepository: TokenRepository by lazy { TokenRepository(database.tokenDao()) }
    val analyticsRepository: AnalyticsRepository by lazy { AnalyticsRepository(database.analyticsDao()) }
    val stationRepository: StationRepository by lazy { StationRepository(database.stationDao()) }
    val broadcastRepository: BroadcastRepository by lazy { BroadcastRepository(vaultRepository, scheduleRepository, stationRepository) }
    val broadcastEngine: BroadcastEngine by lazy { BroadcastEngine(appContext, broadcastRepository) }
    val embeddedHttpServer: EmbeddedHttpServer by lazy { EmbeddedHttpServer(appContext, appPreferences, broadcastEngine) }
    val reachabilityRepository: ReachabilityRepository by lazy {
        ReachabilityRepository(appContext, appPreferences, securePreferences, stationRepository)
    }

    companion object {
        @Volatile private var INSTANCE: ServiceLocator? = null
        fun get(context: Context): ServiceLocator = INSTANCE ?: synchronized(this) {
            INSTANCE ?: ServiceLocator(context).also { INSTANCE = it }
        }
    }
}
