package com.paperweight.os.ui.dashboard.broadcast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.broadcast.ValidationBroadcastSeeder
import com.paperweight.os.debug.DebugBuild
import com.paperweight.os.di.ServiceLocator
import com.paperweight.os.network.models.BroadcastQueueItem
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BroadcastViewModel(application: Application) : AndroidViewModel(application) {
    private val services = ServiceLocator.get(application)
    private val engine = services.broadcastEngine
    private val validationSeeder = ValidationBroadcastSeeder(application, services.vaultRepository)
    private val validationToneAvailable = DebugBuild.isDebuggable(application)
    private val _state = MutableStateFlow<ScreenState<BroadcastUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<BroadcastUiState>> = _state.asStateFlow()

    init { load() }

    fun load() {
        engine.start()
        viewModelScope.launch {
            engine.state.collect { broadcast ->
                _state.value = ScreenState.Content(
                    BroadcastUiState(
                        mode = broadcast.mode,
                        nowPlayingTitle = broadcast.nowPlayingTitle,
                        nowPlayingArtist = broadcast.nowPlayingArtist,
                        liveActive = broadcast.isRunning,
                        listenerCount = broadcast.listenerCount,
                        queue = broadcast.queue.mapIndexed { index, item ->
                            BroadcastQueueItem(id = index + 1, title = item.title, artist = item.artist)
                        },
                        actionMessage = broadcast.actionMessage,
                        actionInFlight = false,
                        validationToneAvailable = validationToneAvailable,
                    ),
                )
            }
        }
    }

    fun toggleMode() = engine.toggleMode()

    fun restart() = engine.restart()

    fun removeFromQueue(index: Int) = engine.removeFromQueue(index)

    fun seedValidationTone() {
        viewModelScope.launch {
            updateCurrentState(
                actionInFlight = true,
                actionMessage = "Generating Phase 5 validation tone…",
            )
            validationSeeder.seedValidationTone().fold(
                onSuccess = {
                    engine.restart()
                    updateCurrentState(
                        actionInFlight = false,
                        actionMessage = "Validation tone added. Wait a few seconds, then open /live/playlist.m3u8 or press Play from another device.",
                    )
                },
                onFailure = { error ->
                    updateCurrentState(
                        actionInFlight = false,
                        actionMessage = "Validation tone failed: ${error.message ?: error::class.java.simpleName}",
                    )
                },
            )
        }
    }

    private fun updateCurrentState(actionInFlight: Boolean, actionMessage: String) {
        val current = (_state.value as? ScreenState.Content)?.data ?: BroadcastUiState()
        _state.value = ScreenState.Content(
            current.copy(
                actionInFlight = actionInFlight,
                actionMessage = actionMessage,
                validationToneAvailable = validationToneAvailable,
            ),
        )
    }
}
