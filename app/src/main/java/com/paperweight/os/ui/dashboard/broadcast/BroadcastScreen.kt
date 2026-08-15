package com.paperweight.os.ui.dashboard.broadcast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.network.models.BroadcastQueueItem
import com.paperweight.os.ui.components.EmptyStateView
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader

@Composable
fun BroadcastScreen(viewModel: BroadcastViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Signal / Broadcast",
                    title = if (data.liveActive) "The room is open." else "Your room, when you're ready.",
                    description = if (data.micLive) {
                        "The A12 mic is feeding the same HLS stream. Toggle off to return to the station rotation."
                    } else if (data.liveActive) {
                        "Listeners are in the room. Keep making it feel like they found something."
                    } else {
                        "Manage station rotation, queue state, and the live-mic cutover."
                    },
                    action = {
                        AssistChip(
                            onClick = {},
                            label = { Text(if (data.liveActive) "Live now" else "Studio ready") },
                            leadingIcon = { Icon(Icons.Outlined.Radio, contentDescription = null) },
                        )
                    },
                )
            }
            item { LiveReadinessPanel(data) }
            item {
                RotationPanel(
                    data = data,
                    onToggleMode = viewModel::toggleMode,
                    onRestart = viewModel::restart,
                    onToggleMicLive = viewModel::toggleMicLive,
                    onSeedValidationTone = viewModel::seedValidationTone,
                )
            }
            item { QueuePanel(data.queue, actionInFlight = data.actionInFlight, onRemove = viewModel::removeFromQueue) }
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
private fun LiveReadinessPanel(data: BroadcastUiState) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (data.nowPlayingTitle != null) data.nowPlayingTitle else "Nothing playing",
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (data.nowPlayingArtist != null) {
                    Text(
                        text = data.nowPlayingArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = "${data.listenerCount} listeners tuned in",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            BroadcastWaveform(live = data.liveActive, modifier = Modifier.weight(1f).height(96.dp))
        }
    }
}

@Composable
private fun RotationPanel(
    data: BroadcastUiState,
    onToggleMode: () -> Unit,
    onRestart: () -> Unit,
    onToggleMicLive: () -> Unit,
    onSeedValidationTone: () -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Station rotation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(text = data.mode.uppercase(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 18.dp)) {
            Button(onClick = onToggleMode, enabled = !data.actionInFlight && !data.micLive, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Shuffle, contentDescription = null)
                Text(text = "Switch to ${data.alternateMode}", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onRestart, enabled = !data.actionInFlight && !data.micLive, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text(text = "Restart", modifier = Modifier.padding(start = 8.dp))
            }
        }
        Button(
            onClick = onToggleMicLive,
            enabled = !data.actionInFlight,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Icon(Icons.Outlined.Radio, contentDescription = null)
            Text(text = if (data.micLive) "Stop live mic" else "Go live", modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            text = if (data.micLive) {
                "Live mic is active. Toggle off to return to station rotation at the next segment boundary."
            } else {
                "Cuts the stream to the A12 microphone using the same AAC/HLS output path."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (data.validationToneAvailable && data.queue.isEmpty()) {
            OutlinedButton(
                onClick = onSeedValidationTone,
                enabled = !data.actionInFlight,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Outlined.Radio, contentDescription = null)
                Text(text = "Generate Phase 5 validation tone", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = "Debug-only: creates a real local WAV track so the A12 can generate HLS for LAN playback validation.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun QueuePanel(
    queue: List<BroadcastQueueItem>,
    actionInFlight: Boolean,
    onRemove: (Int) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Broadcast queue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        if (queue.isEmpty()) {
            EmptyStateView(
                icon = Icons.AutoMirrored.Outlined.List,
                title = "Queue is empty",
                body = "Add tracks from the library in Studio; queued tracks will show here for removal.",
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                queue.forEachIndexed { index, item ->
                    QueueRow(item = item, index = index, enabled = !actionInFlight, onRemove = onRemove)
                }
            }
        }
    }
}

@Composable
private fun QueueRow(item: BroadcastQueueItem, index: Int, enabled: Boolean, onRemove: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title ?: "Track ${item.stableId}", style = MaterialTheme.typography.bodyMedium)
            if (item.artist != null) {
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onRemove(index) }, enabled = enabled) {
            Icon(Icons.Outlined.Close, contentDescription = "Remove from queue", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BroadcastWaveform(live: Boolean, modifier: Modifier = Modifier) {
    val accent = if (live) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val step = size.width / 16f
        repeat(16) { index ->
            val x = index * step + step / 2f
            val heightPercent = 0.2f + ((index * 7) % 10) / 12f
            val yTop = size.height * (1f - heightPercent)
            drawLine(
                color = if (live) primary else accent,
                start = Offset(x, size.height),
                end = Offset(x, yTop),
                strokeWidth = 5f,
            )
        }
        drawCircle(
            color = Color.Transparent,
            radius = size.minDimension / 2.3f,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = 2f),
        )
    }
}
