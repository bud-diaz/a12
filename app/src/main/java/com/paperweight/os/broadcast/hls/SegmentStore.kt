package com.paperweight.os.broadcast

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ceil

class SegmentStore(private val outputDir: File) {
    init { outputDir.mkdirs() }

    fun writeInitialSilentWindow(windowSize: Int = 3, targetDurationSeconds: Int = 6): List<HlsSegment> {
        clearHlsFiles()
        File(outputDir, "init.aac").writeBytes(AacEncoder.silentAdtsFrame())
        val segments = (0 until windowSize.coerceAtLeast(1)).map { index ->
            val fileName = "segment-$index.aac"
            File(outputDir, fileName).writeBytes(AacEncoder.silentSegment())
            HlsSegment(sequence = index.toLong(), fileName = fileName, durationSeconds = targetDurationSeconds.toDouble())
        }
        publishLiveWindow(segments = segments, currentIndex = segments.lastIndex, targetDurationSeconds = targetDurationSeconds)
        return segments
    }

    fun writeEncodedSegments(
        encodedAudio: EncodedAacAudio,
        startSequence: Long = 0,
        targetDurationSeconds: Int = SegmentWriter.DEFAULT_SEGMENT_SECONDS,
    ): List<HlsSegment> {
        clearHlsFiles()
        File(outputDir, "init.aac").writeBytes(ByteArray(0))
        val segments = mutableListOf<HlsSegment>()
        var sequence = startSequence
        var currentDurationUs = 0L
        var segmentBytes = ByteArrayOutputStream()

        fun flushSegment() {
            if (segmentBytes.size() == 0) return
            val fileName = "segment-$sequence.aac"
            File(outputDir, fileName).writeBytes(segmentBytes.toByteArray())
            segments += HlsSegment(
                sequence = sequence,
                fileName = fileName,
                durationSeconds = currentDurationUs / 1_000_000.0,
            )
            sequence += 1
            currentDurationUs = 0L
            segmentBytes = ByteArrayOutputStream()
        }

        encodedAudio.frames.forEach { frame ->
            segmentBytes.write(frame.payload)
            currentDurationUs += frame.durationUs
            if (currentDurationUs >= targetDurationSeconds * 1_000_000L) {
                flushSegment()
            }
        }
        flushSegment()

        val written = segments.ifEmpty {
            val fileName = "segment-$startSequence.aac"
            File(outputDir, fileName).writeBytes(encodedAudio.asByteArray())
            listOf(
                HlsSegment(
                    sequence = startSequence,
                    fileName = fileName,
                    durationSeconds = ceil(encodedAudio.durationUs / 1000.0) / 1000.0,
                ),
            )
        }
        publishLiveWindow(written, currentIndex = 0, targetDurationSeconds = targetDurationSeconds)
        return written
    }

    fun writeEncodedWindow(
        encodedAudio: EncodedAacAudio,
        startSequence: Long = 0,
        targetDurationSeconds: Int = SegmentWriter.DEFAULT_SEGMENT_SECONDS,
        windowSize: Int = DEFAULT_WINDOW_SIZE,
    ): List<HlsSegment> {
        val segments = writeEncodedSegments(encodedAudio, startSequence, targetDurationSeconds)
        return publishLiveWindow(segments, currentIndex = segments.lastIndex, targetDurationSeconds, windowSize)
    }

    fun publishLiveWindow(
        segments: List<HlsSegment>,
        currentIndex: Int,
        targetDurationSeconds: Int = SegmentWriter.DEFAULT_SEGMENT_SECONDS,
        windowSize: Int = DEFAULT_WINDOW_SIZE,
    ): List<HlsSegment> {
        if (segments.isEmpty()) return emptyList()
        val safeCurrent = currentIndex.coerceIn(0, segments.lastIndex)
        val first = (safeCurrent - windowSize.coerceAtLeast(1) + 1).coerceAtLeast(0)
        val liveWindow = segments.subList(first, safeCurrent + 1)
        PlaylistWriter.writeLivePlaylist(
            outputDir = outputDir,
            segments = liveWindow,
            mediaSequence = liveWindow.first().sequence,
            targetDurationSeconds = targetDurationSeconds,
        )
        return liveWindow
    }

    fun playlistFile(): File = File(outputDir, PlaylistWriter.LIVE_PLAYLIST)

    private fun clearHlsFiles() {
        outputDir.mkdirs()
        outputDir.listFiles()
            ?.filter { it.name == PlaylistWriter.LIVE_PLAYLIST || it.name == "${PlaylistWriter.LIVE_PLAYLIST}.tmp" || it.name == "init.aac" || it.name.startsWith("segment-") }
            ?.forEach { it.delete() }
    }

    private companion object {
        const val DEFAULT_WINDOW_SIZE = 5
    }
}
