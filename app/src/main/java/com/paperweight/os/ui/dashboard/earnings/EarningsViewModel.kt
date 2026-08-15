package com.paperweight.os.ui.dashboard.earnings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.di.ServiceLocator
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Payments/tips are out of scope entirely for v1 (plan doc: "no processor, no
// webhooks, no local IOU ledger"), not just deferred UI — the old Retrofit-era
// revenue/tips/subscriptions screen this replaced is gone, not extended.
// This is a static "coming soon" shell plus a read of the inert vault
// pricing metadata Phase 2 already made editable
// (VaultViewModel.saveLocalTrackPricing), so pricing you set on tracks has
// somewhere honest to show up before real payment infrastructure exists.
class EarningsViewModel(application: Application) : AndroidViewModel(application) {
    private val services = ServiceLocator.get(application)

    private val _state = MutableStateFlow<ScreenState<EarningsUiState>>(ScreenState.Content(EarningsUiState()))
    val state: StateFlow<ScreenState<EarningsUiState>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            services.vaultRepository.observeTracks().collect { tracks ->
                val priced = tracks.filter { it.visibility == "public" && it.suggestedPriceCents > 0 }
                val current = (_state.value as? ScreenState.Content)?.data
                _state.value = ScreenState.Content(
                    EarningsUiState(
                        pricedPublicTrackCount = priced.size,
                        lowestSuggestedPriceCents = priced.minOfOrNull { it.suggestedPriceCents },
                        highestSuggestedPriceCents = priced.maxOfOrNull { it.suggestedPriceCents },
                        actionMessage = current?.actionMessage,
                    ),
                )
            }
        }
    }

    fun openPaymentSettings() = notify(NOT_WIRED)

    fun notify(message: String) {
        val current = (_state.value as? ScreenState.Content)?.data ?: EarningsUiState()
        _state.value = ScreenState.Content(current.copy(actionMessage = message))
    }

    private companion object {
        const val NOT_WIRED = "Payments aren't part of Paperweight OS v1 — no processor, no payouts, just the pricing metadata above."
    }
}
