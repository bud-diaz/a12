package com.paperweight.os.backup

import com.paperweight.os.data.db.AppDatabase
import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val timestamp: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val schemaVersion: Int,
    val databaseFile: String = AppDatabase.DATABASE_NAME,
    val preferencesFile: String = BackupWriter.PREFERENCES_FILE,
)

data class BackupSnapshot(
    val displayName: String,
    val directoryUri: String,
)
