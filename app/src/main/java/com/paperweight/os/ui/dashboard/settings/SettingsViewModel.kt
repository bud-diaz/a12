package com.paperweight.os.ui.dashboard.settings

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.backup.BackupPruner
import com.paperweight.os.backup.BackupScheduler
import com.paperweight.os.backup.BackupWriter
import com.paperweight.os.backup.RecoveryInfoExporter
import com.paperweight.os.di.ServiceLocator
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val services = ServiceLocator.get(application)
    private val preferences = services.appPreferences

    private val _state = MutableStateFlow<ScreenState<SettingsUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<SettingsUiState>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            combine(
                preferences.stationName,
                preferences.serverPort,
                preferences.backupRetentionCount,
                preferences.backupIntervalHours,
                preferences.vaultTreeUri,
            ) { stationName, serverPort, retention, interval, treeUri ->
                val current = (_state.value as? ScreenState.Content)?.data
                SettingsUiState(
                    stationName = stationName,
                    serverPort = serverPort,
                    backupRetentionCount = retention,
                    backupIntervalHours = interval,
                    vaultTreeGranted = treeUri != null,
                    lastBackupName = current?.lastBackupName,
                    recoveryInfo = current?.recoveryInfo,
                    actionMessage = current?.actionMessage,
                    actionInFlight = current?.actionInFlight ?: false,
                )
            }.collect { _state.value = ScreenState.Content(it) }
        }
    }

    fun saveBackupSettings(retentionCount: Int, intervalHours: Int) {
        preferences.setBackupRetentionCount(retentionCount.coerceAtLeast(1))
        preferences.setBackupIntervalHours(intervalHours.coerceAtLeast(1))
        BackupScheduler.schedule(getApplication(), intervalHours.coerceAtLeast(1))
        mutate { copy(actionMessage = "Backup schedule saved.") }
    }

    fun backUpNow() {
        viewModelScope.launch {
            mutate { copy(actionInFlight = true, actionMessage = "Backing up now…") }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val treeUri = preferences.vaultTreeUri.first() ?: error("Choose the Paperweight SD-card folder from Vault before backing up.")
                    val root = DocumentFile.fromTreeUri(getApplication(), Uri.parse(treeUri))
                        ?: error("The saved SD-card folder grant is no longer readable.")
                    val snapshot = BackupWriter(getApplication(), services.database, preferences).writeBackup(root)
                    BackupPruner.prune(root, preferences.backupRetentionCount.first())
                    snapshot
                }
            }
            result.fold(
                onSuccess = { snapshot -> mutate { copy(actionInFlight = false, lastBackupName = snapshot.displayName, actionMessage = "Backup written: ${snapshot.displayName}") } },
                onFailure = { error -> mutate { copy(actionInFlight = false, actionMessage = error.message ?: "Backup failed.") } },
            )
        }
    }

    fun showRecoveryInfo() {
        mutate { copy(recoveryInfo = RecoveryInfoExporter.message(), actionMessage = "Recovery info displayed.") }
    }

    fun notify(message: String) {
        mutate { copy(actionMessage = message) }
    }

    // Legacy handlers kept so any surviving old composable references fail soft rather than silently succeeding.
    fun saveNotifications(webhookUrl: String, liveEnabled: Boolean) = notify(LOCAL_ONLY_SETTINGS)
    fun saveFeed(enabled: Boolean, scope: String) = notify(LOCAL_ONLY_SETTINGS)
    fun saveGlowColor(color: String) = notify(LOCAL_ONLY_SETTINGS)
    fun generateResetLink(email: String) = notify(LOCAL_ONLY_SETTINGS)
    fun selectDoc(id: String, title: String) = notify(LOCAL_ONLY_SETTINGS)
    fun closeDoc() = notify(LOCAL_ONLY_SETTINGS)

    private fun mutate(block: SettingsUiState.() -> SettingsUiState) {
        val current = (_state.value as? ScreenState.Content)?.data ?: SettingsUiState()
        _state.value = ScreenState.Content(current.block())
    }

    private companion object {
        const val LOCAL_ONLY_SETTINGS = "This settings panel is now local-only; remote account/feed/webhook tools were removed by the on-device pivot."
    }
}
