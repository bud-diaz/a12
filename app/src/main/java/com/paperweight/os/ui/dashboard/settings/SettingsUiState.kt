package com.paperweight.os.ui.dashboard.settings

data class SettingsUiState(
    val stationName: String = "Paperweight Station",
    val serverPort: Int = 8080,
    val backupRetentionCount: Int = 7,
    val backupIntervalHours: Int = 24,
    val vaultTreeGranted: Boolean = false,
    val stationSlug: String? = null,
    val tunnelStatusText: String = "Not registered",
    val lastBackupName: String? = null,
    val recoveryInfo: String? = null,
    val actionMessage: String? = null,
    val actionInFlight: Boolean = false,
)
