package com.paperweight.os.network

import com.paperweight.os.network.models.AutoCreateTunnelRequest
import com.paperweight.os.network.models.CloudflareZonesResponse
import com.paperweight.os.network.models.SaveCloudflareTokenRequest
import com.paperweight.os.network.models.SaveTelemetrySecretRequest
import com.paperweight.os.network.models.SetSearchableRequest
import com.paperweight.os.network.models.SetupProgress
import com.paperweight.os.network.models.SignupRequest
import com.paperweight.os.network.models.StationData
import com.paperweight.os.network.models.StationHealth
import com.paperweight.os.network.models.StationMutationResponse
import com.paperweight.os.network.models.TunnelStatus
import com.paperweight.os.network.models.UpdateStationUrlRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

// Mirrors views/Station.tsx + lib/api.js's `dashboard.station`,
// `dashboard.setupProgress`, `dashboard.signup`/`dismissSignup`.
interface DashboardStationApi {
    @GET("/api/dashboard/station")
    suspend fun station(): StationData

    @GET("/api/dashboard/station/health")
    suspend fun health(): StationHealth

    @PUT("/api/dashboard/station/url")
    suspend fun updateUrl(@Body request: UpdateStationUrlRequest): StationMutationResponse

    @PUT("/api/dashboard/station/searchable")
    suspend fun setSearchable(@Body request: SetSearchableRequest): StationMutationResponse

    @PUT("/api/dashboard/station/cloudflare/token")
    suspend fun saveCloudflareToken(@Body request: SaveCloudflareTokenRequest): StationMutationResponse

    @GET("/api/dashboard/station/cloudflare/zones")
    suspend fun cloudflareZones(): CloudflareZonesResponse

    @POST("/api/dashboard/station/cloudflare/auto-tunnel")
    suspend fun autoCreateTunnel(@Body request: AutoCreateTunnelRequest): StationMutationResponse

    @GET("/api/dashboard/station/cloudflare/tunnel/status")
    suspend fun tunnelStatus(): TunnelStatus

    @POST("/api/dashboard/station/cloudflare/tunnel/connect")
    suspend fun tunnelConnect(): StationMutationResponse

    @POST("/api/dashboard/station/cloudflare/tunnel/disconnect")
    suspend fun tunnelDisconnect(): StationMutationResponse

    @PUT("/api/dashboard/station/telemetry/secret")
    suspend fun saveTelemetrySecret(@Body request: SaveTelemetrySecretRequest): StationMutationResponse

    @POST("/api/dashboard/station/telemetry/register")
    suspend fun registerTelemetry(): StationMutationResponse

    @POST("/api/dashboard/station/frp/paperweighthq/register-and-create")
    suspend fun createHqTunnel(): StationMutationResponse

    @GET("/api/dashboard/setup-progress")
    suspend fun setupProgress(): SetupProgress

    @POST("/api/dashboard/signup")
    suspend fun signup(@Body request: SignupRequest): StationMutationResponse

    @POST("/api/dashboard/signup/dismiss")
    suspend fun dismissSignup(): StationMutationResponse
}
