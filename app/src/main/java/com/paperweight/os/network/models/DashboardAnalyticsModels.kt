package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

// GET /api/dashboard/analytics/history?days= — studio's views/Overview.tsx
// and views/Analytics.tsx HistoryDay/HistoryRow types.
@Serializable
data class AnalyticsHistoryDay(
    val date: String,
    val unique_listeners: Int = 0,
    val total_listen_sec: Long = 0,
    val top_media_id: Int? = null,
)

// GET /api/dashboard/analytics/activity?limit= — views/Overview.tsx's ActivityItem.
@Serializable
data class AnalyticsActivityItem(
    val type: String, // "tip" | "unlock" | "subscription"
    val title: String,
    val detail: String,
    val occurred_at: String,
)

// GET /api/dashboard/analytics/live — views/Analytics.tsx's LiveStats.
@Serializable
data class AnalyticsLiveStats(
    val currentListeners: Int = 0,
    val peakToday: Int = 0,
)

// GET /api/dashboard/analytics/top?limit=&period= — views/Analytics.tsx's TopTrack.
@Serializable
data class AnalyticsTopTrack(
    val id: Int,
    val title: String? = null,
    val filename: String = "",
    val artist: String? = null,
    val play_count: Int = 0,
    val total_seconds: Long = 0,
)

// GET /api/dashboard/analytics/subscribers?days= — views/Analytics.tsx's SubscriberStats.
@Serializable
data class AnalyticsSubscriberStats(
    val activeTotal: Int = 0,
    val rows: List<AnalyticsSubscriberRow> = emptyList(),
)

@Serializable
data class AnalyticsSubscriberRow(
    val date: String,
    val new_subscribers: Int = 0,
)
