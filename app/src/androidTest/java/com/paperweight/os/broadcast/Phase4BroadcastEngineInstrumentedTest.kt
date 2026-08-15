package com.paperweight.os.broadcast

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.db.entity.VaultTrackEntity
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

class Phase4BroadcastEngineInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        outputDir = File(context.cacheDir, "phase4-hls").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        db.close()
        outputDir.deleteRecursively()
    }

    @Test
    fun adtsHeaderUsesAacLc441StereoAndPacketLength() {
        val header = AdtsHeaderWriter.header(packetLength = 107, sampleRate = 44_100, channelCount = 2)

        assertThat(header).hasLength(7)
        assertThat(header[0]).isEqualTo(0xFF.toByte())
        assertThat(header[1].toInt() and 0xF0).isEqualTo(0xF0)
        assertThat(AdtsHeaderWriter.packetLength(header)).isEqualTo(107)
    }

    @Test
    fun playlistWriterCreatesPackedAudioLivePlaylist() {
        PlaylistWriter.writeLivePlaylist(
            outputDir = outputDir,
            segments = listOf(HlsSegment(sequence = 7, fileName = "segment-7.aac", durationSeconds = 6.0)),
            mediaSequence = 7,
            targetDurationSeconds = 6,
        )

        val playlist = File(outputDir, "live.m3u8").readText()
        assertThat(playlist).contains("#EXTM3U")
        assertThat(playlist).contains("#EXT-X-TARGETDURATION:6")
        assertThat(playlist).contains("#EXT-X-MEDIA-SEQUENCE:7")
        assertThat(playlist).contains("#EXT-X-MAP")
        assertThat(playlist).contains("#EXTINF:6.000,")
        assertThat(playlist).contains("segment-7.aac")
    }

    @Test
    fun broadcastEnginePublishesPublicTrackAsNowPlayingAndWritesInitialHlsFiles() = runBlocking {
        val repository = BroadcastRepository(
            vaultRepository = VaultRepository(db.vaultDao()),
            scheduleRepository = ScheduleRepository(db.scheduleDao()),
            stationRepository = StationRepository(db.stationDao()),
        )
        repository.vaultRepository.upsertTrack(track(id = "track-1", title = "First Public", visibility = "public"))
        repository.vaultRepository.upsertTrack(track(id = "track-2", title = "Private Cut", visibility = "private"))
        val engine = BroadcastEngine(context, repository, SegmentStore(outputDir))

        engine.start()
        val state = engine.state.first { it.nowPlayingTitle == "First Public" }

        assertThat(state.isRunning).isTrue()
        assertThat(state.nowPlayingTitle).isEqualTo("First Public")
        assertThat(state.queue.map { it.title }).containsExactly("First Public")
        assertThat(File(outputDir, "live.m3u8").isFile).isTrue()
        assertThat(outputDir.listFiles()?.map { it.name }).contains("segment-0.aac")
    }

    private fun track(id: String, title: String, visibility: String): VaultTrackEntity = VaultTrackEntity(
        id = id,
        title = title,
        artist = "Paperweight",
        album = "Phase 4",
        sourceUri = "content://source/$id.wav",
        storagePath = "content://vault/$id.wav",
        durationMs = 60_000,
        mimeType = "audio/wav",
        visibility = visibility,
        suggestedPriceCents = 0,
        minimumPriceCents = 0,
        allowFree = true,
        createdAt = 1,
        updatedAt = 2,
    )
}
