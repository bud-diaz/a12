package com.paperweight.os.network

import com.paperweight.os.network.models.ScheduleBlock
import com.paperweight.os.network.models.ScheduleBlockRequest
import com.paperweight.os.network.models.ScheduleMutationResponse
import com.paperweight.os.network.models.SchedulePreviewResponse
import com.paperweight.os.network.models.SmartPlaylist
import com.paperweight.os.network.models.SmartPlaylistRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Mirrors views/ScheduleView.tsx + lib/api.js's `dashboard.schedule` block.
// Note the real routes are /api/schedule*, not /api/dashboard/schedule* —
// only the block/playlist mutation endpoints (not GET /api/schedule) are
// desktop-platform gated (403 on non-desktop deployments).
interface DashboardScheduleApi {
    @GET("/api/schedule")
    suspend fun blocks(): List<ScheduleBlock>

    @POST("/api/schedule/blocks")
    suspend fun createBlock(@Body request: ScheduleBlockRequest): ScheduleBlock

    @PUT("/api/schedule/blocks/{id}")
    suspend fun updateBlock(@Path("id") id: Int, @Body request: ScheduleBlockRequest): ScheduleBlock

    @DELETE("/api/schedule/blocks/{id}")
    suspend fun deleteBlock(@Path("id") id: Int): ScheduleMutationResponse

    @GET("/api/schedule/preview")
    suspend fun preview(@Query("from") from: String, @Query("hours") hours: Int = 24): SchedulePreviewResponse

    @GET("/api/schedule/smart-playlists")
    suspend fun smartPlaylists(): List<SmartPlaylist>

    @POST("/api/schedule/smart-playlists")
    suspend fun createSmartPlaylist(@Body request: SmartPlaylistRequest): SmartPlaylist

    @PUT("/api/schedule/smart-playlists/{id}")
    suspend fun updateSmartPlaylist(@Path("id") id: Int, @Body request: SmartPlaylistRequest): SmartPlaylist

    @DELETE("/api/schedule/smart-playlists/{id}")
    suspend fun deleteSmartPlaylist(@Path("id") id: Int): ScheduleMutationResponse
}
