package com.paperweight.os.broadcast

import java.io.File

data class HlsSegment(
    val sequence: Long,
    val fileName: String,
    val durationSeconds: Double,
    val discontinuity: Boolean = false,
)

object PlaylistWriter {
    const val LIVE_PLAYLIST = "live.m3u8"

    fun writeLivePlaylist(
        outputDir: File,
        segments: List<HlsSegment>,
        mediaSequence: Long,
        targetDurationSeconds: Int,
    ): File {
        outputDir.mkdirs()
        val temp = File(outputDir, "$LIVE_PLAYLIST.tmp")
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:7")
            appendLine("#EXT-X-TARGETDURATION:$targetDurationSeconds")
            appendLine("#EXT-X-MEDIA-SEQUENCE:$mediaSequence")
            segments.forEach { segment ->
                if (segment.discontinuity) appendLine("#EXT-X-DISCONTINUITY")
                appendLine("#EXTINF:${"%.3f".format(segment.durationSeconds)},")
                appendLine(segment.fileName)
            }
        }
        temp.writeText(playlist)
        val target = File(outputDir, LIVE_PLAYLIST)
        if (!temp.renameTo(target)) {
            target.writeText(playlist)
            temp.delete()
        }
        return target
    }
}
