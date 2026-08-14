package com.paperweight.os.network

import com.paperweight.os.network.models.DeviceRedeemRequest
import com.paperweight.os.network.models.DeviceRedeemResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("/api/auth/dashboard/device/redeem")
    suspend fun redeemDevice(@Body body: DeviceRedeemRequest): Response<DeviceRedeemResponse>

    // Auth probe matching api.dashboard.check() in studio/src/lib/api.js —
    // any 2xx means the stored session is still valid.
    @GET("/api/dashboard/vault")
    suspend fun checkSession(): Response<Unit>
}
