package com.paperweight.os.network

import com.paperweight.os.network.models.StreamStatus
import retrofit2.http.GET

interface StreamApi {
    @GET("/api/stream/status")
    suspend fun status(): StreamStatus
}
