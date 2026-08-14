package com.paperweight.os.ui.dashboard.settings

import com.paperweight.os.network.models.DashboardAccountFull
import com.paperweight.os.network.models.DocEntry

data class SettingsUiState(
    val notifyWebhookUrl: String = "",
    val notifyLiveEnabled: Boolean = true,
    val feedEnabled: Boolean = false,
    val feedScope: String = "podcasts",
    val trackGlowColor: String = "#39ff14",
    val emailConfigured: Boolean = false,
    val accounts: List<DashboardAccountFull> = emptyList(),
    val docs: List<DocEntry> = emptyList(),
    val selectedDocId: String? = null,
    val selectedDocTitle: String = "",
    val selectedDocText: String? = null,
    val selectedDocLoading: Boolean = false,
    val resetLinkUrl: String? = null,
    val resetLinkEmail: String? = null,
    val resetLinkExpiresAt: String? = null,
    val actionMessage: String? = null,
    val actionInFlight: Boolean = false,
) {
    // Mutations refetch settings/accounts/docs but shouldn't clobber the
    // independently-managed doc viewer or reset-link result panels.
    fun withCoreFrom(fresh: SettingsUiState): SettingsUiState = copy(
        notifyWebhookUrl = fresh.notifyWebhookUrl,
        notifyLiveEnabled = fresh.notifyLiveEnabled,
        feedEnabled = fresh.feedEnabled,
        feedScope = fresh.feedScope,
        trackGlowColor = fresh.trackGlowColor,
        emailConfigured = fresh.emailConfigured,
        accounts = fresh.accounts,
        docs = fresh.docs,
    )
}
