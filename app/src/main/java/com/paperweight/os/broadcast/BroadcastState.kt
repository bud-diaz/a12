package com.paperweight.os.broadcast

data class BroadcastQueueTrack(
    val id: String,
    val title: String,
    val artist: String?,
)

data class BroadcastState(
    val isRunning: Boolean = false,
    val mode: String = "shuffle",
    val isMicLive: Boolean = false,
    val nowPlayingTrackId: String? = null,
    val nowPlayingTitle: String? = null,
    val nowPlayingArtist: String? = null,
    val elapsedMs: Long = 0,
    val durationMs: Long = 0,
    val listenerCount: Int = 0,
    val queue: List<BroadcastQueueTrack> = emptyList(),
    val playlistPath: String? = null,
    val segmentCount: Int = 0,
    val actionMessage: String? = null,
)
