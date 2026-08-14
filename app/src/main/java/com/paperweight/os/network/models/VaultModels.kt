package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

// GET /api/dashboard/vault/pricing — views/Vault.tsx's `VaultTrackPrice`.
@Serializable
data class VaultTrackPrice(
    val content_id: Int,
    val title: String? = null,
    val filename: String = "",
    val suggested_price: Int = 0,
    val minimum_price: Int = 0,
    val allow_free: Int = 0,
    val payment_type: String = "one_time",
    val recurring_interval: String? = null,
)

@Serializable
data class VaultProjectItem(
    val content_id: Int,
    val title: String? = null,
    val filename: String = "",
)

@Serializable
data class VaultProject(
    val id: Int,
    val name: String = "",
    val description: String? = null,
    val suggested_price: Int = 0,
    val minimum_price: Int = 0,
    val allow_free: Int = 0,
    val payment_type: String = "one_time",
    val recurring_interval: String? = null,
    val items: List<VaultProjectItem> = emptyList(),
)

@Serializable
data class VaultPricingResponse(
    val trackPrices: List<VaultTrackPrice> = emptyList(),
    val projects: List<VaultProject> = emptyList(),
)

// GET/PUT /api/dashboard/vault/highlight
@Serializable
data class VaultHighlight(
    val highlight_type: String? = null,
    val highlight_id: Int? = null,
)

@Serializable
data class SetHighlightRequest(val type: String? = null, val id: Int? = null)

// PUT /api/dashboard/vault/pricing/track/{contentId}, PUT .../projects/{id}
@Serializable
data class VaultPricingRequest(
    val suggested_price: Int,
    val minimum_price: Int,
    val allow_free: Boolean,
    val payment_type: String,
    val recurring_interval: String? = null,
)

@Serializable
data class UpdateCollectionRequest(
    val name: String,
    val description: String? = null,
    val suggested_price: Int,
    val minimum_price: Int,
    val allow_free: Boolean,
    val payment_type: String,
    val recurring_interval: String? = null,
)

@Serializable
data class AddCollectionTrackRequest(val content_id: Int)

@Serializable
data class ReorderCollectionTracksRequest(val content_ids: List<Int>)

@Serializable
data class VaultMutationResponse(
    val error: String? = null,
    val ok: Boolean = false,
)

// GET /api/dashboard/media — views/Vault.tsx's `DashboardMediaItem`.
@Serializable
data class DashboardMediaItem(
    val id: Int,
    val title: String? = null,
    val filename: String = "",
    val visibility: String = "public",
)

// GET /api/dashboard/accounts
@Serializable
data class DashboardAccount(val email: String)

// GET /api/dashboard/tokens
@Serializable
data class VaultToken(
    val id: Int,
    val label: String? = null,
    val tier: String = "subscriber",
    val is_active: Boolean = false,
    val last_used: String? = null,
    val scope_type: String? = null,
    val scope_id: Int? = null,
)

@Serializable
data class CreateTokenRequest(val label: String, val tier: String)

@Serializable
data class CreateTokenResponse(
    val token: String? = null,
    val id: Int? = null,
    val error: String? = null,
)

@Serializable
data class SetTierRequest(val tier: String)

@Serializable
data class TokenAssignment(
    val id: Int,
    val email: String = "",
    val created_at: String? = null,
)

@Serializable
data class AssignTokenRequest(val email: String)
