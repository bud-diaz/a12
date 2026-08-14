package com.paperweight.os.ui.dashboard.overview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.network.ApiClient
import com.paperweight.os.network.models.LibraryStructure
import com.paperweight.os.network.models.StreamStatus
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Mirrors views/Overview.tsx: stream/status polls every 5s (matching
// Studio's refetchInterval), everything else loads once per screen visit.
class OverviewViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = ApiClient(application)
    private val _state = MutableStateFlow<ScreenState<OverviewUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<OverviewUiState>> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun load() {
        job?.cancel()
        _state.value = ScreenState.Loading
        job = viewModelScope.launch {
            try {
                val status = apiClient.stream.status()
                val structure = apiClient.library.structure()
                val history = apiClient.analytics.history(30)
                val earnings = apiClient.earnings.earnings()
                val activity = apiClient.analytics.activity(3)

                _state.value = ScreenState.Content(
                    OverviewUiState(
                        stationLabel = statusLabel(status),
                        listenerCount = status.listenerCount,
                        nowPlayingTitle = status.nowPlaying?.title,
                        catalogCount = catalogCount(structure),
                        collectionsCount = structure.projects.size,
                        listeningHours = history.sumOf { it.total_listen_sec } / 3600.0,
                        monthRevenueCents = earnings.totals.monthRevenueCents,
                        weekHistory = history.takeLast(14),
                        recentActivity = activity,
                    )
                )
            } catch (e: Exception) {
                _state.value = ScreenState.Error("Can't reach your station right now.")
                return@launch
            }

            while (true) {
                delay(5_000)
                val current = (_state.value as? ScreenState.Content)?.data ?: return@launch
                try {
                    val status = apiClient.stream.status()
                    _state.value = ScreenState.Content(
                        current.copy(
                            stationLabel = statusLabel(status),
                            listenerCount = status.listenerCount,
                            nowPlayingTitle = status.nowPlaying?.title,
                        )
                    )
                } catch (e: Exception) {
                    // A single missed poll shouldn't blank an already-loaded screen;
                    // the next tick will pick back up. No retry queue, no cache file.
                }
            }
        }
    }

    private fun statusLabel(status: StreamStatus): String = when {
        status.liveActive -> "Live now"
        status.nowPlaying != null -> "Playing now"
        else -> "Station idle"
    }

    private fun catalogCount(structure: LibraryStructure): Int =
        structure.standalone.size + structure.projects.sumOf { it.tracks.size }
}
