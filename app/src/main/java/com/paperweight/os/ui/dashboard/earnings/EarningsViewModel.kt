package com.paperweight.os.ui.dashboard.earnings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.paperweight.os.network.models.TipConfig
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The remote-backend client (ApiClient/Retrofit) this screen used to call
// was removed as part of the on-device-backend pivot (see HANDOFF.md).
// Per the plan, payments/tips are deferred entirely for v1 — no processor,
// no webhooks, no local IOU ledger. This screen becomes a static "coming
// soon" empty state once it's properly rewired (see the Earnings-deferral
// phase); it's a plain error stub for now purely to keep the build green.
class EarningsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ScreenState<EarningsUiState>>(ScreenState.Error(NOT_YET_WIRED))
    val state: StateFlow<ScreenState<EarningsUiState>> = _state.asStateFlow()

    fun load() {
        _state.value = ScreenState.Error(NOT_YET_WIRED)
    }

    fun openPaymentSettings() {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun saveTipConfig(config: TipConfig) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    private companion object {
        const val NOT_YET_WIRED =
            "Earnings/tips are deferred for this build — payments aren't part of v1 yet."
    }
}
