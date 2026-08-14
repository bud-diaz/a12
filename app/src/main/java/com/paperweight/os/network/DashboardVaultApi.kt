package com.paperweight.os.network

import com.paperweight.os.network.models.AddCollectionTrackRequest
import com.paperweight.os.network.models.AssignTokenRequest
import com.paperweight.os.network.models.CreateTokenRequest
import com.paperweight.os.network.models.CreateTokenResponse
import com.paperweight.os.network.models.DashboardAccount
import com.paperweight.os.network.models.DashboardMediaItem
import com.paperweight.os.network.models.ReorderCollectionTracksRequest
import com.paperweight.os.network.models.SetHighlightRequest
import com.paperweight.os.network.models.SetTierRequest
import com.paperweight.os.network.models.TokenAssignment
import com.paperweight.os.network.models.UpdateCollectionRequest
import com.paperweight.os.network.models.VaultHighlight
import com.paperweight.os.network.models.VaultMutationResponse
import com.paperweight.os.network.models.VaultPricingRequest
import com.paperweight.os.network.models.VaultPricingResponse
import com.paperweight.os.network.models.VaultToken
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

// Mirrors views/Vault.tsx + lib/api.js's `dashboard.vault`, `dashboard.tokens`,
// `dashboard.media.list`/`.uploadArtwork`, `dashboard.accounts`.
interface DashboardVaultApi {
    @GET("/api/dashboard/vault/pricing")
    suspend fun pricing(): VaultPricingResponse

    @PUT("/api/dashboard/vault/pricing/track/{contentId}")
    suspend fun updateTrackPricing(@Path("contentId") contentId: Int, @Body request: VaultPricingRequest): VaultMutationResponse

    @PUT("/api/dashboard/vault/projects/{id}")
    suspend fun updateCollection(@Path("id") id: Int, @Body request: UpdateCollectionRequest): VaultMutationResponse

    @DELETE("/api/dashboard/vault/projects/{id}")
    suspend fun deleteCollection(@Path("id") id: Int): VaultMutationResponse

    @POST("/api/dashboard/vault/projects/{projId}/items")
    suspend fun addCollectionTrack(@Path("projId") projId: Int, @Body request: AddCollectionTrackRequest): VaultMutationResponse

    @PUT("/api/dashboard/vault/projects/{projId}/items/order")
    suspend fun reorderCollectionTracks(@Path("projId") projId: Int, @Body request: ReorderCollectionTracksRequest): VaultMutationResponse

    @DELETE("/api/dashboard/vault/projects/{projId}/items/{contentId}")
    suspend fun removeCollectionTrack(@Path("projId") projId: Int, @Path("contentId") contentId: Int): VaultMutationResponse

    @GET("/api/dashboard/vault/highlight")
    suspend fun highlight(): VaultHighlight

    @PUT("/api/dashboard/vault/highlight")
    suspend fun setHighlight(@Body request: SetHighlightRequest): VaultMutationResponse

    @GET("/api/dashboard/media")
    suspend fun mediaList(): List<DashboardMediaItem>

    @Multipart
    @POST("/api/dashboard/media/{id}/artwork")
    suspend fun uploadArtwork(@Path("id") id: Int, @Part artwork: MultipartBody.Part): VaultMutationResponse

    @GET("/api/dashboard/accounts")
    suspend fun accounts(): List<DashboardAccount>

    @GET("/api/dashboard/tokens")
    suspend fun tokens(): List<VaultToken>

    @POST("/api/dashboard/tokens")
    suspend fun createToken(@Body request: CreateTokenRequest): CreateTokenResponse

    @DELETE("/api/dashboard/tokens/{id}")
    suspend fun revokeToken(@Path("id") id: Int): VaultMutationResponse

    @PATCH("/api/dashboard/tokens/{id}/tier")
    suspend fun setTokenTier(@Path("id") id: Int, @Body request: SetTierRequest): VaultMutationResponse

    @GET("/api/dashboard/tokens/{id}/assignments")
    suspend fun tokenAssignments(@Path("id") id: Int): List<TokenAssignment>

    @POST("/api/dashboard/tokens/{id}/assignments")
    suspend fun assignToken(@Path("id") id: Int, @Body request: AssignTokenRequest): VaultMutationResponse

    @DELETE("/api/dashboard/tokens/{tokenId}/assignments/{assignmentId}")
    suspend fun unassignToken(@Path("tokenId") tokenId: Int, @Path("assignmentId") assignmentId: Int): VaultMutationResponse
}
