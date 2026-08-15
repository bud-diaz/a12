package com.paperweight.os.broadcast

import android.content.Context
import android.net.Uri
import com.paperweight.os.data.db.entity.VaultTrackEntity
import com.paperweight.os.data.repository.VaultRepository
import com.paperweight.os.debug.DebugBuild
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class ValidationBroadcastSeeder(
    private val context: Context,
    private val vaultRepository: VaultRepository,
) {
    suspend fun seedValidationTone(): Result<VaultTrackEntity> = runCatching {
        check(DebugBuild.isDebuggable(context)) { "Validation tone is only available in debug builds." }

        val dir = File(context.filesDir, "validation").apply { mkdirs() }
        val wavFile = File(dir, VALIDATION_FILE_NAME)
        wavFile.writeBytes(generatedWav(durationSeconds = VALIDATION_DURATION_SECONDS))

        val now = System.currentTimeMillis()
        val track = VaultTrackEntity(
            id = VALIDATION_TRACK_ID,
            title = "Phase 5 validation tone",
            artist = "Paperweight OS",
            album = "Debug validation",
            sourceUri = Uri.fromFile(wavFile).toString(),
            storagePath = Uri.fromFile(wavFile).toString(),
            durationMs = VALIDATION_DURATION_SECONDS * 1_000L,
            mimeType = "audio/wav",
            visibility = "public",
            suggestedPriceCents = 0,
            minimumPriceCents = 0,
            allowFree = true,
            createdAt = now,
            updatedAt = now,
        )
        vaultRepository.upsertTrack(track)
        track
    }

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

    private fun generatedPcm(durationSeconds: Int): ByteArray {
        val sampleCount = SAMPLE_RATE * durationSeconds
        val buffer = ByteBuffer.allocate(sampleCount * CHANNEL_COUNT * BYTES_PER_SAMPLE)
            .order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { index ->
            val sample = (sin(2.0 * PI * 440.0 * index / SAMPLE_RATE) * Short.MAX_VALUE * 0.35)
                .toInt()
                .toShort()
            repeat(CHANNEL_COUNT) { buffer.putShort(sample) }
        }
        return buffer.array()
    }

    companion object {
        const val VALIDATION_TRACK_ID = "debug-phase5-validation-tone"
        private const val VALIDATION_FILE_NAME = "phase5-validation-tone.wav"
        private const val VALIDATION_DURATION_SECONDS = 12
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_COUNT = 2
        private const val BYTES_PER_SAMPLE = 2
    }
}
