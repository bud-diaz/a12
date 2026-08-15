package com.paperweight.os.broadcast

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.google.common.truth.Truth.assertThat
import com.paperweight.os.broadcast.mic.MicCapture
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class Phase6RealMicSignalInstrumentedTest {
    @Test
    fun micCaptureConditioningProducesAudiblePcmLevelFromSpeakerTone() {
        val player = thread(start = true) { playSpeakerTone(durationMs = 2_000, amplitude = 0.8) }
        Thread.sleep(250)
        val pcm = MicCapture().capturePcmSegment(durationMs = 1_500)
        player.join(3_000)
        val metrics = pcmMetrics(pcm.pcm)
        println("PHASE6_MIC_CONDITIONED bytes=${pcm.pcm.size} sampleRate=${pcm.sampleRate} channels=${pcm.channelCount} maxAbs=${metrics.maxAbs} rms=${metrics.rms}")
        assertThat(pcm.channelCount).isEqualTo(1)
        assertThat(metrics.maxAbs).isGreaterThan(1_000)
        assertThat(metrics.rms).isGreaterThan(50.0)
    }

    @Test
    fun micCaptureDoesNotGateQuieterSpeechLikeSignalToDeadAir() {
        val player = thread(start = true) { playSpeakerTone(durationMs = 2_000, amplitude = 0.15) }
        Thread.sleep(250)
        val pcm = MicCapture().capturePcmSegment(durationMs = 1_500)
        player.join(3_000)
        val metrics = pcmMetrics(pcm.pcm)
        println("PHASE6_MIC_QUIET_CONDITIONED bytes=${pcm.pcm.size} sampleRate=${pcm.sampleRate} channels=${pcm.channelCount} maxAbs=${metrics.maxAbs} rms=${metrics.rms}")
        assertThat(metrics.maxAbs).isGreaterThan(500)
        assertThat(metrics.rms).isGreaterThan(20.0)
    }

    private fun playSpeakerTone(durationMs: Int, amplitude: Double) {
        val sampleRate = 44_100
        val samples = sampleRate * durationMs / 1_000
        val pcm = ShortArray(samples)
        repeat(samples) { index ->
            pcm[index] = (sin(2.0 * PI * 880.0 * index / sampleRate) * Short.MAX_VALUE * amplitude).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            track.play()
            track.write(pcm, 0, pcm.size)
            Thread.sleep(durationMs.toLong())
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private data class PcmMetrics(val maxAbs: Int, val rms: Double)

    private fun pcmMetrics(bytes: ByteArray): PcmMetrics {
        var maxAbs = 0
        var sumSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xff
            val hi = bytes[i + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toInt()
            val abs = kotlin.math.abs(sample)
            if (abs > maxAbs) maxAbs = abs
            sumSquares += sample.toDouble() * sample.toDouble()
            samples += 1
            i += 2
        }
        return PcmMetrics(maxAbs = maxAbs, rms = if (samples == 0) 0.0 else sqrt(sumSquares / samples))
    }
}
