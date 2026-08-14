package com.paperweight.os.ui.dashboard.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.network.ApiClient
import com.paperweight.os.network.models.LibraryStructure
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Mirrors views/Analytics.tsx: only the `live` query declares a
// refetchInterval (10s) — history/top/subscribers/playcounts/catalog load
// once per visit, unlike Overview/Broadcast's blanket 5s poll.
class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = ApiClient(application)
    private val _state = MutableStateFlow<ScreenState<AnalyticsUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<AnalyticsUiState>> = _state.asStateFlow()

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
                _state.value = ScreenState.Error("Can't reach analytics right now.")
                return@launch
            }

            while (true) {
                delay(10_000)
                try {
                    val live = apiClient.analytics.live()
                    val current = (_state.value as? ScreenState.Content)?.data ?: return@launch
                    _state.value = ScreenState.Content(
                        current.copy(currentListeners = live.currentListeners, peakToday = live.peakToday)
                    )
                } catch (e: Exception) {
                    // Missed polls shouldn't blank an already-loaded screen.
                }
            }
        }
    }

    // Studio's export button is itself a stub ("Analytics export is wired
    // in a later pass.") — ported as-is, not a real report generator.
    fun exportReport() {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(exportMessage = "Analytics export is wired in a later pass."))
    }

    private suspend fun fetchState(): AnalyticsUiState {
        val live = apiClient.analytics.live()
        val history = apiClient.analytics.history(30)
        val top = apiClient.analytics.top(limit = 6, period = "7d")
        val subscribers = apiClient.analytics.subscribers(30)
        val playcounts = apiClient.analytics.playcounts()
        val structure = apiClient.library.structure()

        return AnalyticsUiState(
            currentListeners = live.currentListeners,
            peakToday = live.peakToday,
            activeSubscribers = subscribers.activeTotal,
            newSubscribersInRange = subscribers.rows.sumOf { it.new_subscribers },
            totalListenersRange = history.sumOf { it.unique_listeners },
            history = history,
            subscriberRows = subscribers.rows,
            topTracks = top,
            allTimeTracks = allTimeTracks(structure, playcounts),
        )
    }

    private fun allTimeTracks(structure: LibraryStructure, playcounts: Map<String, Int>): List<AllTimeTrack> {
        val allTracks = structure.projects.flatMap { it.tracks } + structure.standalone
        return allTracks
            .map { track ->
                AllTimeTrack(
                    id = track.id,
                    title = track.title ?: "Untitled",
                    artist = track.artist,
                    plays = playcounts[track.id.toString()] ?: 0,
                    durationSeconds = track.duration,
                )
            }
            .filter { it.plays > 0 }
            .sortedByDescending { it.plays }
            .take(20)
    }
}
