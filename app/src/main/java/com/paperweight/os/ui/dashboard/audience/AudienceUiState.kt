package com.paperweight.os.ui.dashboard.audience

import com.paperweight.os.network.models.AudienceInsight
import com.paperweight.os.network.models.AudiencePerson
import com.paperweight.os.network.models.AudienceSegment
import com.paperweight.os.network.models.Automations
import com.paperweight.os.network.models.ExternalSearchItem
import com.paperweight.os.network.models.MarketingContacts
import com.paperweight.os.network.models.ParticipationRequestItem
import com.paperweight.os.network.models.Poll
import com.paperweight.os.network.models.RadioHostStatus

data class AudienceUiState(
    val outcomes: Map<String, Int> = emptyMap(),
    val insights: List<AudienceInsight> = emptyList(),
    val segments: List<AudienceSegment> = emptyList(),
    val people: List<AudiencePerson> = emptyList(),
    val contacts: MarketingContacts = MarketingContacts(),
    val automations: Automations = Automations(),
    val polls: List<Poll> = emptyList(),
    val requests: List<ParticipationRequestItem> = emptyList(),
    val creatorType: String? = null,
    val radioHost: RadioHostStatus = RadioHostStatus(),
    val externalResults: List<ExternalSearchItem> = emptyList(),
    val searchingExternal: Boolean = false,
    val actionMessage: String? = null,
    val actionInFlight: Boolean = false,
) {
    // Mutations refetch everything except the independently-managed people
    // search/segment filter and external-search results, matching how
    // Studio's react-query keys only invalidate the queries a mutation
    // actually affects.
    fun withCoreFrom(fresh: AudienceUiState): AudienceUiState = copy(
        outcomes = fresh.outcomes,
        insights = fresh.insights,
        segments = fresh.segments,
        contacts = fresh.contacts,
        automations = fresh.automations,
        polls = fresh.polls,
        requests = fresh.requests,
        creatorType = fresh.creatorType,
        radioHost = fresh.radioHost,
    )
}
