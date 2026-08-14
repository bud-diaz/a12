package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

@Serializable
data class BroadcastQueueResponse(
    val queue: List<BroadcastQueueItem> = emptyList(),
)

// The Studio type originally called this mediaId, while the current Express
// endpoint returns id. Keep both nullable so the Android client tolerates either
// server shape during the port.
@Serializable
data class BroadcastQueueItem(
    val id: Int? = null,
    val mediaId: Int? = null,
    val title: String? = null,
    val artist: String? = null,
) {
    val stableId: Int
        get() = mediaId ?: id ?: 0
}

@Serializable
data class BroadcastModeRequest(
    val mode: String,
)

@Serializable
data class BroadcastMutationResponse(
    val ok: Boolean = false,
    val mode: String? = null,
    val restarting: Boolean = false,
    val stopped: Boolean = false,
    val queueLength: Int? = null,
    val error: String? = null,
)
