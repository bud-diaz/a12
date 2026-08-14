package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

// GET /api/stream/status — studio/src/lib/api.js's api.stream.status()
@Serializable
data class StreamStatus(
    val nowPlaying: NowPlaying? = null,
    val listenerCount: Int = 0,
    val mode: String = "shuffle",
    val liveActive: Boolean = false,
    val isVideo: Boolean = false,
)

@Serializable
data class NowPlaying(
    val id: Int = 0,
    val title: String = "",
    val artist: String? = null,
    val duration: Int? = null,
)
