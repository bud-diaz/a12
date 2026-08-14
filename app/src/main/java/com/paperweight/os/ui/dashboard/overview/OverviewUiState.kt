package com.paperweight.os.ui.dashboard.overview

import com.paperweight.os.network.models.AnalyticsActivityItem
import com.paperweight.os.network.models.AnalyticsHistoryDay

data class OverviewUiState(
    val stationLabel: String,
    val listenerCount: Int,
    val nowPlayingTitle: String?,
    val catalogCount: Int,
    val collectionsCount: Int,
    val listeningHours: Double,
    val monthRevenueCents: Long,
    val weekHistory: List<AnalyticsHistoryDay>,
    val recentActivity: List<AnalyticsActivityItem>,
)
