package com.paperweight.os.ui.dashboard.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.network.ApiClient
import com.paperweight.os.network.models.BroadcastModeRequest
import com.paperweight.os.network.models.ScheduleBlockRequest
import com.paperweight.os.network.models.SmartPlaylistRequest
import com.paperweight.os.ui.components.ScreenState
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = ApiClient(application)
    private val _state = MutableStateFlow<ScreenState<ScheduleUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<ScheduleUiState>> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    // Unlike Overview/Broadcast, none of Studio's three schedule queries
    // declare a refetchInterval — this screen reloads on mutation and via
    // the explicit "Refresh preview" action, not a 5s poll.
    fun load() {
        job?.cancel()
        _state.value = ScreenState.Loading
        job = viewModelScope.launch {
            try {
                _state.value = ScreenState.Content(fetchState())
            } catch (e: Exception) {
                _state.value = ScreenState.Error("Can't reach schedule right now.")
            }
        }
    }

    fun refreshPreview() {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        viewModelScope.launch {
            try {
                val segments = apiClient.schedule.preview(nowIso(), 24).segments
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(previewSegments = segments))
            } catch (e: Exception) {
                // Best-effort refresh; leave the existing segments in place.
            }
        }
    }

    fun enableScheduledMode() {
        runAction("Broadcast mode updated.") {
            apiClient.broadcast.setMode(BroadcastModeRequest("scheduled"))
        }
    }

    fun saveBlock(id: Int?, request: ScheduleBlockRequest) {
        runAction(if (id != null) "Schedule block updated." else "Schedule block created.") {
            if (id != null) apiClient.schedule.updateBlock(id, request) else apiClient.schedule.createBlock(request)
        }
    }

    fun deleteBlock(id: Int) {
        runAction("Schedule block deleted.") {
            apiClient.schedule.deleteBlock(id)
        }
    }

    fun savePlaylist(id: Int?, request: SmartPlaylistRequest) {
        runAction(if (id != null) "Smart playlist updated." else "Smart playlist created.") {
            if (id != null) apiClient.schedule.updateSmartPlaylist(id, request) else apiClient.schedule.createSmartPlaylist(request)
        }
    }

    fun deletePlaylist(id: Int) {
        runAction("Smart playlist deleted.") {
            apiClient.schedule.deleteSmartPlaylist(id)
        }
    }

    private fun runAction(successMessage: String, action: suspend () -> Unit) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null))
        viewModelScope.launch {
            try {
                action()
                _state.value = ScreenState.Content(fetchState().copy(actionMessage = successMessage))
            } catch (e: HttpException) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(
                        actionInFlight = false,
                        actionMessage = e.desktopGateMessage() ?: "That schedule action didn't go through.",
                    )
                )
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = "That schedule action didn't go through.")
                )
            }
        }
    }

    private suspend fun fetchState(): ScheduleUiState {
        val blocks = apiClient.schedule.blocks()
        val playlists = apiClient.schedule.smartPlaylists()
        val preview = apiClient.schedule.preview(nowIso(), 24)
        return ScheduleUiState(
            blocks = blocks,
            playlists = playlists,
            previewSegments = preview.segments,
            actionInFlight = false,
        )
    }

    private fun nowIso(): String = Instant.now().toString()

    // Block/playlist mutations are desktop-platform gated on some
    // deployments (403 "This feature is available in the Paperweight
    // desktop app.") — surface the server's real message when that happens.
    // errorBody().string() does blocking I/O, so it must not run on Main.
    private suspend fun HttpException.desktopGateMessage(): String? {
        if (code() != 403) return null
        val body = withContext(Dispatchers.IO) { response()?.errorBody()?.string() } ?: return null
        return Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
    }
}
