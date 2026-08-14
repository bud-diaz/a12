package com.paperweight.os.ui.dashboard.analytics

// Mirrors views/Analytics.tsx: live listener stats, 30-day audience chart,
// subscriber growth, top tracks (7 days), and all-time most played.

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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.network.models.AnalyticsHistoryDay
import com.paperweight.os.network.models.AnalyticsSubscriberRow
import com.paperweight.os.network.models.AnalyticsTopTrack
import com.paperweight.os.ui.components.MetricAccent
import com.paperweight.os.ui.components.MetricTile
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Signal / Analytics",
                    title = "Know what resonates.",
                    description = "The useful version of the numbers: where people found you, what they stayed for, and when they come back.",
                    action = {
                        OutlinedButton(onClick = viewModel::exportReport) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Text(text = "Export report", modifier = Modifier.padding(start = 8.dp))
                        }
                    },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        label = "Listening now",
                        value = data.currentListeners.toString(),
                        change = "Live",
                        icon = Icons.Outlined.Headphones,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Peak today",
                        value = data.peakToday.toString(),
                        change = "Unique listeners",
                        icon = Icons.Outlined.TrendingUp,
                        accent = MetricAccent.Coral,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        label = "Active subscribers",
                        value = data.activeSubscribers.toString(),
                        change = "+${data.newSubscribersInRange} in 30 days",
                        icon = Icons.Outlined.People,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Listeners, 30 days",
                        value = data.totalListenersRange.toString(),
                        change = "Summed daily uniques",
                        icon = Icons.Outlined.PlayCircle,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item { AudienceHistoryChart(data.history) }
            item { SubscriberGrowthPanel(data.subscriberRows, data.activeSubscribers) }
            item { TrackListPanel(title = "Top tracks, last 7 days", emptyText = "No plays in the last 7 days.", tracks = data.topTracks) }
            item { AllTimePanel(data.allTimeTracks) }
            if (data.exportMessage != null) {
                item {
                    Text(
                        text = data.exportMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudienceHistoryChart(history: List<AnalyticsHistoryDay>) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Audience over time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(text = "Last 30 days", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        if (history.isEmpty()) {
            Text(
                text = "No listening data yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            val maxListeners = (history.maxOfOrNull { it.unique_listeners } ?: 1).coerceAtLeast(1)
            val barColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 16.dp)) {
                val barWidth = size.width / (history.size * 1.5f)
                val gap = barWidth / 2f
                history.forEachIndexed { index, day ->
                    val barHeight = size.height * (day.unique_listeners.toFloat() / maxListeners)
                    val x = index * (barWidth + gap)
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        style = Fill,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(text = history.first().date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = history.last().date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SubscriberGrowthPanel(rows: List<AnalyticsSubscriberRow>, activeTotal: Int) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Subscriber growth", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(text = "$activeTotal active", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        val recent = rows.filter { it.new_subscribers > 0 }.takeLast(5)
        if (recent.isEmpty()) {
            Text(
                text = "No new subscribers in the last 30 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { row ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(text = row.date, style = MaterialTheme.typography.bodySmall)
                        Text(text = "+${row.new_subscribers}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackListPanel(title: String, emptyText: String, tracks: List<AnalyticsTopTrack>) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (tracks.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tracks.forEach { track ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = track.title ?: track.filename, style = MaterialTheme.typography.bodyMedium)
                            if (track.artist != null) {
                                Text(text = track.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(text = "${track.play_count} plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = formatDurationSeconds(track.total_seconds.toDouble()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllTimePanel(tracks: List<AllTimeTrack>) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Most played, ever", style = MaterialTheme.typography.titleMedium)
        if (tracks.isEmpty()) {
            Text(
                text = "No plays recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tracks.forEach { track ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = track.title, style = MaterialTheme.typography.bodyMedium)
                            if (track.artist != null) {
                                Text(text = track.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(text = "${track.plays} plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = formatDurationSeconds(track.durationSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatDurationSeconds(seconds: Double?): String {
    if (seconds == null || !seconds.isFinite() || seconds <= 0) return "—:—"
    val total = seconds.roundToInt()
    val minutes = total / 60
    val secs = total % 60
    return "$minutes:${secs.toString().padStart(2, '0')}"
}
