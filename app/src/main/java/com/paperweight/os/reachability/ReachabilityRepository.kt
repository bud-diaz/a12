package com.paperweight.os.reachability

import android.content.Context
import com.paperweight.os.data.db.entity.StationProfileEntity
import com.paperweight.os.data.prefs.AppPreferences
import com.paperweight.os.data.prefs.SecurePreferences
import com.paperweight.os.data.repository.StationRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.SecureRandom

/**
 * Composes registration + frpc.toml writing + process supervision behind a
 * simple register/disconnect/status surface for the Station ViewModel.
 * `register(slug)` is idempotent about the registration step: a
 * `secret` already stored in [SecurePreferences] is reused rather than
 * re-registering (mirrors paperweightv1's dashboard "register-and-create"
 * route, which only registers telemetry if it isn't already configured).
 */
class ReachabilityRepository(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val securePreferences: SecurePreferences,
    private val stationRepository: StationRepository,
    private val registrationClient: FrpRegistrationClient = FrpRegistrationClient(),
    private val supervisor: FrpcProcessSupervisor = FrpcProcessSupervisor(frpcBinaryFile(context)),
) {
    val status: StateFlow<TunnelStatus> = supervisor.status

    suspend fun register(slug: String): Result<String> = runCatching {
        val stationKey = securePreferences.installStationKey()
        var secret = securePreferences.registrationSecret
        if (secret == null) {
            secret = randomHex(32)
            registrationClient.register(slug, stationKey, secret)
            securePreferences.registrationSecret = secret
        }

        val credentials = registrationClient.createTunnel(slug, stationKey, secret)
        securePreferences.frpAuthToken = credentials.authToken
        appPreferences.setStationSlug(slug)

        val localPort = appPreferences.serverPort.first()
        val configFile = FrpcConfigWriter.write(context, credentials, localPort)
        val publicUrl = "https://${credentials.hostname}"
        supervisor.start(configFile, publicUrl)
        TunnelHealthCheckWorker.schedule(context)

        val now = System.currentTimeMillis()
        val existing = stationRepository.getProfile()
        stationRepository.upsertProfile(
            (existing ?: StationProfileEntity(
                stationName = appPreferences.stationName.first(),
                localPort = localPort,
                createdAt = now,
                updatedAt = now,
            )).copy(publicUrl = publicUrl, updatedAt = now),
        )
        publicUrl
    }

    fun disconnect() {
        supervisor.stop()
        TunnelHealthCheckWorker.cancel(context)
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** jniLibs-convention path: Android's installer extracts `src/main/jniLibs/arm64-v8a/libfrpc.so` here. */
        fun frpcBinaryFile(context: Context): File = File(context.applicationInfo.nativeLibraryDir, "libfrpc.so")
    }
}
