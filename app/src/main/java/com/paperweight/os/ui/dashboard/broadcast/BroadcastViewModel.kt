package com.paperweight.os.ui.dashboard.broadcast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.di.ServiceLocator
import com.paperweight.os.network.models.BroadcastQueueItem
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BroadcastViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = ServiceLocator.get(application).broadcastEngine
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
                    ),
                )
            }
        }
    }

    fun toggleMode() = engine.toggleMode()

    fun restart() = engine.restart()

    fun removeFromQueue(index: Int) = engine.removeFromQueue(index)
}
