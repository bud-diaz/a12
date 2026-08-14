package com.paperweight.os.ui.dashboard.audience

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.paperweight.os.network.models.ExternalSearchItem
import com.paperweight.os.network.models.Poll
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The remote-backend client (ApiClient/Retrofit) this screen used to call
// was removed as part of the on-device-backend pivot (see HANDOFF.md). Per
// the scope cut, automations/marketing/polls/participation/external-search/
// radio-host are being dropped entirely (no infra to support them on a
// standalone device) — this screen is being rebuilt around local
// today/insights + a listener list once it's rewired. All methods below are
// kept as no-ops for now purely so AudienceScreen.kt keeps compiling.
class AudienceViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ScreenState<AudienceUiState>>(ScreenState.Error(NOT_YET_WIRED))
    val state: StateFlow<ScreenState<AudienceUiState>> = _state.asStateFlow()

    fun load() {
        _state.value = ScreenState.Error(NOT_YET_WIRED)
    }

    fun loadPeople(search: String, segmentKey: String?) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun runExternalSearch(platform: String, query: String) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun importExternal(item: ExternalSearchItem) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun toggleAutomationsPaused(paused: Boolean) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun setRuleEnabled(id: Int, enabled: Boolean) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun setRuleMode(id: Int, mode: String) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun sendAutomationRun(id: Int) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun sweepAutomations() {
        // Dropped once this screen is rewired — see class doc.
    }

    fun createPoll(question: String, options: List<String>) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun togglePollStatus(poll: Poll) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun updateRequestStatus(id: Int, status: String) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun toggleRadioHost() {
        // Dropped once this screen is rewired — see class doc.
    }

    private companion object {
        const val NOT_YET_WIRED =
            "Audience isn't wired to the on-device backend yet — coming in a later build phase."
    }
}
