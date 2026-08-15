package com.paperweight.os.broadcast

data class BroadcastQueueTrack(
    val id: String,
    val title: String,
    val artist: String?,
)

data class BroadcastState(
    val isRunning: Boolean = false,
    val mode: String = "shuffle",
    val nowPlayingTrackId: String? = null,
    val nowPlayingTitle: String? = null,
    val nowPlayingArtist: String? = null,
    val listenerCount: Int = 0,
    val queue: List<BroadcastQueueTrack> = emptyList(),
    val playlistPath: String? = null,
    val actionMessage: String? = null,
)
