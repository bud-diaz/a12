package com.paperweight.os.ui.dashboard.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The remote-backend client (ApiClient/Retrofit) this screen used to call
// was removed as part of the on-device-backend pivot (see HANDOFF.md). Per
// the scope cut, webhook/feed/listener-account-recovery/docs-viewer are
// being dropped once this screen is rewired around local server/frp/backup
// configuration instead. All methods below are kept as no-ops for now
// purely so SettingsScreen.kt keeps compiling.
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ScreenState<SettingsUiState>>(ScreenState.Error(NOT_YET_WIRED))
    val state: StateFlow<ScreenState<SettingsUiState>> = _state.asStateFlow()

    fun load() {
        _state.value = ScreenState.Error(NOT_YET_WIRED)
    }

    fun notify(message: String) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun saveNotifications(webhookUrl: String, liveEnabled: Boolean) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun saveFeed(enabled: Boolean, scope: String) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun saveGlowColor(color: String) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun generateResetLink(email: String) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun selectDoc(id: String, title: String) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun closeDoc() {
        // Dropped once this screen is rewired — see class doc.
    }

    private companion object {
        const val NOT_YET_WIRED =
            "Settings isn't wired to the on-device backend yet — coming in a later build phase."
    }
}
