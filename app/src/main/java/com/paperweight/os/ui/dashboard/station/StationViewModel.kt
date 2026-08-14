package com.paperweight.os.ui.dashboard.station

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.network.ApiClient
import com.paperweight.os.network.models.AutoCreateTunnelRequest
import com.paperweight.os.network.models.SaveCloudflareTokenRequest
import com.paperweight.os.network.models.SaveTelemetrySecretRequest
import com.paperweight.os.network.models.SetSearchableRequest
import com.paperweight.os.network.models.SignupRequest
import com.paperweight.os.network.models.StationMutationResponse
import com.paperweight.os.network.models.UpdateStationUrlRequest
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

// Mirrors views/Station.tsx. Only the Cloudflare tunnel status query polls
// (5s, matching Studio's refetchInterval), and only when a tunnel is
// actually configured/managed — everything else loads once per visit.
class StationViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = ApiClient(application)
    private val _state = MutableStateFlow<ScreenState<StationUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<StationUiState>> = _state.asStateFlow()

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
                _state.value = ScreenState.Error("Can't reach station settings right now.")
                return@launch
            }

            while (true) {
                delay(5_000)
                val current = (_state.value as? ScreenState.Content)?.data ?: return@launch
                if (!current.cloudflareApiConfigured && !current.cloudflareTunnelManaged) continue
                try {
                    val status = apiClient.station.tunnelStatus()
                    val latest = (_state.value as? ScreenState.Content)?.data ?: return@launch
                    _state.value = ScreenState.Content(latest.copy(tunnelStatus = status))
                } catch (e: Exception) {
                    // Missed polls shouldn't blank an already-loaded screen.
                }
            }
        }
    }

    fun refetchHealth() {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(healthChecking = true))
        viewModelScope.launch {
            try {
                val health = apiClient.station.health()
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(health = health, healthChecking = false))
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(healthChecking = false))
            }
        }
    }

    fun notify(message: String) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionMessage = message))
    }

    fun updateUrl(url: String) {
        runAction("Public URL updated.") { apiClient.station.updateUrl(UpdateStationUrlRequest(url.trim())) }
    }

    // The web UI's onSuccess additionally lists which readiness checks
    // failed and re-runs the health check on failure — the itemized check
    // list is skipped here (would need the checks map parsed out of the
    // raw error body), but the health recheck is cheap to keep.
    fun toggleSearchable(enabled: Boolean) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null))
        viewModelScope.launch {
            try {
                apiClient.station.setSearchable(SetSearchableRequest(enabled))
                val fresh = fetchState()
                _state.value = ScreenState.Content(fresh.copy(actionInFlight = false, actionMessage = "Directory searchability updated."))
            } catch (e: HttpException) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = e.serverErrorMessage() ?: "Could not update searchability.")
                )
                refetchHealth()
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(actionInFlight = false, actionMessage = "Could not update searchability."))
            }
        }
    }

    fun saveCloudflareToken(token: String) {
        runAction("Cloudflare token saved and verified.") { apiClient.station.saveCloudflareToken(SaveCloudflareTokenRequest(token.trim())) }
    }

    fun autoCreateTunnel(zoneId: String, hostname: String) {
        runAction("Tunnel created. Connector is starting.") {
            apiClient.station.autoCreateTunnel(AutoCreateTunnelRequest(zoneId, hostname.trim()))
        }
    }

    fun toggleTunnel() {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        val message = if (current.cloudflareTunnelPaused) "Tunnel reconnected." else "Tunnel disconnected."
        runAction(message) {
            if (current.cloudflareTunnelPaused) apiClient.station.tunnelConnect() else apiClient.station.tunnelDisconnect()
        }
    }

    fun saveTelemetrySecret(secret: String) {
        runAction("Telemetry secret saved.") { apiClient.station.saveTelemetrySecret(SaveTelemetrySecretRequest(secret.trim())) }
    }

    fun registerTelemetry() {
        runAction("Registered with PaperweightHQ.") { apiClient.station.registerTelemetry() }
    }

    fun createHqTunnel() {
        runAction("PaperweightHQ tunnel created. Connector is starting.") { apiClient.station.createHqTunnel() }
    }

    fun signup(email: String) {
        runAction("Thanks, you are signed up.") { apiClient.station.signup(SignupRequest(email.trim(), true)) }
    }

    fun dismissSignup() {
        runAction("Signup prompt dismissed.") { apiClient.station.dismissSignup() }
    }

    private fun runAction(defaultMessage: String, action: suspend () -> StationMutationResponse) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null))
        viewModelScope.launch {
            try {
                val result = action()
                val fresh = fetchState()
                _state.value = ScreenState.Content(fresh.copy(actionInFlight = false, actionMessage = result.note ?: defaultMessage))
            } catch (e: HttpException) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = e.serverErrorMessage() ?: "Station update failed.")
                )
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(actionInFlight = false, actionMessage = "Station update failed."))
            }
        }
    }

    private suspend fun fetchState(): StationUiState {
        val station = apiClient.station.station()
        val setup = apiClient.station.setupProgress()
        val health = if (!station.url.isNullOrBlank()) {
            try {
                apiClient.station.health()
            } catch (e: Exception) {
                null
            }
        } else null
        val zones = if (station.cloudflareApiConfigured) {
            try {
                apiClient.station.cloudflareZones().zones
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
        val tunnelStatus = if (station.cloudflareApiConfigured || station.cloudflareTunnelManaged) {
            try {
                apiClient.station.tunnelStatus()
            } catch (e: Exception) {
                null
            }
        } else null

        return StationUiState(
            slug = station.slug,
            url = station.url,
            searchable = station.searchable,
            telemetryConfigured = station.telemetryConfigured,
            paperweighthqTunnelAvailable = station.paperweighthqTunnelAvailable,
            cloudflareTunnelPaused = station.cloudflareTunnelPaused,
            cloudflareApiConfigured = station.cloudflareApiConfigured,
            cloudflareTunnelManaged = station.cloudflareTunnelManaged,
            requirements = station.requirements,
            health = health,
            zones = zones,
            tunnelStatus = tunnelStatus,
            setupMilestones = setup.milestones,
            signupDismissed = setup.signupDismissed,
        )
    }

    // errorBody().string() does blocking I/O, so it must not run on Main.
    private suspend fun HttpException.serverErrorMessage(): String? {
        val body = withContext(Dispatchers.IO) { response()?.errorBody()?.string() } ?: return null
        return Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
    }
}
