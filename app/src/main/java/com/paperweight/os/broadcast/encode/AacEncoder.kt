package com.paperweight.os.broadcast

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

private const val ENCODER_TIMEOUT_US = 10_000L

data class EncodedAacFrame(
    val payload: ByteArray,
    val durationUs: Long,
)

data class EncodedAacAudio(
    val frames: List<EncodedAacFrame>,
    val sampleRate: Int,
    val channelCount: Int,
    val durationUs: Long,
) {
    fun asByteArray(): ByteArray = ByteArrayOutputStream().use { out ->
        frames.forEach { out.write(it.payload) }
        out.toByteArray()
    }
}

object AacEncoder {
    private val SILENT_AAC_LC_FRAME = byteArrayOf(
        0x21, 0x10, 0x04, 0x60, 0x8C.toByte(), 0x1C, 0x00, 0x00,
    )

    fun encode(pcmAudio: DecodedPcmAudio, bitRate: Int = DEFAULT_BIT_RATE): EncodedAacAudio {
        val sampleRate = pcmAudio.sampleRate
        val channelCount = pcmAudio.channelCount.coerceIn(1, 2)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, DEFAULT_INPUT_CHUNK_BYTES)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val info = MediaCodec.BufferInfo()
        val frames = mutableListOf<EncodedAacFrame>()
        var inputOffset = 0
        var inputDone = false
        var outputDone = false
        val frameDurationUs = AAC_SAMPLES_PER_FRAME * 1_000_000L / sampleRate.coerceAtLeast(1)
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(ENCODER_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex) ?: error("Encoder input buffer unavailable")
                        inputBuffer.clear()
                        val remaining = pcmAudio.pcm.size - inputOffset
                        if (remaining <= 0) {
                            val presentationTimeUs = pcmBytesToDurationUs(inputOffset, sampleRate, channelCount)
                            encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val size = minOf(inputBuffer.capacity(), remaining)
                            inputBuffer.put(pcmAudio.pcm, inputOffset, size)
                            val presentationTimeUs = pcmBytesToDurationUs(inputOffset, sampleRate, channelCount)
                            encoder.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
                            inputOffset += size
                        }
                    }
                }

                when (val outputIndex = encoder.dequeueOutputBuffer(info, ENCODER_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = encoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            val raw = ByteArray(info.size)
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            outputBuffer.get(raw)
                            val packetLength = raw.size + AdtsHeaderWriter.ADTS_HEADER_LENGTH
                            frames += EncodedAacFrame(
                                payload = AdtsHeaderWriter.header(packetLength, sampleRate, channelCount) + raw,
                                durationUs = frameDurationUs,
                            )
                        }
                        outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
        }
        return EncodedAacAudio(
            frames = frames.ifEmpty { listOf(silentFrame(sampleRate, channelCount)) },
            sampleRate = sampleRate,
            channelCount = channelCount,
            durationUs = pcmAudio.durationUs.takeIf { it > 0 }
                ?: pcmBytesToDurationUs(pcmAudio.pcm.size, sampleRate, channelCount),
        )
    }

    fun silentAdtsFrame(sampleRate: Int = 44_100, channelCount: Int = 2): ByteArray {
        val packetLength = 7 + SILENT_AAC_LC_FRAME.size
        return AdtsHeaderWriter.header(packetLength, sampleRate, channelCount) + SILENT_AAC_LC_FRAME
    }

    fun silentSegment(frameCount: Int = 96, sampleRate: Int = 44_100, channelCount: Int = 2): ByteArray =
        buildList {
            repeat(frameCount.coerceAtLeast(1)) { add(silentAdtsFrame(sampleRate, channelCount)) }
        }.fold(ByteArray(0)) { acc, frame -> acc + frame }

    private fun silentFrame(sampleRate: Int, channelCount: Int): EncodedAacFrame = EncodedAacFrame(
        payload = silentAdtsFrame(sampleRate, channelCount),
        durationUs = AAC_SAMPLES_PER_FRAME * 1_000_000L / sampleRate.coerceAtLeast(1),
    )

    private fun pcmBytesToDurationUs(byteCount: Int, sampleRate: Int, channelCount: Int): Long {
        val bytesPerSecond = sampleRate.toLong() * channelCount.coerceAtLeast(1) * BYTES_PER_PCM_SAMPLE
        return if (bytesPerSecond > 0) ceil(byteCount * 1_000_000.0 / bytesPerSecond).toLong() else 0L
    }

    private const val DEFAULT_BIT_RATE = 128_000
    private const val DEFAULT_INPUT_CHUNK_BYTES = 16 * 1024
    private const val AAC_SAMPLES_PER_FRAME = 1024
    private const val BYTES_PER_PCM_SAMPLE = 2
}
