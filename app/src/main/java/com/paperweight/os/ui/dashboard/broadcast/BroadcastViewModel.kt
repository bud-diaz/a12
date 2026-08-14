package com.paperweight.os.ui.dashboard.broadcast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.network.ApiClient
import com.paperweight.os.network.models.BroadcastModeRequest
import com.paperweight.os.network.models.BroadcastQueueItem
import com.paperweight.os.network.models.StreamStatus
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BroadcastViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = ApiClient(application)
    private val _state = MutableStateFlow<ScreenState<BroadcastUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<BroadcastUiState>> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        load()
    }

    fun load() {
        pollJob?.cancel()
        _state.value = ScreenState.Loading
        pollJob = viewModelScope.launch {
            try {
                _state.value = ScreenState.Content(fetchState())
            } catch (e: Exception) {
                _state.value = ScreenState.Error("Can't reach broadcast controls right now.")
                return@launch
            }

            while (true) {
                delay(5_000)
                try {
                    val current = (_state.value as? ScreenState.Content)?.data ?: return@launch
                    _state.value = ScreenState.Content(fetchState().copy(actionMessage = current.actionMessage))
                } catch (e: Exception) {
                    // Missed polls should not blank a loaded control surface.
                }
            }
        }
    }

    fun toggleMode() {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        runAction("Rotation mode updated.") {
            apiClient.broadcast.setMode(BroadcastModeRequest(current.alternateMode))
        }
    }

    fun restart() {
        runAction("Broadcast restarted.") {
            apiClient.broadcast.restart()
        }
    }

    fun removeFromQueue(index: Int) {
        runAction("Removed from broadcast queue.") {
            apiClient.broadcast.removeFromQueue(index)
        }
    }

    private fun runAction(successMessage: String, action: suspend () -> Unit) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null))
        viewModelScope.launch {
            try {
                action()
                _state.value = ScreenState.Content(fetchState().copy(actionMessage = successMessage))
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(
                        actionInFlight = false,
                        actionMessage = "That broadcast action didn't go through.",
                    )
                )
            }
        }
    }

    private suspend fun fetchState(): BroadcastUiState {
        val status = apiClient.stream.status()
        val queue = apiClient.broadcast.queue().queue
        return status.toBroadcastUiState(queue)
    }

    private fun StreamStatus.toBroadcastUiState(queue: List<BroadcastQueueItem>) = BroadcastUiState(
        mode = mode,
        nowPlayingTitle = nowPlaying?.title,
        nowPlayingArtist = nowPlaying?.artist,
        liveActive = liveActive,
        listenerCount = listenerCount,
        queue = queue,
        actionInFlight = false,
    )
}
