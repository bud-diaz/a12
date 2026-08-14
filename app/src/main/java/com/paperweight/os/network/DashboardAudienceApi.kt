package com.paperweight.os.network

import com.paperweight.os.network.models.AudienceMutationResponse
import com.paperweight.os.network.models.AudiencePeopleResponse
import com.paperweight.os.network.models.AudienceSegmentsResponse
import com.paperweight.os.network.models.AudienceToday
import com.paperweight.os.network.models.Automations
import com.paperweight.os.network.models.CreatePollRequest
import com.paperweight.os.network.models.CreatorTypeResponse
import com.paperweight.os.network.models.ExternalSearchResponse
import com.paperweight.os.network.models.ImportExternalRequest
import com.paperweight.os.network.models.ImportExternalResponse
import com.paperweight.os.network.models.MarketingContacts
import com.paperweight.os.network.models.ParticipationRequestsResponse
import com.paperweight.os.network.models.PauseAutomationsRequest
import com.paperweight.os.network.models.PollsResponse
import com.paperweight.os.network.models.RadioHostStatus
import com.paperweight.os.network.models.SetPollStatusRequest
import com.paperweight.os.network.models.SweepResponse
import com.paperweight.os.network.models.UpdateRequestStatusRequest
import com.paperweight.os.network.models.UpdateRuleEnabledRequest
import com.paperweight.os.network.models.UpdateRuleModeRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Mirrors views/AudienceView.tsx + lib/api.js's `dashboard.today`,
// `.audienceMemory`, `.audience()`, `.automations`, `.participation`,
// `.creatorType`, `.radioHostStatus`/`.toggleRadioHost`, `.externalSearch`,
// and `.media.importExternal`. radio-host and media/external are
// desktop-platform gated (403 on non-desktop deployments).
interface DashboardAudienceApi {
    @GET("/api/dashboard/today")
    suspend fun today(): AudienceToday

    @GET("/api/dashboard/audience-memory/segments")
    suspend fun segments(): AudienceSegmentsResponse

    @GET("/api/dashboard/audience-memory/people")
    suspend fun people(@Query("search") search: String = ""): AudiencePeopleResponse

    @GET("/api/dashboard/audience-memory/segments/{key}")
    suspend fun peopleInSegment(@Path("key") key: String): AudiencePeopleResponse

    @GET("/api/dashboard/audience")
    suspend fun marketingContacts(): MarketingContacts

    @GET("/api/dashboard/automations")
    suspend fun automations(): Automations

    @PUT("/api/dashboard/automations/pause")
    suspend fun pauseAutomations(@Body request: PauseAutomationsRequest): AudienceMutationResponse

    @PUT("/api/dashboard/automations/rules/{id}")
    suspend fun setRuleEnabled(@Path("id") id: Int, @Body request: UpdateRuleEnabledRequest): AudienceMutationResponse

    @PUT("/api/dashboard/automations/rules/{id}")
    suspend fun setRuleMode(@Path("id") id: Int, @Body request: UpdateRuleModeRequest): AudienceMutationResponse

    @POST("/api/dashboard/automations/runs/{id}/send")
    suspend fun sendAutomationRun(@Path("id") id: Int): AudienceMutationResponse

    @POST("/api/dashboard/automations/sweep")
    suspend fun sweepAutomations(): SweepResponse

    @GET("/api/dashboard/participation/polls")
    suspend fun polls(): PollsResponse

    @POST("/api/dashboard/participation/polls")
    suspend fun createPoll(@Body request: CreatePollRequest): AudienceMutationResponse

    @PUT("/api/dashboard/participation/polls/{id}/status")
    suspend fun setPollStatus(@Path("id") id: Int, @Body request: SetPollStatusRequest): AudienceMutationResponse

    @GET("/api/dashboard/participation/requests")
    suspend fun requests(): ParticipationRequestsResponse

    @PUT("/api/dashboard/participation/requests/{id}")
    suspend fun updateRequestStatus(@Path("id") id: Int, @Body request: UpdateRequestStatusRequest): AudienceMutationResponse

    @GET("/api/dashboard/creator-type")
    suspend fun creatorType(): CreatorTypeResponse

    @GET("/api/dashboard/radio-host")
    suspend fun radioHostStatus(): RadioHostStatus

    @POST("/api/dashboard/radio-host")
    suspend fun toggleRadioHost(): RadioHostStatus

    @GET("/api/dashboard/external-search")
    suspend fun externalSearch(@Query("platform") platform: String, @Query("q") query: String): ExternalSearchResponse

    @POST("/api/dashboard/media/external")
    suspend fun importExternal(@Body request: ImportExternalRequest): ImportExternalResponse
}
