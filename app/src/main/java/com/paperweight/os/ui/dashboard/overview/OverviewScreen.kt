package com.paperweight.os.ui.dashboard.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
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
import com.paperweight.os.network.models.AnalyticsActivityItem
import com.paperweight.os.network.models.AnalyticsHistoryDay
import com.paperweight.os.ui.components.MetricTile
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader
import kotlin.math.roundToInt

@Composable
fun OverviewScreen(viewModel: OverviewViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Signal / Overview",
                    title = data.stationLabel,
                    description = if (data.nowPlayingTitle != null) "Now playing: ${data.nowPlayingTitle}" else "Nothing queued right now.",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        label = "Listeners now",
                        value = data.listenerCount.toString(),
                        change = data.stationLabel,
                        icon = Icons.Outlined.Headphones,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "Catalog size",
                        value = data.catalogCount.toString(),
                        change = "${data.collectionsCount} collections",
                        icon = Icons.Outlined.LibraryMusic,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        label = "Listening hours",
                        value = "%.1fh".format(data.listeningHours),
                        change = "Last 30 days",
                        icon = Icons.Outlined.Schedule,
                        modifier = Modifier.weight(1f),
                    )
                    MetricTile(
                        label = "This month",
                        value = formatCents(data.monthRevenueCents),
                        change = "Unlocks + tips",
                        icon = Icons.Outlined.AccountBalanceWallet,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (data.weekHistory.isNotEmpty()) {
                item { WeekHistoryChart(data.weekHistory) }
            }
            item {
                Text(
                    text = "Recent activity",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            if (data.recentActivity.isEmpty()) {
                item {
                    Text(
                        text = "Nothing to show yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(data.recentActivity) { activity -> ActivityRow(activity) }
            }
        }
    }
}

@Composable
private fun WeekHistoryChart(history: List<AnalyticsHistoryDay>) {
    val maxListeners = (history.maxOfOrNull { it.unique_listeners } ?: 1).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    PanelCard {
        Text(text = "Audience, this week", style = MaterialTheme.typography.titleMedium)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(top = 12.dp),
        ) {
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
    }
}

@Composable
private fun ActivityRow(activity: AnalyticsActivityItem) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(text = activity.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = activity.detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun formatCents(cents: Long): String {
    val dollars = cents / 100.0
    return if (cents % 100 == 0L) "$${dollars.roundToInt()}" else "$%.2f".format(dollars)
}
