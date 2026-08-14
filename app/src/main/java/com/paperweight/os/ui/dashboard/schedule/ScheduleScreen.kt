package com.paperweight.os.ui.dashboard.schedule

// Mirrors views/ScheduleView.tsx: schedule blocks + smart playlists CRUD,
// plus a "next 24 hours" preview panel. Studio's two-column desktop layout
// collapses to a single stacked column here, matching the mobile fallback.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.network.models.ScheduleBlock
import com.paperweight.os.network.models.ScheduleBlockRequest
import com.paperweight.os.network.models.SchedulePreviewSegment
import com.paperweight.os.network.models.SmartPlaylist
import com.paperweight.os.network.models.SmartPlaylistRequest
import com.paperweight.os.ui.components.DropdownField
import com.paperweight.os.ui.components.EmptyStateView
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val CATEGORIES = listOf(
    "" to "Any",
    "music" to "Music",
    "beats" to "Beats",
    "podcasts" to "Podcasts",
    "videos" to "Videos",
    "drafts" to "Drafts",
    "live_sessions" to "Live sessions",
)
private val MODES = listOf("shuffle" to "Shuffle", "sequential" to "Sequential")
private val PREVIEW_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d, h:mm a")

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        var addingBlock by rememberSaveable { mutableStateOf(false) }
        var editingBlockId by rememberSaveable { mutableStateOf<Int?>(null) }
        var addingPlaylist by rememberSaveable { mutableStateOf(false) }
        var editingPlaylistId by rememberSaveable { mutableStateOf<Int?>(null) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Signal / Schedule",
                    title = "Shape the broadcast day.",
                    description = "Dayparting blocks and smart playlists drive scheduled station playback.",
                    action = {
                        OutlinedButton(onClick = viewModel::enableScheduledMode, enabled = !data.actionInFlight) {
                            Icon(Icons.Outlined.Radio, contentDescription = null)
                            Text(text = "Enable scheduled mode", modifier = Modifier.padding(start = 8.dp))
                        }
                    },
                )
            }
            item {
                BlocksPanel(
                    blocks = data.blocks,
                    playlists = data.playlists,
                    adding = addingBlock,
                    editingId = editingBlockId,
                    actionInFlight = data.actionInFlight,
                    onAddClick = { addingBlock = true },
                    onEditClick = { id -> editingBlockId = if (editingBlockId == id) null else id },
                    onDelete = viewModel::deleteBlock,
                    onCancelAdd = { addingBlock = false },
                    onCancelEdit = { editingBlockId = null },
                    onSave = { id, request ->
                        viewModel.saveBlock(id, request)
                        addingBlock = false
                        editingBlockId = null
                    },
                )
            }
            item {
                PlaylistsPanel(
                    playlists = data.playlists,
                    adding = addingPlaylist,
                    editingId = editingPlaylistId,
                    actionInFlight = data.actionInFlight,
                    onAddClick = { addingPlaylist = true },
                    onEditClick = { id -> editingPlaylistId = if (editingPlaylistId == id) null else id },
                    onDelete = viewModel::deletePlaylist,
                    onCancelAdd = { addingPlaylist = false },
                    onCancelEdit = { editingPlaylistId = null },
                    onSave = { id, request ->
                        viewModel.savePlaylist(id, request)
                        addingPlaylist = false
                        editingPlaylistId = null
                    },
                )
            }
            item {
                PreviewPanel(segments = data.previewSegments, onRefresh = viewModel::refreshPreview)
            }
            if (data.actionMessage != null) {
                item {
                    Text(
                        text = data.actionMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BlocksPanel(
    blocks: List<ScheduleBlock>,
    playlists: List<SmartPlaylist>,
    adding: Boolean,
    editingId: Int?,
    actionInFlight: Boolean,
    onAddClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onCancelAdd: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: (Int?, ScheduleBlockRequest) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = "Schedule blocks", style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = onAddClick, enabled = !actionInFlight) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(text = "Block", modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (adding) {
            BlockForm(block = null, playlists = playlists, enabled = !actionInFlight, onCancel = onCancelAdd, onSave = onSave)
        }
        if (blocks.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.Schedule,
                title = "No dayparts yet",
                body = "Create a daily block or a day-specific block for scheduled playback.",
                actionLabel = "Add block",
                onAction = onAddClick,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                blocks.forEach { block ->
                    Column {
                        BlockRow(block = block, enabled = !actionInFlight, onEdit = { onEditClick(block.id) }, onDelete = { onDelete(block.id) })
                        if (editingId == block.id) {
                            BlockForm(block = block, playlists = playlists, enabled = !actionInFlight, onCancel = onCancelEdit, onSave = onSave)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockRow(block: ScheduleBlock, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = block.label ?: "Block #${block.id}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${dayLabel(block.day_of_week)} · ${block.start_time}-${block.end_time} · ${block.mode ?: "shuffle"} · ${sourceLabel(block)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            IconButton(onClick = onEdit, enabled = enabled) { Icon(Icons.Outlined.Edit, contentDescription = "Edit block") }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete block", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun dayLabel(dayOfWeek: Int?): String = if (dayOfWeek == null) "Daily" else DAYS.getOrElse(dayOfWeek) { "Daily" }

private fun sourceLabel(block: ScheduleBlock): String =
    if (block.target_type == "smart_playlist") "smart playlist #${block.target_id}" else block.category ?: "any category"

@Composable
private fun BlockForm(
    block: ScheduleBlock?,
    playlists: List<SmartPlaylist>,
    enabled: Boolean,
    onCancel: () -> Unit,
    onSave: (Int?, ScheduleBlockRequest) -> Unit,
) {
    var label by rememberSaveable(block?.id) { mutableStateOf(block?.label ?: "") }
    var day by rememberSaveable(block?.id) { mutableStateOf(block?.day_of_week) }
    var start by rememberSaveable(block?.id) { mutableStateOf(block?.start_time ?: "") }
    var end by rememberSaveable(block?.id) { mutableStateOf(block?.end_time ?: "") }
    var category by rememberSaveable(block?.id) { mutableStateOf(block?.category ?: "") }
    var mode by rememberSaveable(block?.id) { mutableStateOf(block?.mode ?: "shuffle") }
    var source by rememberSaveable(block?.id) { mutableStateOf(block?.target_type ?: "") }
    var targetId by rememberSaveable(block?.id) { mutableStateOf(block?.target_id) }

    val dayOptions: List<Pair<Int?, String>> = listOf<Pair<Int?, String>>(null to "Daily") + DAYS.mapIndexed { index, name -> index to name }
    val targetOptions: List<Pair<Int?, String>> = playlists.map { it.id to it.name }

    PanelCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label") },
            placeholder = { Text("Morning rotation") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            DropdownField(label = "Day", options = dayOptions, selected = day, onSelect = { day = it }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start (HH:MM)") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End (HH:MM)") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            DropdownField(label = "Category", options = CATEGORIES, selected = category, onSelect = { category = it }, modifier = Modifier.weight(1f))
            DropdownField(label = "Mode", options = MODES, selected = mode, onSelect = { mode = it }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            DropdownField(
                label = "Source",
                options = listOf("" to "Category", "smart_playlist" to "Smart playlist"),
                selected = source,
                onSelect = { source = it; if (it != "smart_playlist") targetId = null },
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                label = "Target",
                options = targetOptions,
                selected = targetId,
                onSelect = { targetId = it },
                enabled = source == "smart_playlist",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) { Text("Cancel") }
            Button(
                onClick = {
                    onSave(
                        block?.id,
                        ScheduleBlockRequest(
                            label = label.trim().ifBlank { null },
                            day_of_week = day,
                            start_time = start,
                            end_time = end,
                            category = category.ifBlank { null },
                            mode = mode,
                            target_type = source.ifBlank { null },
                            target_id = if (source == "smart_playlist") targetId else null,
                        ),
                    )
                },
                enabled = enabled && start.isNotBlank() && end.isNotBlank(),
            ) { Text(if (block != null) "Save block" else "Create block") }
        }
    }
}

@Composable
private fun PlaylistsPanel(
    playlists: List<SmartPlaylist>,
    adding: Boolean,
    editingId: Int?,
    actionInFlight: Boolean,
    onAddClick: () -> Unit,
    onEditClick: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onCancelAdd: () -> Unit,
    onCancelEdit: () -> Unit,
    onSave: (Int?, SmartPlaylistRequest) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = "Smart playlists", style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = onAddClick, enabled = !actionInFlight) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(text = "Playlist", modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (adding) {
            PlaylistForm(playlist = null, enabled = !actionInFlight, onCancel = onCancelAdd, onSave = onSave)
        }
        if (playlists.isEmpty()) {
            EmptyStateView(
                icon = Icons.AutoMirrored.Outlined.List,
                title = "No smart playlists",
                body = "Filter your library by category and tags, then assign the playlist to a schedule block.",
                actionLabel = "Add playlist",
                onAction = onAddClick,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                playlists.forEach { playlist ->
                    Column {
                        PlaylistRow(playlist = playlist, enabled = !actionInFlight, onEdit = { onEditClick(playlist.id) }, onDelete = { onDelete(playlist.id) })
                        if (editingId == playlist.id) {
                            PlaylistForm(playlist = playlist, enabled = !actionInFlight, onCancel = onCancelEdit, onSave = onSave)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: SmartPlaylist, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = playlist.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${playlist.category ?: "any category"} · ${tagsToText(playlist.tags_filter).ifBlank { "no tags" }} · ${playlist.mode}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            IconButton(onClick = onEdit, enabled = enabled) { Icon(Icons.Outlined.Edit, contentDescription = "Edit playlist") }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete playlist", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PlaylistForm(
    playlist: SmartPlaylist?,
    enabled: Boolean,
    onCancel: () -> Unit,
    onSave: (Int?, SmartPlaylistRequest) -> Unit,
) {
    var name by rememberSaveable(playlist?.id) { mutableStateOf(playlist?.name ?: "") }
    var description by rememberSaveable(playlist?.id) { mutableStateOf(playlist?.description ?: "") }
    var category by rememberSaveable(playlist?.id) { mutableStateOf(playlist?.category ?: "") }
    var mode by rememberSaveable(playlist?.id) { mutableStateOf(playlist?.mode ?: "shuffle") }
    var tags by rememberSaveable(playlist?.id) { mutableStateOf(tagsToText(playlist?.tags_filter)) }

    PanelCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, placeholder = { Text("Late night requests") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, placeholder = { Text("Optional note") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            DropdownField(label = "Category", options = CATEGORIES, selected = category, onSelect = { category = it }, modifier = Modifier.weight(1f))
            DropdownField(label = "Mode", options = MODES, selected = mode, onSelect = { mode = it }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags") }, placeholder = { Text("live, request, ambient") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) { Text("Cancel") }
            Button(
                onClick = {
                    onSave(
                        playlist?.id,
                        SmartPlaylistRequest(
                            name = name.trim(),
                            description = description.trim().ifBlank { null },
                            category = category.ifBlank { null },
                            tags_filter = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            mode = mode,
                        ),
                    )
                },
                enabled = enabled && name.isNotBlank(),
            ) { Text(if (playlist != null) "Save playlist" else "Create playlist") }
        }
    }
}

// tags_filter arrives as a JSON-stringified array (or null); a lightweight
// bracket/quote strip is enough since tag values never contain commas.
private fun tagsToText(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val trimmed = raw.trim().removePrefix("[").removeSuffix("]")
    return trimmed.split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }.joinToString(", ")
}

@Composable
private fun PreviewPanel(segments: List<SchedulePreviewSegment>, onRefresh: () -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Next 24 hours", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh preview") }
        }
        if (segments.isEmpty()) {
            Text(
                text = "No preview segments in this range.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                segments.forEach { segment -> PreviewRow(segment) }
            }
        }
    }
}

@Composable
private fun PreviewRow(segment: SchedulePreviewSegment) {
    PanelCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Text(
            text = segment.block?.label ?: segment.block?.let { "Block #${it.id}" } ?: "Shuffle",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "${formatSegmentTime(segment.resolvedStart)} - ${formatSegmentTime(segment.resolvedEnd)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "${segment.tracks.size} tracks (sample)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun formatSegmentTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(PREVIEW_TIME_FORMATTER)
    } catch (e: DateTimeParseException) {
        iso
    }
}
