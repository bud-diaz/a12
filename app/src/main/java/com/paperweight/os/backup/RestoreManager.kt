package com.paperweight.os.backup

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.prefs.AppPreferences
import com.paperweight.os.data.prefs.NonSecretConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.io.IOException

class RestoreManager(
    private val context: Context,
    private val appPreferences: AppPreferences,
) {
    suspend fun findLatestSnapshot(treeRoot: DocumentFile): BackupSnapshot? = withContext(Dispatchers.IO) {
        latestSnapshot(treeRoot)?.let { BackupSnapshot(displayName = it.name ?: "", directoryUri = it.uri.toString()) }
    }

    suspend fun restoreLatest(treeRoot: DocumentFile): BackupSnapshot? = withContext(Dispatchers.IO) {
        val snapshot = latestSnapshot(treeRoot) ?: return@withContext null
        restoreSnapshot(snapshot)
        BackupSnapshot(displayName = snapshot.name ?: "", directoryUri = snapshot.uri.toString())
    }

    private fun latestSnapshot(treeRoot: DocumentFile): DocumentFile? = BackupDocumentTree.backupsDirectory(treeRoot)
        ?.listFiles()
        ?.filter { it.isDirectory && hasRestorePayload(it) }
        ?.maxByOrNull { it.name ?: "" }

    private fun hasRestorePayload(snapshot: DocumentFile): Boolean =
        snapshot.findFile(AppDatabase.DATABASE_NAME) != null && snapshot.findFile(BackupWriter.PREFERENCES_FILE) != null

    private fun restoreSnapshot(snapshot: DocumentFile) {
        val dbFile = snapshot.findFile(AppDatabase.DATABASE_NAME)
            ?: throw IOException("Backup is missing ${AppDatabase.DATABASE_NAME}.")
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val destination = context.getDatabasePath(AppDatabase.DATABASE_NAME).apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(dbFile.uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Unable to read backup database.")

        val prefsFile = snapshot.findFile(BackupWriter.PREFERENCES_FILE)
            ?: throw IOException("Backup is missing ${BackupWriter.PREFERENCES_FILE}.")
        val prefsJson = context.contentResolver.openInputStream(prefsFile.uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IOException("Unable to read backup preferences.")
        appPreferences.restoreNonSecretConfig(BackupWriter.json.decodeFromString<NonSecretConfig>(prefsJson))
    }
}
