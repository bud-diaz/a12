package com.paperweight.os.ui.dashboard.broadcast

import com.paperweight.os.network.models.BroadcastQueueItem

data class BroadcastUiState(
    val mode: String = "shuffle",
    val nowPlayingTitle: String? = null,
    val nowPlayingArtist: String? = null,
    val liveActive: Boolean = false,
    val listenerCount: Int = 0,
    val queue: List<BroadcastQueueItem> = emptyList(),
    val actionMessage: String? = null,
    val actionInFlight: Boolean = false,
) {
    val alternateMode: String
        get() = if (mode == "shuffle") "scheduled" else "shuffle"
}
