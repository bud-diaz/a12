package com.paperweight.os.server.routes

import com.paperweight.os.server.RangeResponse
import fi.iki.elonen.NanoHTTPD
import java.io.File

/** Serves the live-sliding HLS playlist that [com.paperweight.os.broadcast.hls.PlaylistWriter] writes. */
class PlaylistRoute(private val hlsDir: File) {
    fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val playlist = File(hlsDir, PLAYLIST_FILE_NAME)
        // Never cacheable: it's rewritten (atomic tmp-then-rename) every rotation tick.
        return RangeResponse.serveFile(session, playlist, MIME_TYPE, cacheable = false)
    }

    private companion object {
        const val PLAYLIST_FILE_NAME = "live.m3u8"
        const val MIME_TYPE = "application/vnd.apple.mpegurl"
    }
}
