package com.paperweight.os.backup

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.db.entity.VaultTrackEntity
import com.paperweight.os.data.prefs.AppPreferences
import com.paperweight.os.data.repository.VaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class Phase3BackupRecoveryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var rootDir: File
    private lateinit var paperweightRoot: DocumentFile

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        context.deleteSharedPreferences(TEST_PREFS)
        rootDir = File(context.cacheDir, "phase3-backup-root").apply {
            deleteRecursively()
            mkdirs()
        }
        paperweightRoot = DocumentFile.fromFile(File(rootDir, "Paperweight").apply { mkdirs() })
    }

    @After
    fun tearDown() {
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        context.deleteSharedPreferences(TEST_PREFS)
        rootDir.deleteRecursively()
    }

    @Test
    fun backupWriterSnapshotsDatabasePreferencesAndManifestIntoBackupsFolder() = runBlocking {
        val db = openPersistentDatabase()
        val preferences = AppPreferences.createForTest(context, TEST_PREFS)
        val repository = VaultRepository(db.vaultDao())
        val track = phase3Track("track-backup")
        repository.upsertTrack(track)
        preferences.setStationName("Fifth Avenue")
        preferences.setServerPort(9090)
        preferences.setBackupRetentionCount(3)
        preferences.setBackupIntervalHours(6)

        val snapshot = BackupWriter(context, db, preferences).writeBackup(paperweightRoot, timestamp = "2026-08-14T18-30-00Z")
        db.close()

        val snapshotDir = File(rootDir, "Paperweight/backups/2026-08-14T18-30-00Z")
        assertThat(snapshot.displayName).isEqualTo("2026-08-14T18-30-00Z")
        assertThat(File(snapshotDir, AppDatabase.DATABASE_NAME).isFile).isTrue()
        val prefsJson = Json.parseToJsonElement(File(snapshotDir, "preferences.json").readText()).jsonObject
        assertThat(prefsJson["stationName"]!!.jsonPrimitive.content).isEqualTo("Fifth Avenue")
        val manifestJson = Json.parseToJsonElement(File(snapshotDir, "manifest.json").readText()).jsonObject
        assertThat(manifestJson["schemaVersion"]!!.jsonPrimitive.content).isEqualTo("1")
        assertThat(manifestJson["databaseFile"]!!.jsonPrimitive.content).isEqualTo(AppDatabase.DATABASE_NAME)
    }

    @Test
    fun backupPrunerKeepsNewestSnapshotsOnly() {
        val backups = File(rootDir, "Paperweight/backups").apply { mkdirs() }
        listOf("2026-08-14T01-00-00Z", "2026-08-14T02-00-00Z", "2026-08-14T03-00-00Z").forEach {
            File(backups, it).mkdirs()
        }

        BackupPruner.prune(paperweightRoot, keepCount = 2)

        assertThat(backups.list()?.toList()).containsExactly("2026-08-14T02-00-00Z", "2026-08-14T03-00-00Z")
    }

    @Test
    fun restoreManagerDetectsNewestAvailableBackupBeforeRoomOpens() = runBlocking {
        val preferences = AppPreferences.createForTest(context, TEST_PREFS)
        val db = openPersistentDatabase()
        listOf("2026-08-14T18-00-00Z", "2026-08-14T19-00-00Z").forEach { timestamp ->
            BackupWriter(context, db, preferences).writeBackup(paperweightRoot, timestamp = timestamp)
        }
        db.close()

        val latest = RestoreManager(context, preferences).findLatestSnapshot(paperweightRoot)

        assertThat(latest?.displayName).isEqualTo("2026-08-14T19-00-00Z")
    }

    @Test
    fun restoreManagerRestoresDatabaseAndNonSecretPreferencesBeforeRoomOpens() = runBlocking {
        val preferences = AppPreferences.createForTest(context, TEST_PREFS)
        val originalDb = openPersistentDatabase()
        val originalTrack = phase3Track("track-restore")
        VaultRepository(originalDb.vaultDao()).upsertTrack(originalTrack)
        preferences.setStationName("Recovered Station")
        preferences.setServerPort(8181)
        BackupWriter(context, originalDb, preferences).writeBackup(paperweightRoot, timestamp = "2026-08-14T19-00-00Z")
        originalDb.close()

        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        context.deleteSharedPreferences(TEST_PREFS)
        val restoredPreferences = AppPreferences.createForTest(context, TEST_PREFS)

        val restored = RestoreManager(context, restoredPreferences).restoreLatest(paperweightRoot)

        assertThat(restored?.displayName).isEqualTo("2026-08-14T19-00-00Z")
        val restoredDb = openPersistentDatabase()
        assertThat(VaultRepository(restoredDb.vaultDao()).getTrack("track-restore")).isEqualTo(originalTrack)
        assertThat(restoredPreferences.stationName.first()).isEqualTo("Recovered Station")
        assertThat(restoredPreferences.serverPort.first()).isEqualTo(8181)
        restoredDb.close()
    }

    private fun openPersistentDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME,
    ).allowMainThreadQueries().build()

    private fun phase3Track(id: String): VaultTrackEntity = VaultTrackEntity(
        id = id,
        title = "Phase 3 Track",
        artist = "Paperweight",
        album = "Backup Test",
        sourceUri = "content://source/$id.wav",
        storagePath = "content://vault/$id.wav",
        durationMs = 45_000,
        mimeType = "audio/wav",
        visibility = "public",
        suggestedPriceCents = 500,
        minimumPriceCents = 0,
        allowFree = true,
        createdAt = 1,
        updatedAt = 2,
    )

    private companion object {
        const val TEST_PREFS = "paperweight_phase3_test_preferences"
    }
}
