package com.paperweight.os.ui.dashboard.station

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The remote-backend client (ApiClient/Retrofit) this screen used to call
// was removed as part of the on-device-backend pivot (see HANDOFF.md). Per
// the scope cut, the Cloudflare-tunnel-specific UI here (saveCloudflareToken/
// autoCreateTunnel/toggleTunnel) is being dropped entirely once this screen
// is properly rewired; the PaperweightHQ telemetry/tunnel surface
// (saveTelemetrySecret/registerTelemetry/createHqTunnel) is being repurposed
// for the frp-based reachability mechanism paperweightv1 itself now uses.
// That rewiring — plus new LAN URL/QR-code display — lands together in the
// reachability phase, run locally where paperweightv1's actual registration
// contract can be read directly (see the plan's open item). All methods
// below are kept as no-ops for now purely so StationScreen.kt keeps
// compiling until that rewrite happens.
class StationViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ScreenState<StationUiState>>(ScreenState.Error(NOT_YET_WIRED))
    val state: StateFlow<ScreenState<StationUiState>> = _state.asStateFlow()

    fun load() {
        _state.value = ScreenState.Error(NOT_YET_WIRED)
    }

    fun refetchHealth() {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun notify(message: String) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun updateUrl(url: String) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun toggleSearchable(enabled: Boolean) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun saveCloudflareToken(token: String) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun autoCreateTunnel(zoneId: String, hostname: String) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun toggleTunnel() {
        // Dropped once this screen is rewired — see class doc.
    }

    fun saveTelemetrySecret(secret: String) {
        // Repurposed for frp registration once this screen is rewired — see class doc.
    }

    fun registerTelemetry() {
        // Repurposed for frp registration once this screen is rewired — see class doc.
    }

    fun createHqTunnel() {
        // Repurposed for frp registration once this screen is rewired — see class doc.
    }

    fun signup(email: String) {
        // Dropped once this screen is rewired — see class doc.
    }

    fun dismissSignup() {
        // Dropped once this screen is rewired — see class doc.
    }

    private companion object {
        const val NOT_YET_WIRED =
            "Station isn't wired to the on-device backend yet — this screen is being " +
                "rebuilt around local frp reachability status in a later build phase."
    }
}
