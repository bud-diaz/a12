package com.paperweight.os.ui.dashboard.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.network.ApiClient
import com.paperweight.os.network.models.UpdateFeedSettingsRequest
import com.paperweight.os.network.models.UpdateGlowColorRequest
import com.paperweight.os.network.models.UpdateNotificationSettingsRequest
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

// Mirrors views/SettingsView.tsx (minus DesktopSection — no Electron-
// equivalent bridge here). No refetchInterval on any query — a one-shot
// load like Schedule/Earnings/Vault, not a poll.
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = ApiClient(application)
    private val _state = MutableStateFlow<ScreenState<SettingsUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<SettingsUiState>> = _state.asStateFlow()

    private var job: Job? = null
    private var docJob: Job? = null

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
                _state.value = ScreenState.Error("Can't reach settings right now.")
            }
        }
    }

    fun notify(message: String) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionMessage = message))
    }

    fun saveNotifications(webhookUrl: String, liveEnabled: Boolean) {
        runAction("Settings saved.") {
            apiClient.settings.updateNotifications(UpdateNotificationSettingsRequest(webhookUrl, liveEnabled))
        }
    }

    fun saveFeed(enabled: Boolean, scope: String) {
        runAction("Settings saved.") {
            apiClient.settings.updateFeed(UpdateFeedSettingsRequest(enabled, scope))
        }
    }

    fun saveGlowColor(color: String) {
        runAction("Settings saved.") {
            apiClient.settings.updateGlowColor(UpdateGlowColorRequest(color))
        }
    }

    fun generateResetLink(email: String) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        val match = current.accounts.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }
        if (match == null) {
            notify("No active listener account with that email.")
            return
        }
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null, resetLinkUrl = null))
        viewModelScope.launch {
            try {
                val result = apiClient.settings.generateResetLink(match.id)
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                if (result.url == null) {
                    _state.value = ScreenState.Content(
                        latest.copy(actionInFlight = false, actionMessage = result.error ?: "Could not generate a reset link.")
                    )
                } else {
                    _state.value = ScreenState.Content(
                        latest.copy(
                            actionInFlight = false,
                            resetLinkUrl = result.url,
                            resetLinkEmail = result.email ?: match.email,
                            resetLinkExpiresAt = result.expiresAt,
                        )
                    )
                }
            } catch (e: HttpException) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = e.serverErrorMessage() ?: "Failed to generate reset link.")
                )
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(actionInFlight = false, actionMessage = "Failed to generate reset link."))
            }
        }
    }

    fun selectDoc(id: String, title: String) {
        docJob?.cancel()
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(
            current.copy(selectedDocId = id, selectedDocTitle = title, selectedDocText = null, selectedDocLoading = true)
        )
        docJob = viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { apiClient.settings.docContent(id).string() }
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                if (latest.selectedDocId == id) {
                    _state.value = ScreenState.Content(latest.copy(selectedDocText = text, selectedDocLoading = false))
                }
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                if (latest.selectedDocId == id) {
                    _state.value = ScreenState.Content(latest.copy(selectedDocText = "Could not load this document.", selectedDocLoading = false))
                }
            }
        }
    }

    fun closeDoc() {
        docJob?.cancel()
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(selectedDocId = null, selectedDocText = null, selectedDocLoading = false))
    }

    private fun runAction(successMessage: String, action: suspend () -> Unit) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null))
        viewModelScope.launch {
            try {
                action()
                val fresh = fetchState()
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.withCoreFrom(fresh).copy(actionInFlight = false, actionMessage = successMessage))
            } catch (e: HttpException) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = e.serverErrorMessage() ?: "Failed to save.")
                )
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(actionInFlight = false, actionMessage = "Failed to save settings."))
            }
        }
    }

    private suspend fun fetchState(): SettingsUiState {
        val settings = apiClient.settings.settings()
        val accounts = apiClient.settings.accounts()
        val docs = apiClient.settings.docsList().docs

        return SettingsUiState(
            notifyWebhookUrl = settings.notifyWebhookUrl,
            notifyLiveEnabled = settings.notifyLiveEnabled,
            feedEnabled = settings.feedEnabled,
            feedScope = settings.feedScope,
            trackGlowColor = settings.trackGlowColor,
            emailConfigured = settings.emailConfigured,
            accounts = accounts,
            docs = docs,
        )
    }

    // errorBody().string() does blocking I/O, so it must not run on Main.
    private suspend fun HttpException.serverErrorMessage(): String? {
        val body = withContext(Dispatchers.IO) { response()?.errorBody()?.string() } ?: return null
        return Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
    }
}
