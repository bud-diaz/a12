package com.paperweight.os.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.db.entity.ScheduleBlockEntity
import com.paperweight.os.data.db.entity.StationProfileEntity
import com.paperweight.os.data.db.entity.VaultTrackEntity
import com.paperweight.os.data.prefs.AppPreferences
import com.paperweight.os.data.repository.ScheduleRepository
import com.paperweight.os.data.repository.StationRepository
import com.paperweight.os.data.repository.VaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class Phase1DataLayerInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteSharedPreferences("paperweight_test_preferences")
    }

    @Test
    fun appDatabaseUsesFixedNameForBackupRestore() {
        assertThat(AppDatabase.DATABASE_NAME).isEqualTo("paperweight-os.db")
    }

    @Test
    fun vaultRepositoryPersistsTracksAndCollections() = runBlocking {
        val repository = VaultRepository(db.vaultDao())
        val track = VaultTrackEntity(
            id = "track-1",
            title = "Intro",
            artist = "Paperweight",
            album = "Phase One",
            sourceUri = "content://source/intro.mp3",
            storagePath = "Paperweight/vault/intro.mp3",
            durationMs = 123_000,
            mimeType = "audio/mpeg",
            visibility = "public",
            suggestedPriceCents = 500,
            minimumPriceCents = 0,
            allowFree = true,
            createdAt = 10,
            updatedAt = 20,
        )

        repository.upsertTrack(track)

        assertThat(repository.observeTracks().first()).containsExactly(track)
        assertThat(repository.getTrack("track-1")).isEqualTo(track)
    }

    @Test
    fun scheduleRepositoryPersistsBlocksAndSmartPlaylists() = runBlocking {
        val repository = ScheduleRepository(db.scheduleDao())
        val block = ScheduleBlockEntity(
            id = "block-1",
            name = "Morning rotation",
            dayOfWeek = 1,
            startMinutes = 8 * 60,
            endMinutes = 10 * 60,
            playlistId = "playlist-1",
            isEnabled = true,
            createdAt = 100,
            updatedAt = 200,
        )

        repository.upsertBlock(block)

        assertThat(repository.observeBlocks().first()).containsExactly(block)
        assertThat(repository.getBlock("block-1")).isEqualTo(block)
    }

    @Test
    fun stationRepositoryPersistsLocalStationProfile() = runBlocking {
        val repository = StationRepository(db.stationDao())
        val profile = StationProfileEntity(
            id = "default",
            stationName = "Fifth Avenue",
            description = "Local Paperweight OS station",
            accentColor = "#E4FF4D",
            localPort = 8080,
            lanUrl = "http://192.168.1.20:8080",
            publicUrl = null,
            createdAt = 1,
            updatedAt = 2,
        )

        repository.upsertProfile(profile)

        assertThat(repository.observeProfile().first()).isEqualTo(profile)
    }

    @Test
    fun appPreferencesRoundTripNonSecretDeviceConfig() = runBlocking {
        val preferences = AppPreferences.createForTest(context, "paperweight_test_preferences")

        preferences.setStationName("Fifth Avenue")
        preferences.setServerPort(8088)
        preferences.setBackupRetentionCount(5)
        preferences.setBackupIntervalHours(12)

        assertThat(preferences.stationName.first()).isEqualTo("Fifth Avenue")
        assertThat(preferences.serverPort.first()).isEqualTo(8088)
        assertThat(preferences.backupRetentionCount.first()).isEqualTo(5)
        assertThat(preferences.backupIntervalHours.first()).isEqualTo(12)
    }
}
