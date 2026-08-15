package com.paperweight.os.broadcast.mic

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.net.Uri
import com.paperweight.os.broadcast.DecodedPcmAudio
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Blocking AudioRecord wrapper for Phase 6 mic go-live.
 *
 * Captures short mono PCM windows from the camcorder-oriented microphone source,
 * applies device AGC when available, noise-gates only near-digital-silence input, then peak
 * normalizes the result before handing it to the existing AAC/HLS pipeline. The
 * Samsung A12 can report a valid MIC recording session while returning extremely
 * low-amplitude PCM; without this conditioning the listener stream cuts over but
 * sounds like dead air.
 */
open class MicCapture(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) {
    open fun capturePcmSegment(durationMs: Long = DEFAULT_CAPTURE_MS): DecodedPcmAudio {
        val channelMask = AudioFormat.CHANNEL_IN_MONO
        val channelCount = CHANNEL_COUNT
        val bytesPerSample = BYTES_PER_SAMPLE
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        require(minBuffer > 0) { "Unsupported microphone format: ${sampleRate}Hz mono PCM16 (minBuffer=$minBuffer)" }
        val targetBytes = ((sampleRate * durationMs / 1_000L) * channelCount * bytesPerSample)
            .toInt()
            .coerceAtLeast(minBuffer)
        val capture = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER,
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer.coerceAtLeast(sampleRate / 2 * channelCount * bytesPerSample),
        )
        require(capture.state == AudioRecord.STATE_INITIALIZED) { "Microphone AudioRecord failed to initialize" }

        val agc = if (AutomaticGainControl.isAvailable()) {
            runCatching { AutomaticGainControl.create(capture.audioSessionId)?.apply { enabled = true } }.getOrNull()
        } else {
            null
        }
        val pcm = ByteArray(targetBytes)
        var offset = 0
        try {
            capture.startRecording()
            val scratch = ByteArray(minBuffer)
            while (offset < pcm.size && capture.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = capture.read(scratch, 0, minOf(scratch.size, pcm.size - offset))
                if (read <= 0) break
                scratch.copyInto(pcm, destinationOffset = offset, startIndex = 0, endIndex = read)
                offset += read
            }
        } finally {
            runCatching { capture.stop() }
            agc?.release()
            capture.release()
        }
        if (offset <= 0) error("Microphone capture produced no PCM bytes")
        val captured = pcm.copyOf(offset)
        val conditioned = normalizePcm16(captured)
        return DecodedPcmAudio(
            sourceUri = MIC_SOURCE_URI,
            pcm = conditioned,
            sampleRate = sampleRate,
            channelCount = channelCount,
            durationUs = conditioned.size.toLong() * 1_000_000L / (sampleRate * channelCount * bytesPerSample).coerceAtLeast(1),
            mimeType = "audio/raw",
        )
    }

    private fun normalizePcm16(bytes: ByteArray): ByteArray {
        val peak = peakAbs(bytes)
        if (peak < MIN_SIGNAL_PEAK) return ByteArray(bytes.size)
        val gain = (TARGET_PEAK.toDouble() / peak).coerceIn(1.0, MAX_GAIN)
        if (gain <= 1.0) return bytes
        val out = bytes.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val lo = out[i].toInt() and 0xff
            val hi = out[i + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toInt()
            val scaled = (sample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = (scaled and 0xff).toByte()
            out[i + 1] = ((scaled ushr 8) and 0xff).toByte()
            i += 2
        }
        return out
    }

    private fun peakAbs(bytes: ByteArray): Int {
        var peak = 0
        var i = 0
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xff
            val hi = bytes[i + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toInt()
            val absSample = abs(sample)
            if (absSample > peak) peak = absSample
            i += 2
        }
        return peak
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val CHANNEL_COUNT = 1
        const val DEFAULT_CAPTURE_MS = 1_500L
        private const val BYTES_PER_SAMPLE = 2
        private const val MIN_SIGNAL_PEAK = 24
        private const val TARGET_PEAK = 16_000
        private const val MAX_GAIN = 48.0
        val MIC_SOURCE_URI: Uri = Uri.parse("paperweight://mic/live")
    }
}
