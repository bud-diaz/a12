package com.paperweight.os.network

import com.paperweight.os.network.models.DashboardAccountFull
import com.paperweight.os.network.models.DashboardSettings
import com.paperweight.os.network.models.DocsListResponse
import com.paperweight.os.network.models.ResetLinkResponse
import com.paperweight.os.network.models.SettingsMutationResponse
import com.paperweight.os.network.models.UpdateFeedSettingsRequest
import com.paperweight.os.network.models.UpdateGlowColorRequest
import com.paperweight.os.network.models.UpdateNotificationSettingsRequest
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// Mirrors views/SettingsView.tsx + lib/api.js's `dashboard.settings`,
// `dashboard.accounts`/`resetLink`, and `docs`. DesktopSection is
// intentionally not ported (no Electron-equivalent bridge on Android).
interface DashboardSettingsApi {
    @GET("/api/dashboard/settings")
    suspend fun settings(): DashboardSettings

    @PUT("/api/dashboard/settings")
    suspend fun updateNotifications(@Body request: UpdateNotificationSettingsRequest): SettingsMutationResponse

    @PUT("/api/dashboard/settings")
    suspend fun updateFeed(@Body request: UpdateFeedSettingsRequest): SettingsMutationResponse

    @PUT("/api/dashboard/settings")
    suspend fun updateGlowColor(@Body request: UpdateGlowColorRequest): SettingsMutationResponse

    @GET("/api/dashboard/accounts")
    suspend fun accounts(): List<DashboardAccountFull>

    @POST("/api/dashboard/accounts/{id}/reset-link")
    suspend fun generateResetLink(@Path("id") id: Int): ResetLinkResponse

    @GET("/api/docs")
    suspend fun docsList(): DocsListResponse

    // Raw text/Markdown, not JSON — decoded manually via ResponseBody.string().
    @GET("/api/docs/{id}")
    suspend fun docContent(@Path("id") id: String): ResponseBody
}
