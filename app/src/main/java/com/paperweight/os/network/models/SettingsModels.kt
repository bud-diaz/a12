package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

// GET /api/dashboard/settings — views/SettingsView.tsx's `DashboardSettings`.
@Serializable
data class DashboardSettings(
    val notifyWebhookUrl: String = "",
    val notifyLiveEnabled: Boolean = true,
    val feedEnabled: Boolean = false,
    val feedScope: String = "podcasts",
    val trackGlowColor: String = "#39ff14",
    val emailConfigured: Boolean = false,
)

// PUT /api/dashboard/settings — each save button in the source sends only
// its own field group, so each gets its own non-nullable request DTO rather
// than one shared DTO with fields that would need to encode as explicit
// null when omitted.
@Serializable
data class UpdateNotificationSettingsRequest(val notifyWebhookUrl: String, val notifyLiveEnabled: Boolean)

@Serializable
data class UpdateFeedSettingsRequest(val feedEnabled: Boolean, val feedScope: String)

@Serializable
data class UpdateGlowColorRequest(val trackGlowColor: String)

@Serializable
data class SettingsMutationResponse(val error: String? = null, val ok: Boolean = false)

// GET /api/dashboard/accounts — full shape needed for account recovery
// (id + email), distinct from Vault's email-only DashboardAccount.
@Serializable
data class DashboardAccountFull(val id: Int, val email: String = "")

// POST /api/dashboard/accounts/{id}/reset-link
@Serializable
data class ResetLinkResponse(
    val url: String? = null,
    val email: String? = null,
    val expiresAt: String? = null,
    val error: String? = null,
)

// GET /api/docs
@Serializable
data class DocEntry(val id: String, val title: String = "")

@Serializable
data class DocsListResponse(val docs: List<DocEntry> = emptyList())
