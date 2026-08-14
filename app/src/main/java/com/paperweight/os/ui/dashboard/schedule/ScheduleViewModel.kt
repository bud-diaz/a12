package com.paperweight.os.ui.dashboard.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.paperweight.os.network.models.ScheduleBlockRequest
import com.paperweight.os.network.models.SmartPlaylistRequest
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The remote-backend client (ApiClient/Retrofit) this screen used to call
// was removed as part of the on-device-backend pivot (see HANDOFF.md).
// Rewiring to a local ScheduleRepository/Room-backed ScheduleBlockEntity and
// SmartPlaylistEntity lands in a later phase.
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ScreenState<ScheduleUiState>>(ScreenState.Error(NOT_YET_WIRED))
    val state: StateFlow<ScreenState<ScheduleUiState>> = _state.asStateFlow()

    fun load() {
        _state.value = ScreenState.Error(NOT_YET_WIRED)
    }

    fun refreshPreview() {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun enableScheduledMode() {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun saveBlock(id: Int?, request: ScheduleBlockRequest) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun deleteBlock(id: Int) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun savePlaylist(id: Int?, request: SmartPlaylistRequest) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun deletePlaylist(id: Int) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    private companion object {
        const val NOT_YET_WIRED =
            "Schedule isn't wired to the on-device backend yet — coming in a later build phase."
    }
}
