package com.paperweight.os.ui.dashboard.overview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The remote-backend client (ApiClient/Retrofit) this screen used to poll
// was removed as part of the on-device-backend pivot (see HANDOFF.md).
// Rewiring to BroadcastRepository/local Room data lands in a later phase.
class OverviewViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ScreenState<OverviewUiState>>(ScreenState.Error(NOT_YET_WIRED))
    val state: StateFlow<ScreenState<OverviewUiState>> = _state.asStateFlow()

    fun load() {
        _state.value = ScreenState.Error(NOT_YET_WIRED)
    }

    private companion object {
        const val NOT_YET_WIRED =
            "Overview isn't wired to the on-device backend yet — coming in a later build phase."
    }
}
