package com.paperweight.os.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Encrypted-at-rest storage for the frp reachability secrets (plan decision
 * #6/#12): the registration `secret` posted to system.pape and the `authToken`
 * frpc uses to authenticate to the gateway. Deliberately a separate file from
 * [AppPreferences] so these fields can never accidentally be pulled into
 * [AppPreferences.snapshotNonSecretConfig] and round-trip through the
 * automatic SD-card backup — the Android Keystore key backing this file does
 * not survive a reinstall/factory reset, so these values need
 * `RecoveryInfoExporter`'s manual one-time reveal instead, not an automatic
 * backup that would just silently fail to restore anything real.
 */
class SecurePreferences private constructor(private val sharedPreferences: SharedPreferences) {

    /** Stable per-install identifier, generated once. Mirrors paperweightv1's `pwinst_<32-hex>` install key. */
    fun installStationKey(): String {
        sharedPreferences.getString(KEY_STATION_KEY, null)?.let { return it }
        val generated = "pwinst_${randomHex(16)}"
        sharedPreferences.edit().putString(KEY_STATION_KEY, generated).apply()
        return generated
    }

    var registrationSecret: String?
        get() = sharedPreferences.getString(KEY_REGISTRATION_SECRET, null)
        set(value) { sharedPreferences.edit().putString(KEY_REGISTRATION_SECRET, value).apply() }

    var frpAuthToken: String?
        get() = sharedPreferences.getString(KEY_FRP_AUTH_TOKEN, null)
        set(value) { sharedPreferences.edit().putString(KEY_FRP_AUTH_TOKEN, value).apply() }

    fun clearFrpCredentials() {
        sharedPreferences.edit().remove(KEY_REGISTRATION_SECRET).remove(KEY_FRP_AUTH_TOKEN).apply()
    }

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS_NAME = "paperweight_secure_preferences"
        private const val KEY_STATION_KEY = "station_key"
        private const val KEY_REGISTRATION_SECRET = "registration_secret"
        private const val KEY_FRP_AUTH_TOKEN = "frp_auth_token"

        fun create(context: Context): SecurePreferences {
            val appContext = context.applicationContext
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return SecurePreferences(prefs)
        }
    }
}
