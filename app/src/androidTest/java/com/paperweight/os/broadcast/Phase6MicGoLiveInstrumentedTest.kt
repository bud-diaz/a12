package com.paperweight.os.broadcast

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.paperweight.os.broadcast.mic.MicCapture
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.db.entity.VaultTrackEntity
import com.paperweight.os.data.repository.BroadcastRepository
import com.paperweight.os.data.repository.ScheduleRepository
import com.paperweight.os.data.repository.StationRepository
import com.paperweight.os.data.repository.VaultRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class Phase6MicGoLiveInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        outputDir = File(context.cacheDir, "phase6-hls").apply {
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
    fun manifestDeclaresMicPermissionsAndBroadcastServiceMicType() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertThat(packageInfo.requestedPermissions?.toList()).contains(android.Manifest.permission.RECORD_AUDIO)
        assertThat(packageInfo.requestedPermissions?.toList()).contains(android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
    }

    @Test
    fun goLivePublishesMicPcmThroughHlsAndStopReturnsToRotationState() = runBlocking {
        val repository = BroadcastRepository(
            vaultRepository = VaultRepository(db.vaultDao()),
            scheduleRepository = ScheduleRepository(db.scheduleDao()),
            stationRepository = StationRepository(db.stationDao()),
        )
        val engine = BroadcastEngine(
            context = context,
            repository = repository,
            segmentStore = SegmentStore(outputDir),
            micCapture = FakeMicCapture(),
        )

        engine.start()
        engine.goLive()
        val liveState = engine.state.first { it.isMicLive && it.segmentCount > 0 }

        assertThat(liveState.isRunning).isTrue()
        assertThat(liveState.mode).isEqualTo("live_mic")
        assertThat(liveState.nowPlayingTitle).isEqualTo("Live from the A12 mic")
        assertThat(File(outputDir, "live.m3u8").readText()).contains("segment-")
        val segment = outputDir.listFiles()?.firstOrNull { it.name.startsWith("segment-") }
        assertThat(segment).isNotNull()
        assertThat(segment!!.length()).isGreaterThan(AacEncoder.silentAdtsFrame().size.toLong())

        engine.stopLive()
        val stoppedState = engine.state.first { !it.isMicLive }
        assertThat(stoppedState.isMicLive).isFalse()
    }

    @Test
    fun idleBroadcastStillPublishesSilentPlaylistSoListenerPageCanLoad() = runBlocking {
        val repository = BroadcastRepository(
            vaultRepository = VaultRepository(db.vaultDao()),
            scheduleRepository = ScheduleRepository(db.scheduleDao()),
            stationRepository = StationRepository(db.stationDao()),
        )
        val engine = BroadcastEngine(
            context = context,
            repository = repository,
            segmentStore = SegmentStore(outputDir),
            micCapture = FakeMicCapture(),
        )

        engine.start()
        val idleState = engine.state.first { it.isRunning && it.segmentCount > 0 }
        val playlist = File(outputDir, "live.m3u8")

        assertThat(idleState.actionMessage).contains("no public vault tracks")
        assertThat(playlist.isFile).isTrue()
        assertThat(playlist.readText()).contains("segment-0.aac")
        assertThat(File(outputDir, "segment-0.aac").isFile).isTrue()
    }

    @Test
    fun goLiveAndStopLiveKeepMonotonicPlaylistSoListenersDoNotNeedRefresh() = runBlocking {
        val repository = BroadcastRepository(
            vaultRepository = VaultRepository(db.vaultDao()),
            scheduleRepository = ScheduleRepository(db.scheduleDao()),
            stationRepository = StationRepository(db.stationDao()),
        )
        val wavFile = File(context.cacheDir, "phase6-return-tone.wav").apply { writeBytes(generatedWav(durationSeconds = 2)) }
        repository.vaultRepository.upsertTrack(publicTrack(storagePath = Uri.fromFile(wavFile).toString()))
        val engine = BroadcastEngine(
            context = context,
            repository = repository,
            segmentStore = SegmentStore(outputDir),
            micCapture = FakeMicCapture(),
        )

        engine.start()
        val rotationState = engine.state.first { it.nowPlayingTitle == "Return Tone" && it.segmentCount > 0 }
        val rotationMaxSequence = maxSegmentSequence(File(outputDir, "live.m3u8").readText())
        assertThat(rotationState.isMicLive).isFalse()

        engine.goLive()
        val livePlaylist = waitForPlaylist { playlist ->
            maxSegmentSequence(playlist) > rotationMaxSequence
        }
        val liveMaxSequence = maxSegmentSequence(livePlaylist)
        assertThat(liveMaxSequence).isGreaterThan(rotationMaxSequence)

        engine.stopLive()
        val resumedPlaylist = waitForPlaylist { playlist ->
            maxSegmentSequence(playlist) > liveMaxSequence
        }
        val resumedMaxSequence = maxSegmentSequence(resumedPlaylist)
        assertThat(resumedMaxSequence).isGreaterThan(liveMaxSequence)
    }

    private suspend fun waitForPlaylist(predicate: (String) -> Boolean): String {
        val playlist = File(outputDir, "live.m3u8")
        var latest = playlist.readText()
        withTimeout(10_000L) {
            while (!predicate(latest)) {
                delay(100L)
                latest = playlist.readText()
            }
        }
        return latest
    }

    private class FakeMicCapture : MicCapture() {
        override fun capturePcmSegment(durationMs: Long): DecodedPcmAudio = DecodedPcmAudio(
            sourceUri = Uri.parse("paperweight://test/mic"),
            pcm = generatedPcm(durationMs = 1_000L),
            sampleRate = SAMPLE_RATE,
            channelCount = CHANNEL_COUNT,
            durationUs = 1_000_000L,
            mimeType = "audio/raw",
        )
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_COUNT = 2
        const val BYTES_PER_SAMPLE = 2

        fun maxSegmentSequence(playlist: String): Long = playlist
            .lineSequence()
            .filter { it.startsWith("segment-") && it.endsWith(".aac") }
            .map { it.removePrefix("segment-").removeSuffix(".aac").toLong() }
            .max()

        fun publicTrack(storagePath: String): VaultTrackEntity = VaultTrackEntity(
            id = "phase6-return-tone",
            title = "Return Tone",
            artist = "Paperweight",
            album = "Phase 6",
            sourceUri = storagePath,
            storagePath = storagePath,
            durationMs = 2_000,
            mimeType = "audio/wav",
            visibility = "public",
            createdAt = 1,
            updatedAt = 2,
        )

        fun generatedWav(durationSeconds: Int): ByteArray {
            val pcm = generatedPcm(durationMs = durationSeconds * 1_000L)
            val out = ByteArrayOutputStream()
            fun writeAscii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
            fun writeInt(value: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
            fun writeShort(value: Int) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
            writeAscii("RIFF")
            writeInt(36 + pcm.size)
            writeAscii("WAVE")
            writeAscii("fmt ")
            writeInt(16)
            writeShort(1)
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

        fun generatedPcm(durationMs: Long): ByteArray {
            val sampleCount = (SAMPLE_RATE * durationMs / 1_000L).toInt()
            val buffer = ByteBuffer.allocate(sampleCount * CHANNEL_COUNT * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
            repeat(sampleCount) { index ->
                val sample = (sin(2.0 * PI * 660.0 * index / SAMPLE_RATE) * Short.MAX_VALUE * 0.30).toInt().toShort()
                repeat(CHANNEL_COUNT) { buffer.putShort(sample) }
            }
            return buffer.array()
        }
    }
}
