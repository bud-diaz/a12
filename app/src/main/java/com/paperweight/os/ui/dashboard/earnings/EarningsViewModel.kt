package com.paperweight.os.ui.dashboard.earnings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.network.ApiClient
import com.paperweight.os.network.models.TipConfig
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Mirrors views/Earnings.tsx — no refetchInterval declared, a one-shot load
// per visit like Schedule, not a poll.
class EarningsViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = ApiClient(application)
    private val _state = MutableStateFlow<ScreenState<EarningsUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<EarningsUiState>> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.value = ScreenState.Loading
        job = viewModelScope.launch {
            try {
                _state.value = ScreenState.Content(fetchState())
            } catch (e: Exception) {
                _state.value = ScreenState.Error("Can't reach earnings right now.")
            }
        }
    }

    // Studio's "Payment settings" button opens the account/payout section of
    // its Settings modal — there's no shared cross-screen modal system here,
    // so this just points the user at the (separately built) Settings screen.
    fun openPaymentSettings() {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionMessage = "Manage payout details from Settings."))
    }

    fun saveTipConfig(config: TipConfig) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null))
        viewModelScope.launch {
            try {
                apiClient.earnings.updateTipConfig(config)
                _state.value = ScreenState.Content(fetchState().copy(actionMessage = "Tip presets saved."))
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = "Failed to save tip presets.")
                )
            }
        }
    }

    private suspend fun fetchState(): EarningsUiState {
        val earnings = apiClient.earnings.earnings()
        val tipConfig = apiClient.earnings.tipConfig()
        return EarningsUiState(
            totals = earnings.totals,
            unlocks = earnings.unlocks,
            tipsCount = earnings.tips.count,
            tipsGrossCents = earnings.tips.grossCents,
            subscriptions = earnings.subscriptions,
            tipConfig = tipConfig,
        )
    }
}
