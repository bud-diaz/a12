package com.paperweight.os.backup

import android.content.Context
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import androidx.room.RoomDatabase
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.Instant

class BackupWriter(
    private val context: Context,
    private val database: RoomDatabase,
    private val appPreferences: AppPreferences,
) {
    suspend fun writeBackup(treeRoot: DocumentFile, timestamp: String = safeTimestamp()): BackupSnapshot = withContext(Dispatchers.IO) {
        val backupsDir = BackupDocumentTree.backupsDirectory(treeRoot)
            ?: throw IOException("Unable to create Paperweight/backups on the granted SD-card tree.")
        val snapshotDir = backupsDir.createDirectory(timestamp)
            ?: throw IOException("Unable to create backup snapshot directory $timestamp.")

        val tempDb = File(context.cacheDir, "${AppDatabase.DATABASE_NAME}.$timestamp.tmp").apply { delete() }
        vacuumInto(tempDb)
        snapshotDir.writeFile(AppDatabase.DATABASE_NAME, DATABASE_MIME, tempDb.readBytes())
        tempDb.delete()

        val preferencesJson = json.encodeToString(appPreferences.snapshotNonSecretConfig())
        snapshotDir.writeFile(PREFERENCES_FILE, JSON_MIME, preferencesJson.toByteArray())

        val manifest = BackupManifest(
            timestamp = timestamp,
            appVersionName = appVersionName(),
            appVersionCode = appVersionCode(),
            schemaVersion = 1,
        )
        snapshotDir.writeFile(MANIFEST_FILE, JSON_MIME, json.encodeToString(manifest).toByteArray())

        BackupSnapshot(displayName = timestamp, directoryUri = snapshotDir.uri.toString())
    }

    private fun vacuumInto(destination: File) {
        database.openHelper.writableDatabase.execSQL("VACUUM INTO '${destination.absolutePath.sqlQuote()}'")
    }

    private fun DocumentFile.writeFile(name: String, mimeType: String, bytes: ByteArray) {
        if (uri.scheme == "file") {
            val directory = File(requireNotNull(uri.path) { "Missing file path for backup directory." })
            File(directory, name).outputStream().use { it.write(bytes) }
            return
        }
        findFile(name)?.delete()
        val file = createFile(mimeType, name) ?: throw IOException("Unable to create $name in backup snapshot.")
        context.contentResolver.openOutputStream(file.uri, "w")?.use { it.write(bytes) }
            ?: throw IOException("Unable to open $name for writing.")
    }

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    @Suppress("DEPRECATION")
    private fun appVersionCode(): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(0L)

    private fun String.sqlQuote(): String = replace("'", "''")

    companion object {
        const val PREFERENCES_FILE = "preferences.json"
        const val MANIFEST_FILE = "manifest.json"
        private const val DATABASE_MIME = "application/octet-stream"
        private const val JSON_MIME = "application/json"
        val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

        fun safeTimestamp(): String = Instant.now().toString().replace(':', '-')
    }
}
