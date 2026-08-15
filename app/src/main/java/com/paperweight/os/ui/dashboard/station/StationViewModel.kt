package com.paperweight.os.ui.dashboard.station

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.di.ServiceLocator
import com.paperweight.os.reachability.TunnelStatus
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// Rewired for Phase 9 (frp reachability). The old Retrofit-era screen
// (Cloudflare tunnel setup, PaperweightHQ telemetry secret paste, directory
// searchability, setup-progress checklist, product-updates signup) is gone —
// dropped per the plan's scope cut and the Phase 9 build-order description
// ("rewire Station: LAN URL, public frp-tunneled URL, QR codes, tunnel
// connection status"), not just trimmed. See HANDOFF.md for the frp
// registration contract this was built against.
class StationViewModel(application: Application) : AndroidViewModel(application) {
    private val services = ServiceLocator.get(application)

    private val _state = MutableStateFlow<ScreenState<StationUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<StationUiState>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            combine(
                services.appPreferences.stationName,
                services.appPreferences.stationSlug,
                services.stationRepository.observeProfile(),
                services.reachabilityRepository.status,
            ) { stationName, slug, profile, tunnelStatus ->
                val current = (_state.value as? ScreenState.Content)?.data
                StationUiState(
                    stationName = stationName,
                    slug = slug ?: "",
                    lanUrl = profile?.lanUrl,
                    publicUrl = (tunnelStatus as? TunnelStatus.Connected)?.publicUrl ?: profile?.publicUrl,
                    tunnelStatusText = tunnelStatusText(tunnelStatus),
                    tunnelError = (tunnelStatus as? TunnelStatus.Error)?.message,
                    lastReachableAt = profile?.lastReachableAt,
                    actionMessage = current?.actionMessage,
                    actionInFlight = current?.actionInFlight ?: false,
                )
            }.collect { _state.value = ScreenState.Content(it) }
        }
    }

    fun register(slug: String) {
        val cleanSlug = normalizeSlug(slug)
        if (cleanSlug == null) {
            mutate { copy(actionMessage = "Slug must be lowercase letters, numbers, and hyphens only.") }
            return
        }
        viewModelScope.launch {
            mutate { copy(actionInFlight = true, actionMessage = "Registering…") }
            val result = services.reachabilityRepository.register(cleanSlug)
            result.fold(
                onSuccess = { publicUrl -> mutate { copy(actionInFlight = false, actionMessage = "Registered. Public URL: $publicUrl") } },
                onFailure = { error -> mutate { copy(actionInFlight = false, actionMessage = error.message ?: "Registration failed.") } },
            )
        }
    }

    fun disconnect() {
        services.reachabilityRepository.disconnect()
        mutate { copy(actionMessage = "Tunnel disconnected.") }
    }

    fun notify(message: String) {
        mutate { copy(actionMessage = message) }
    }

    private fun mutate(block: StationUiState.() -> StationUiState) {
        val current = (_state.value as? ScreenState.Content)?.data ?: StationUiState()
        _state.value = ScreenState.Content(current.block())
    }

    private fun tunnelStatusText(status: TunnelStatus): String = when (status) {
        is TunnelStatus.Stopped -> "Not connected"
        is TunnelStatus.Connecting -> "Connecting…"
        is TunnelStatus.Connected -> "Connected"
        is TunnelStatus.Error -> "Error"
    }

    // system.pape enforces the real reserved-word/profanity blocklist server-side
    // (see FrpRegistrationClient's 409 handling) — this only rejects obviously
    // malformed input before spending a network round trip on it.
    private fun normalizeSlug(raw: String): String? {
        val cleaned = raw.trim().lowercase()
        return cleaned.takeIf { SLUG_PATTERN.matches(it) }
    }

    private companion object {
        val SLUG_PATTERN = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    }
}
