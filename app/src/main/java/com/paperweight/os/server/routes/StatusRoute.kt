package com.paperweight.os.server.routes

import com.paperweight.os.broadcast.BroadcastEngine
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Small JSON status endpoint the listener page polls for its "now playing" widget. */
class StatusRoute(private val broadcastEngine: BroadcastEngine) {
    fun serve(): NanoHTTPD.Response {
        val state = broadcastEngine.state.value
        val body = Json.encodeToString(
            StatusPayload(
                isRunning = state.isRunning,
                isMicLive = state.isMicLive,
                nowPlayingTitle = state.nowPlayingTitle,
                nowPlayingArtist = state.nowPlayingArtist,
                elapsedMs = state.elapsedMs,
                durationMs = state.durationMs,
                listenerCount = state.listenerCount,
                queueLength = state.queue.size,
            ),
        )
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", body).apply {
            addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        }
    }

    @Serializable
    private data class StatusPayload(
        val isRunning: Boolean,
        val isMicLive: Boolean,
        val nowPlayingTitle: String?,
        val nowPlayingArtist: String?,
        val elapsedMs: Long,
        val durationMs: Long,
        val listenerCount: Int,
        val queueLength: Int,
    )
}
