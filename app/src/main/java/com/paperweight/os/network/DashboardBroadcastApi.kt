package com.paperweight.os.network

import com.paperweight.os.network.models.BroadcastModeRequest
import com.paperweight.os.network.models.BroadcastMutationResponse
import com.paperweight.os.network.models.BroadcastQueueResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DashboardBroadcastApi {
    @GET("/api/dashboard/broadcast/queue")
    suspend fun queue(): BroadcastQueueResponse

    @POST("/api/dashboard/broadcast/mode")
    suspend fun setMode(@Body request: BroadcastModeRequest): BroadcastMutationResponse

    @POST("/api/dashboard/broadcast/restart")
    suspend fun restart(): BroadcastMutationResponse

    @POST("/api/dashboard/broadcast/stop")
    suspend fun stop(): BroadcastMutationResponse

    @DELETE("/api/dashboard/broadcast/queue/{idx}")
    suspend fun removeFromQueue(@Path("idx") idx: Int): BroadcastMutationResponse
}
