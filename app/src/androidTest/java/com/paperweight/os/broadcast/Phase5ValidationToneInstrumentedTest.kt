package com.paperweight.os.broadcast

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.repository.BroadcastRepository
import com.paperweight.os.data.repository.ScheduleRepository
import com.paperweight.os.data.repository.StationRepository
import com.paperweight.os.data.repository.VaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class Phase5ValidationToneInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var outputDir: File
    private lateinit var vaultRepository: VaultRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        outputDir = File(context.cacheDir, "phase5-validation-hls").apply {
            deleteRecursively()
            mkdirs()
        }
        vaultRepository = VaultRepository(db.vaultDao())
    }

    @After
    fun tearDown() {
        db.close()
        outputDir.deleteRecursively()
        File(context.filesDir, "validation").deleteRecursively()
    }

    @Test
    fun validationToneSeedsPublicTrackAndBroadcastEnginePublishesHls() = runBlocking {
        val seeded = ValidationBroadcastSeeder(context, vaultRepository).seedValidationTone().getOrThrow()
        val tracks = vaultRepository.observeTracks().first()
        assertThat(tracks.map { it.id }).contains(ValidationBroadcastSeeder.VALIDATION_TRACK_ID)
        assertThat(seeded.visibility).isEqualTo("public")
        assertThat(File(Uri.parse(seeded.storagePath).path!!).isFile).isTrue()

        val repository = BroadcastRepository(
            vaultRepository = vaultRepository,
            scheduleRepository = ScheduleRepository(db.scheduleDao()),
            stationRepository = StationRepository(db.stationDao()),
        )
        val engine = BroadcastEngine(context, repository, SegmentStore(outputDir))

        engine.start()
        val state = engine.state.first { it.nowPlayingTitle == "Phase 5 validation tone" && it.segmentCount > 0 }

        assertThat(state.isRunning).isTrue()
        assertThat(File(outputDir, "live.m3u8").isFile).isTrue()
        val segment = outputDir.listFiles()?.firstOrNull { it.name.startsWith("segment-") }
        assertThat(segment).isNotNull()
        assertThat(segment!!.length()).isGreaterThan(AacEncoder.silentAdtsFrame().size.toLong())
    }
}
