package com.paperweight.os.server

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

/**
 * Shared HTTP Range support for serving static files (HLS segments/playlist)
 * out of NanoHTTPD routes without pulling in a full static-file framework.
 */
object RangeResponse {
    private val RANGE_HEADER = Regex("""bytes=(\d*)-(\d*)""")

    fun serveFile(
        session: NanoHTTPD.IHTTPSession,
        file: File,
        mimeType: String,
        cacheable: Boolean,
    ): NanoHTTPD.Response {
        if (!file.exists() || !file.isFile) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not found")
        }

        val fileLength = file.length()
        val match = session.headers["range"]?.let { RANGE_HEADER.matchEntire(it) }
        val response = if (match != null) {
            val (startText, endText) = match.destructured
            val start = startText.toLongOrNull() ?: 0L
            val end = (endText.toLongOrNull() ?: (fileLength - 1)).coerceAtMost(fileLength - 1)
            if (fileLength == 0L || start > end || start >= fileLength) {
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "").apply {
                    addHeader("Content-Range", "bytes */$fileLength")
                }
            }
            val length = end - start + 1
            val stream = FileInputStream(file).apply { skip(start) }
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.PARTIAL_CONTENT, mimeType, stream, length).apply {
                addHeader("Content-Range", "bytes $start-$end/$fileLength")
            }
        } else {
            NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType, FileInputStream(file), fileLength)
        }

        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader(
            "Cache-Control",
            if (cacheable) "public, max-age=31536000, immutable" else "no-cache, no-store, must-revalidate",
        )
        return response
    }
}
