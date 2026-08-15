package com.paperweight.os.broadcast

import java.io.File

class SegmentStore(private val outputDir: File) {
    init { outputDir.mkdirs() }

    fun writeInitialSilentWindow(windowSize: Int = 3, targetDurationSeconds: Int = 6): List<HlsSegment> {
        File(outputDir, "init.aac").writeBytes(AacEncoder.silentAdtsFrame())
        val segments = (0 until windowSize.coerceAtLeast(1)).map { index ->
            val fileName = "segment-$index.aac"
            File(outputDir, fileName).writeBytes(AacEncoder.silentSegment())
            HlsSegment(sequence = index.toLong(), fileName = fileName, durationSeconds = targetDurationSeconds.toDouble())
        }
        PlaylistWriter.writeLivePlaylist(
            outputDir = outputDir,
            segments = segments,
            mediaSequence = segments.first().sequence,
            targetDurationSeconds = targetDurationSeconds,
        )
        return segments
    }

    fun playlistFile(): File = File(outputDir, PlaylistWriter.LIVE_PLAYLIST)
}
