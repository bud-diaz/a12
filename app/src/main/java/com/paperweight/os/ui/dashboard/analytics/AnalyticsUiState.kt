package com.paperweight.os.ui.dashboard.analytics

import com.paperweight.os.network.models.AnalyticsHistoryDay
import com.paperweight.os.network.models.AnalyticsSubscriberRow
import com.paperweight.os.network.models.AnalyticsTopTrack

data class AnalyticsUiState(
    val currentListeners: Int = 0,
    val peakToday: Int = 0,
    val activeSubscribers: Int = 0,
    val newSubscribersInRange: Int = 0,
    val totalListenersRange: Int = 0,
    val history: List<AnalyticsHistoryDay> = emptyList(),
    val subscriberRows: List<AnalyticsSubscriberRow> = emptyList(),
    val topTracks: List<AnalyticsTopTrack> = emptyList(),
    val allTimeTracks: List<AllTimeTrack> = emptyList(),
    val exportMessage: String? = null,
)

// Studio computes this client-side by joining library/structure with
// analytics/playcounts (views/Analytics.tsx's allTimePlays) — there's no
// single backend endpoint for it.
data class AllTimeTrack(
    val id: Int,
    val title: String,
    val artist: String?,
    val plays: Int,
    val durationSeconds: Double?,
)
