package com.paperweight.os.server

import android.content.Context
import com.paperweight.os.broadcast.BroadcastEngine
import com.paperweight.os.data.prefs.AppPreferences
import com.paperweight.os.server.routes.ListenerWebRoute
import com.paperweight.os.server.routes.PlaylistRoute
import com.paperweight.os.server.routes.SegmentRoute
import com.paperweight.os.server.routes.StatusRoute
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Embedded LAN HTTP server: serves the live HLS playlist/segments Phase 4's
 * [BroadcastEngine] writes, a small JSON status endpoint, and the bundled
 * static listener player page. Binds `0.0.0.0:<port>` (NanoHTTPD's default
 * for a hostname-less constructor) using [AppPreferences.serverPort] read
 * once at construction — a port change takes effect on the next
 * [com.paperweight.os.broadcast.BroadcastService] restart, not live.
 */
class EmbeddedHttpServer(
    context: Context,
    appPreferences: AppPreferences,
    broadcastEngine: BroadcastEngine,
    hlsDir: File = File(context.filesDir, "hls"),
) : NanoHTTPD(runBlocking { appPreferences.serverPort.first() }) {

    private val playlistRoute = PlaylistRoute(hlsDir)
    private val segmentRoute = SegmentRoute(hlsDir)
    private val statusRoute = StatusRoute(broadcastEngine)
    private val listenerWebRoute = ListenerWebRoute(context.applicationContext)

    fun startServer() {
        if (isAlive) return
        start(SOCKET_READ_TIMEOUT_MS, false)
    }

    fun stopServer() {
        if (isAlive) stop()
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/live/playlist.m3u8" -> playlistRoute.serve(session)
            uri.startsWith("/live/") -> segmentRoute.serve(session, uri.removePrefix("/live/"))
            uri == "/status" -> statusRoute.serve()
            else -> listenerWebRoute.serve(uri)
        }
    }

    private companion object {
        const val SOCKET_READ_TIMEOUT_MS = 5_000
    }
}
