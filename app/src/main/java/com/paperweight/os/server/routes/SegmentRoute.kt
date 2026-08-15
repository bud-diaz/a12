package com.paperweight.os.server.routes

import com.paperweight.os.server.RangeResponse
import fi.iki.elonen.NanoHTTPD
import java.io.File

/**
 * Serves the packed-audio ADTS segments [com.paperweight.os.broadcast.SegmentStore] writes
 * (`init.aac`, `segment-<n>.aac`). [fileName] comes straight from the request URI, so it's
 * validated against an exact allow-list pattern before touching the filesystem — this is the
 * only thing standing between an inbound LAN request and path traversal into [hlsDir].
 */
class SegmentRoute(private val hlsDir: File) {
    fun serve(session: NanoHTTPD.IHTTPSession, fileName: String): NanoHTTPD.Response {
        if (!SEGMENT_NAME_PATTERN.matches(fileName)) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.FORBIDDEN, "text/plain", "Invalid segment name")
        }
        val file = File(hlsDir, fileName)
        // Segments are immutable once written (SegmentStore only ever appends new sequence
        // numbers, never rewrites an existing one), so these are safely cacheable.
        return RangeResponse.serveFile(session, file, MIME_TYPE, cacheable = true)
    }

    private companion object {
        val SEGMENT_NAME_PATTERN = Regex("""(init|segment-\d+)\.aac""")
        const val MIME_TYPE = "audio/aac"
    }
}
