package com.paperweight.os.network

import com.paperweight.os.network.models.DashboardEarnings
import com.paperweight.os.network.models.TipConfig
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface DashboardEarningsApi {
    @GET("/api/dashboard/earnings")
    suspend fun earnings(): DashboardEarnings

    @GET("/api/dashboard/tip-config")
    suspend fun tipConfig(): TipConfig

    @PUT("/api/dashboard/tip-config")
    suspend fun updateTipConfig(@Body body: TipConfig)
}
