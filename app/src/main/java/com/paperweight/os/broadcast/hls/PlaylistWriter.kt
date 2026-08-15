package com.paperweight.os.broadcast

import java.io.File

data class HlsSegment(
    val sequence: Long,
    val fileName: String,
    val durationSeconds: Double,
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
            // Packed-audio HLS doesn't use a TS container; this tag marks the
            // stream as explicitly header-framed for clients that inspect maps.
            appendLine("#EXT-X-MAP:URI=\"init.aac\"")
            segments.forEach { segment ->
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
