package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

// GET /api/dashboard/today — views/AudienceView.tsx's `Today`.
@Serializable
data class AudienceToday(
    val outcomes: Map<String, Int> = emptyMap(),
    val insights: List<AudienceInsight> = emptyList(),
)

@Serializable
data class AudienceInsight(
    val key: String,
    val eyebrow: String? = null,
    val title: String = "",
    val body: String = "",
    val tone: String? = null,
)

// GET /api/dashboard/audience-memory/segments
@Serializable
data class AudienceSegment(
    val key: String,
    val label: String = "",
    val count: Int = 0,
)

@Serializable
data class AudienceSegmentsResponse(
    val segments: List<AudienceSegment> = emptyList(),
)

// GET /api/dashboard/audience-memory/people?search= and .../segments/{key}
@Serializable
data class AudiencePerson(
    val profile_id: Int,
    val display_name: String? = null,
    val email: String? = null,
    val listen_count: Int = 0,
    val listen_seconds: Long = 0,
    val purchase_cents: Long = 0,
    val active_subscriptions: Int = 0,
    val favorite_title: String? = null,
    val last_listen_at: String? = null,
    val last_seen_at: String? = null,
)

@Serializable
data class AudiencePeopleResponse(
    val people: List<AudiencePerson> = emptyList(),
)

// GET /api/dashboard/audience — consented marketing contacts.
@Serializable
data class AudienceContact(
    val email: String,
    val name: String? = null,
    val source: String = "",
    val created_at: String = "",
)

@Serializable
data class MarketingContacts(
    val total: Int = 0,
    val bySource: Map<String, Int> = emptyMap(),
    val contacts: List<AudienceContact> = emptyList(),
)

// GET /api/dashboard/automations
@Serializable
data class AutomationRule(
    val id: Int,
    val name: String = "",
    val description: String = "",
    val trigger: String = "",
    val enabled: Boolean = false,
    val mode: String = "draft",
    val marketing: Boolean = false,
)

@Serializable
data class AutomationRun(
    val id: Int,
    val display_name: String? = null,
    val template_key: String? = null,
    val explanation: String? = null,
    val status: String = "",
    val last_error: String? = null,
)

@Serializable
data class Automations(
    val paused: Boolean = false,
    val rules: List<AutomationRule> = emptyList(),
    val runs: List<AutomationRun> = emptyList(),
)

@Serializable
data class PauseAutomationsRequest(val paused: Boolean)

// Rule updates are always single-field from the UI (enabled OR mode, never
// both) — separate request types avoid ever encoding the other as an
// explicit JSON null, which the server's `!== undefined` check would treat
// as "clear this field" rather than "leave it alone".
@Serializable
data class UpdateRuleEnabledRequest(val enabled: Boolean)

@Serializable
data class UpdateRuleModeRequest(val mode: String)

@Serializable
data class SweepResponse(val created: Int = 0)

// GET/POST /api/dashboard/participation/polls
@Serializable
data class PollOption(val label: String = "", val votes: Int = 0)

@Serializable
data class Poll(
    val id: Int,
    val question: String = "",
    val status: String = "open",
    val options: List<PollOption> = emptyList(),
)

@Serializable
data class PollsResponse(val polls: List<Poll> = emptyList())

@Serializable
data class CreatePollRequest(val question: String, val options: List<String>)

@Serializable
data class SetPollStatusRequest(val status: String)

// GET/PUT /api/dashboard/participation/requests
@Serializable
data class ParticipationRequestItem(
    val id: Int,
    val media_title: String = "",
    val listener_name: String? = null,
    val dedication: String? = null,
    val status: String = "pending",
)

@Serializable
data class ParticipationRequestsResponse(val requests: List<ParticipationRequestItem> = emptyList())

@Serializable
data class UpdateRequestStatusRequest(val status: String)

// GET /api/dashboard/creator-type
@Serializable
data class CreatorTypeResponse(
    val creatorType: String? = null,
    val stationIdentity: String? = null,
)

// GET/POST /api/dashboard/radio-host
@Serializable
data class RadioHostStatus(
    val radioHost: Boolean = false,
    val locked: Boolean = false,
    val switches: Int = 0,
    val error: String? = null,
)

// GET /api/dashboard/external-search?platform=&q=
@Serializable
data class ExternalSearchItem(
    val title: String = "",
    val artist: String? = null,
    val platform: String = "",
    val externalUrl: String? = null,
    val duration: Double? = null,
)

@Serializable
data class ExternalSearchResponse(val items: List<ExternalSearchItem> = emptyList())

// POST /api/dashboard/media/external — desktop-platform gated (403 on
// non-desktop deployments, same as the block/playlist mutations in
// DashboardScheduleApi).
@Serializable
data class ImportExternalRequest(
    val title: String,
    val artist: String? = null,
    val platform: String,
    val externalUrl: String? = null,
    val duration: Double? = null,
)

@Serializable
data class ImportExternalResponse(
    val id: Int? = null,
    val title: String? = null,
    val duplicate: Boolean = false,
    val error: String? = null,
)

// Generic small mutation response shared by the requests/polls/rule
// endpoints, which return either {ok:true}/{id} on success or {error} —
// distinguished by HTTP status, not payload shape.
@Serializable
data class AudienceMutationResponse(
    val ok: Boolean = false,
    val id: Int? = null,
    val error: String? = null,
)
