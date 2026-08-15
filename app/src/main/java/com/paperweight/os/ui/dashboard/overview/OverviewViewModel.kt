package com.paperweight.os.ui.dashboard.overview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.di.ServiceLocator
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OverviewViewModel(application: Application) : AndroidViewModel(application) {
    private val services = ServiceLocator.get(application)
    private val _state = MutableStateFlow<ScreenState<OverviewUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<OverviewUiState>> = _state.asStateFlow()

    init { load() }

    fun load() {
        services.broadcastEngine.start()
        viewModelScope.launch {
            services.broadcastEngine.state.collect { broadcast ->
                _state.value = ScreenState.Content(
                    OverviewUiState(
                        stationLabel = "Paperweight Station",
                        listenerCount = broadcast.listenerCount,
                        nowPlayingTitle = broadcast.nowPlayingTitle,
                        catalogCount = broadcast.queue.size,
                        collectionsCount = 0,
                        listeningHours = 0.0,
                        monthRevenueCents = 0,
                        weekHistory = emptyList(),
                        recentActivity = emptyList(),
                    ),
                )
            }
        }
    }
}
