package com.paperweight.os.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AppPreferences private constructor(private val sharedPreferences: SharedPreferences) {
    val stationName: Flow<String> = stringFlow(KEY_STATION_NAME, DEFAULT_STATION_NAME)
    val serverPort: Flow<Int> = intFlow(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
    val backupRetentionCount: Flow<Int> = intFlow(KEY_BACKUP_RETENTION_COUNT, DEFAULT_BACKUP_RETENTION_COUNT)
    val backupIntervalHours: Flow<Int> = intFlow(KEY_BACKUP_INTERVAL_HOURS, DEFAULT_BACKUP_INTERVAL_HOURS)

    // Persisted SAF tree URI granted over the SD card's Paperweight root
    // folder (plan decision #10) — not a secret, just a permission handle
    // for content already physically present on the card, so it stays in
    // plain (non-encrypted) prefs alongside the other non-secret config.
    val vaultTreeUri: Flow<String?> = stringFlow(KEY_VAULT_TREE_URI, "").map { it.ifEmpty { null } }

    fun setStationName(value: String) = sharedPreferences.edit().putString(KEY_STATION_NAME, value).apply()
    fun setServerPort(value: Int) = sharedPreferences.edit().putInt(KEY_SERVER_PORT, value).apply()
    fun setBackupRetentionCount(value: Int) = sharedPreferences.edit().putInt(KEY_BACKUP_RETENTION_COUNT, value).apply()
    fun setBackupIntervalHours(value: Int) = sharedPreferences.edit().putInt(KEY_BACKUP_INTERVAL_HOURS, value).apply()
    fun setVaultTreeUri(value: Uri?) = sharedPreferences.edit().putString(KEY_VAULT_TREE_URI, value?.toString() ?: "").apply()

    private fun stringFlow(key: String, defaultValue: String): Flow<String> = callbackFlow {
        fun emitCurrent() { trySend(sharedPreferences.getString(key, defaultValue) ?: defaultValue) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey -> if (changedKey == key) emitCurrent() }
        emitCurrent()
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private fun intFlow(key: String, defaultValue: Int): Flow<Int> = callbackFlow {
        fun emitCurrent() { trySend(sharedPreferences.getInt(key, defaultValue)) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey -> if (changedKey == key) emitCurrent() }
        emitCurrent()
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    companion object {
        private const val PREFS_NAME = "paperweight_preferences"
        private const val KEY_STATION_NAME = "station_name"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_BACKUP_RETENTION_COUNT = "backup_retention_count"
        private const val KEY_BACKUP_INTERVAL_HOURS = "backup_interval_hours"
        private const val KEY_VAULT_TREE_URI = "vault_tree_uri"
        const val DEFAULT_STATION_NAME = "Paperweight Station"
        const val DEFAULT_SERVER_PORT = 8080
        const val DEFAULT_BACKUP_RETENTION_COUNT = 7
        const val DEFAULT_BACKUP_INTERVAL_HOURS = 24

        fun create(context: Context): AppPreferences = createForTest(context, PREFS_NAME)
        fun createForTest(context: Context, preferencesName: String): AppPreferences =
            AppPreferences(context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE))
    }
}
