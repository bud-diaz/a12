package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

// GET /api/schedule — dayparting blocks. Mirrors studio's ScheduleView.tsx
// `Block` type. day_of_week is 0=Sunday..6=Saturday, null = "Daily".
// start_time > end_time means an overnight block, not an error.
@Serializable
data class ScheduleBlock(
    val id: Int,
    val label: String? = null,
    val day_of_week: Int? = null,
    val start_time: String = "",
    val end_time: String = "",
    val category: String? = null,
    val mode: String? = "shuffle",
    val target_type: String? = null,
    val target_id: Int? = null,
)

// POST /api/schedule/blocks, PUT /api/schedule/blocks/{id} — same body shape
// for create and update, mirrors ScheduleForm's body() in ScheduleView.tsx.
@Serializable
data class ScheduleBlockRequest(
    val label: String? = null,
    val day_of_week: Int? = null,
    val start_time: String,
    val end_time: String,
    val category: String? = null,
    val mode: String = "shuffle",
    val target_type: String? = null,
    val target_id: Int? = null,
)

@Serializable
data class ScheduleMutationResponse(
    val ok: Boolean = false,
    val error: String? = null,
)

// GET /api/schedule/smart-playlists — mirrors studio's `Playlist` type.
// tags_filter is stored server-side as a JSON-stringified array, returned
// as a raw string (or null), not a JSON array.
@Serializable
data class SmartPlaylist(
    val id: Int,
    val name: String = "",
    val description: String? = null,
    val category: String? = null,
    val tags_filter: String? = null,
    val mode: String = "shuffle",
    val created_at: String? = null,
    val updated_at: String? = null,
)

// POST/PUT /api/schedule/smart-playlists — tags_filter is sent as a real
// JSON array here (server stringifies it on write).
@Serializable
data class SmartPlaylistRequest(
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val tags_filter: List<String> = emptyList(),
    val mode: String = "shuffle",
)

// GET /api/schedule/preview?from=<ISO>&hours=24 — response also duplicates
// `segments` as `timeline`; only `segments` is used client-side.
@Serializable
data class SchedulePreviewResponse(
    val segments: List<SchedulePreviewSegment> = emptyList(),
)

@Serializable
data class SchedulePreviewSegment(
    val blockId: Int? = null,
    val startTime: String? = null,
    val start: String? = null,
    val endTime: String? = null,
    val end: String? = null,
    val block: SchedulePreviewBlock? = null,
    val tracks: List<SchedulePreviewTrack> = emptyList(),
) {
    // Server includes both a named and a duplicate-shortname field for each
    // boundary; the studio UI falls back between them, so this does too.
    val resolvedStart: String? get() = startTime ?: start
    val resolvedEnd: String? get() = endTime ?: end
}

@Serializable
data class SchedulePreviewBlock(
    val id: Int,
    val label: String? = null,
    val mode: String? = null,
    val category: String? = null,
    val target_type: String? = null,
    val target_id: Int? = null,
)

// Preview tracks are a non-deterministic sample recomputed per request, not
// a locked queue — surface them to the user as a sample.
@Serializable
data class SchedulePreviewTrack(
    val id: Int,
    val title: String? = null,
    val filename: String? = null,
    val duration: Int = 0,
)
