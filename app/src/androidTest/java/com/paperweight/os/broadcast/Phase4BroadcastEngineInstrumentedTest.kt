package com.paperweight.os.broadcast

import android.content.Context
import android.net.Uri
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class Phase4BroadcastEngineInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var outputDir: File
    private lateinit var fixtureDir: File

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
        fixtureDir = File(context.cacheDir, "phase4-fixtures").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        db.close()
        outputDir.deleteRecursively()
        fixtureDir.deleteRecursively()
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
    fun aacEncoderEncodesNonSilentPcmIntoAdtsFrames() {
        val pcm = generatedPcm(durationSeconds = 1)
        val encoded = AacEncoder.encode(
            DecodedPcmAudio(
                sourceUri = Uri.EMPTY,
                pcm = pcm,
                sampleRate = SAMPLE_RATE,
                channelCount = CHANNEL_COUNT,
                durationUs = 1_000_000,
                mimeType = "audio/raw",
            ),
        )

        assertThat(encoded.frames).isNotEmpty()
        val first = encoded.frames.first().payload
        assertThat(first[0]).isEqualTo(0xFF.toByte())
        assertThat(first[1].toInt() and 0xF0).isEqualTo(0xF0)
        assertThat(encoded.asByteArray()).isNotEqualTo(AacEncoder.silentSegment())
    }

    @Test
    fun segmentStoreWritesRealEncodedAudioWindow() {
        val encoded = AacEncoder.encode(
            DecodedPcmAudio(
                sourceUri = Uri.EMPTY,
                pcm = generatedPcm(durationSeconds = 2),
                sampleRate = SAMPLE_RATE,
                channelCount = CHANNEL_COUNT,
                durationUs = 2_000_000,
                mimeType = "audio/raw",
            ),
        )

        val segments = SegmentStore(outputDir).writeEncodedWindow(encoded, targetDurationSeconds = 1, windowSize = 5)

        assertThat(segments).isNotEmpty()
        val segmentBytes = File(outputDir, segments.first().fileName).readBytes()
        assertThat(segmentBytes.size).isGreaterThan(AacEncoder.silentAdtsFrame().size)
        assertThat(File(outputDir, "live.m3u8").readText()).contains(segments.first().fileName)
    }

    @Test
    fun broadcastEnginePublishesPublicTrackAndWritesRealTrackAudioSegments() = runBlocking {
        val repository = BroadcastRepository(
            vaultRepository = VaultRepository(db.vaultDao()),
            scheduleRepository = ScheduleRepository(db.scheduleDao()),
            stationRepository = StationRepository(db.stationDao()),
        )
        val wavFile = File(fixtureDir, "phase4-tone.wav").apply { writeBytes(generatedWav(durationSeconds = 2)) }
        repository.vaultRepository.upsertTrack(
            track(
                id = "track-1",
                title = "First Public",
                visibility = "public",
                storagePath = Uri.fromFile(wavFile).toString(),
            ),
        )
        repository.vaultRepository.upsertTrack(track(id = "track-2", title = "Private Cut", visibility = "private"))
        val engine = BroadcastEngine(context, repository, SegmentStore(outputDir))

        engine.start()
        val state = engine.state.first { it.nowPlayingTitle == "First Public" && it.segmentCount > 0 }

        assertThat(state.isRunning).isTrue()
        assertThat(state.nowPlayingTitle).isEqualTo("First Public")
        assertThat(state.queue.map { it.title }).containsExactly("First Public")
        assertThat(state.actionMessage).contains("real audio segment")
        assertThat(File(outputDir, "live.m3u8").isFile).isTrue()
        val segment = outputDir.listFiles()?.firstOrNull { it.name.startsWith("segment-") }
        assertThat(segment).isNotNull()
        assertThat(segment!!.length()).isGreaterThan(AacEncoder.silentAdtsFrame().size.toLong())
    }

    private fun track(
        id: String,
        title: String,
        visibility: String,
        storagePath: String = "content://vault/$id.wav",
    ): VaultTrackEntity = VaultTrackEntity(
        id = id,
        title = title,
        artist = "Paperweight",
        album = "Phase 4",
        sourceUri = storagePath,
        storagePath = storagePath,
        durationMs = 2_000,
        mimeType = "audio/wav",
        visibility = visibility,
        suggestedPriceCents = 0,
        minimumPriceCents = 0,
        allowFree = true,
        createdAt = 1,
        updatedAt = 2,
    )

    private fun generatedWav(durationSeconds: Int): ByteArray {
        val pcm = generatedPcm(durationSeconds)
        val out = ByteArrayOutputStream()
        fun writeAscii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        fun writeInt(value: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        fun writeShort(value: Int) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
        writeAscii("RIFF")
        writeInt(36 + pcm.size)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeInt(16)
        writeShort(1) // PCM
        writeShort(CHANNEL_COUNT)
        writeInt(SAMPLE_RATE)
        writeInt(SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE)
        writeShort(CHANNEL_COUNT * BYTES_PER_SAMPLE)
        writeShort(16)
        writeAscii("data")
        writeInt(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }

    private fun generatedPcm(durationSeconds: Int): ByteArray {
        val sampleCount = SAMPLE_RATE * durationSeconds
        val buffer = ByteBuffer.allocate(sampleCount * CHANNEL_COUNT * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { index ->
            val sample = (sin(2.0 * PI * 440.0 * index / SAMPLE_RATE) * Short.MAX_VALUE * 0.35).toInt().toShort()
            repeat(CHANNEL_COUNT) { buffer.putShort(sample) }
        }
        return buffer.array()
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_COUNT = 2
        const val BYTES_PER_SAMPLE = 2
    }
}
