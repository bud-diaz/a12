package com.paperweight.os.network

import com.paperweight.os.network.models.AnalyticsActivityItem
import com.paperweight.os.network.models.AnalyticsHistoryDay
import com.paperweight.os.network.models.AnalyticsLiveStats
import com.paperweight.os.network.models.AnalyticsSubscriberStats
import com.paperweight.os.network.models.AnalyticsTopTrack
import retrofit2.http.GET
import retrofit2.http.Query

interface DashboardAnalyticsApi {
    @GET("/api/dashboard/analytics/history")
    suspend fun history(@Query("days") days: Int): List<AnalyticsHistoryDay>

    @GET("/api/dashboard/analytics/activity")
    suspend fun activity(@Query("limit") limit: Int = 10): List<AnalyticsActivityItem>

    @GET("/api/dashboard/analytics/live")
    suspend fun live(): AnalyticsLiveStats

    @GET("/api/dashboard/analytics/top")
    suspend fun top(@Query("limit") limit: Int = 3, @Query("period") period: String? = null): List<AnalyticsTopTrack>

    @GET("/api/dashboard/analytics/subscribers")
    suspend fun subscribers(@Query("days") days: Int = 90): AnalyticsSubscriberStats

    @GET("/api/dashboard/analytics/playcounts")
    suspend fun playcounts(): Map<String, Int>
}
