package com.paperweight.os.broadcast

import java.io.File

class SegmentWriter(private val outputDir: File) {
    init { outputDir.mkdirs() }

    fun writeSegment(sequence: Long, payload: ByteArray): HlsSegment {
        val fileName = "segment-$sequence.aac"
        File(outputDir, fileName).writeBytes(payload)
        return HlsSegment(sequence = sequence, fileName = fileName, durationSeconds = DEFAULT_SEGMENT_SECONDS.toDouble())
    }

    companion object {
        const val DEFAULT_SEGMENT_SECONDS = 6
    }
}
