package com.paperweight.os.reachability

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paperweight.os.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Periodic (~15 min) "is the public URL still up" check only — does not
 * restart `frpc` itself (the [FrpcProcessSupervisor] already self-heals its
 * own process; this just updates `StationProfileEntity.lastReachableAt` for
 * the Station screen to display), per plan decision #6.
 */
class TunnelHealthCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val services = ServiceLocator.get(applicationContext)
        val profile = services.stationRepository.getProfile() ?: return@withContext Result.success()
        val publicUrl = profile.publicUrl ?: return@withContext Result.success()

        val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
        val reachable = runCatching {
            client.newCall(Request.Builder().url(publicUrl).head().build()).execute().use { it.code < 500 }
        }.getOrDefault(false)

        if (reachable) {
            services.stationRepository.upsertProfile(profile.copy(lastReachableAt = System.currentTimeMillis()))
        }
        Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "tunnel_health_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TunnelHealthCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
