package com.paperweight.os.ui.dashboard.schedule

import com.paperweight.os.network.models.ScheduleBlock
import com.paperweight.os.network.models.SchedulePreviewSegment
import com.paperweight.os.network.models.SmartPlaylist

data class ScheduleUiState(
    val blocks: List<ScheduleBlock> = emptyList(),
    val playlists: List<SmartPlaylist> = emptyList(),
    val previewSegments: List<SchedulePreviewSegment> = emptyList(),
    val actionMessage: String? = null,
    val actionInFlight: Boolean = false,
)
