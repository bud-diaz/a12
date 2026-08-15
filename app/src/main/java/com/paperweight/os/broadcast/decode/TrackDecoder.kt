package com.paperweight.os.broadcast

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

private const val TIMEOUT_US = 10_000L

data class DecodedTrackInfo(
    val sourceUri: Uri,
    val durationUs: Long,
    val mimeType: String?,
)

data class DecodedPcmAudio(
    val sourceUri: Uri,
    val pcm: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val durationUs: Long,
    val mimeType: String?,
)

class TrackDecoder(private val context: Context) {
    fun inspect(sourceUri: Uri): DecodedTrackInfo {
        val extractor = MediaExtractor()
        return try {
            context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
                val format = extractor.firstAudioFormat()
                DecodedTrackInfo(
                    sourceUri = sourceUri,
                    durationUs = format.durationUsOrZero(),
                    mimeType = format.getString(MediaFormat.KEY_MIME),
                )
            } ?: error("Unable to open $sourceUri")
        } finally {
            extractor.release()
        }
    }

    fun decodeToPcm(sourceUri: Uri): DecodedPcmAudio {
        val extractor = MediaExtractor()
        return try {
            context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
                val trackIndex = extractor.firstAudioTrackIndex()
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Audio track has no MIME type")
                extractor.selectTrack(trackIndex)
                if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
                    decodeRawPcm(sourceUri, extractor, format, mime)
                } else {
                    decodeWithCodec(sourceUri, extractor, format, mime)
                }
            } ?: error("Unable to open $sourceUri")
        } finally {
            extractor.release()
        }
    }

    private fun decodeRawPcm(
        sourceUri: Uri,
        extractor: MediaExtractor,
        format: MediaFormat,
        mime: String,
    ): DecodedPcmAudio {
        val pcm = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(DEFAULT_INPUT_BUFFER_SIZE)
        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val bytes = ByteArray(sampleSize)
            buffer.position(0)
            buffer.get(bytes)
            pcm.write(bytes)
            extractor.advance()
        }
        val sampleRate = format.sampleRateOrDefault()
        val channelCount = format.channelCountOrDefault()
        return DecodedPcmAudio(
            sourceUri = sourceUri,
            pcm = pcm.toByteArray(),
            sampleRate = sampleRate,
            channelCount = channelCount,
            durationUs = format.durationUsOrEstimate(pcm.size(), sampleRate, channelCount),
            mimeType = mime,
        )
    }

    private fun decodeWithCodec(
        sourceUri: Uri,
        extractor: MediaExtractor,
        format: MediaFormat,
        mime: String,
    ): DecodedPcmAudio {
        val decoder = MediaCodec.createDecoderByType(mime)
        val output = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var outputFormat = format
        var inputDone = false
        var outputDone = false
        try {
            decoder.configure(format, null, null, 0)
            decoder.start()
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex) ?: error("Decoder input buffer unavailable")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = decoder.outputFormat
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && info.size > 0) {
                            val bytes = ByteArray(info.size)
                            outputBuffer.position(info.offset)
                            outputBuffer.limit(info.offset + info.size)
                            outputBuffer.get(bytes)
                            output.write(bytes)
                        }
                        outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            runCatching { decoder.stop() }
            decoder.release()
        }
        val sampleRate = outputFormat.sampleRateOrDefault()
        val channelCount = outputFormat.channelCountOrDefault()
        return DecodedPcmAudio(
            sourceUri = sourceUri,
            pcm = output.toByteArray(),
            sampleRate = sampleRate,
            channelCount = channelCount,
            durationUs = format.durationUsOrEstimate(output.size(), sampleRate, channelCount),
            mimeType = mime,
        )
    }

    private fun MediaExtractor.firstAudioTrackIndex(): Int = (0 until trackCount)
        .firstOrNull { getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        ?: error("No audio track found")

    private fun MediaExtractor.firstAudioFormat(): MediaFormat = getTrackFormat(firstAudioTrackIndex())

    private fun MediaFormat.durationUsOrZero(): Long = if (containsKey(MediaFormat.KEY_DURATION)) {
        getLong(MediaFormat.KEY_DURATION)
    } else {
        0L
    }

    private fun MediaFormat.durationUsOrEstimate(pcmBytes: Int, sampleRate: Int, channelCount: Int): Long {
        if (containsKey(MediaFormat.KEY_DURATION)) return getLong(MediaFormat.KEY_DURATION)
        val bytesPerSecond = sampleRate.toLong() * channelCount.coerceAtLeast(1) * BYTES_PER_PCM_SAMPLE
        return if (bytesPerSecond > 0) pcmBytes.toLong() * 1_000_000L / bytesPerSecond else 0L
    }

    private fun MediaFormat.sampleRateOrDefault(): Int = if (containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
        getInteger(MediaFormat.KEY_SAMPLE_RATE)
    } else {
        DEFAULT_SAMPLE_RATE
    }

    private fun MediaFormat.channelCountOrDefault(): Int = if (containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
        getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
    } else {
        DEFAULT_CHANNEL_COUNT
    }

    private companion object {
        const val DEFAULT_INPUT_BUFFER_SIZE = 256 * 1024
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val DEFAULT_CHANNEL_COUNT = 2
        const val BYTES_PER_PCM_SAMPLE = 2
    }
}
