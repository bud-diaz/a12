package com.paperweight.os.ui.dashboard.audience

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.network.ApiClient
import com.paperweight.os.network.models.CreatePollRequest
import com.paperweight.os.network.models.ExternalSearchItem
import com.paperweight.os.network.models.ImportExternalRequest
import com.paperweight.os.network.models.Poll
import com.paperweight.os.network.models.PauseAutomationsRequest
import com.paperweight.os.network.models.RadioHostStatus
import com.paperweight.os.network.models.SetPollStatusRequest
import com.paperweight.os.network.models.UpdateRequestStatusRequest
import com.paperweight.os.network.models.UpdateRuleEnabledRequest
import com.paperweight.os.network.models.UpdateRuleModeRequest
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

// Mirrors views/AudienceView.tsx — none of its ~9 queries declare a
// refetchInterval, so this is a one-shot load like Schedule/Analytics'
// non-live queries, not a poll. People search and external search are
// independently-managed side fetches, not part of the main load/mutation
// refresh cycle (see AudienceUiState.withCoreFrom).
class AudienceViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = ApiClient(application)
    private val _state = MutableStateFlow<ScreenState<AudienceUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<AudienceUiState>> = _state.asStateFlow()

    private var job: Job? = null
    private var peopleJob: Job? = null

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
                _state.value = ScreenState.Error("Can't reach audience tools right now.")
            }
        }
    }

    fun loadPeople(search: String, segmentKey: String?) {
        peopleJob?.cancel()
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        peopleJob = viewModelScope.launch {
            try {
                val response = if (!segmentKey.isNullOrEmpty()) {
                    apiClient.audience.peopleInSegment(segmentKey)
                } else {
                    apiClient.audience.people(search)
                }
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(people = response.people))
            } catch (e: Exception) {
                // Best-effort; leave the existing people list in place.
            }
        }
    }

    fun runExternalSearch(platform: String, query: String) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        if (query.isBlank()) return
        _state.value = ScreenState.Content(current.copy(searchingExternal = true))
        viewModelScope.launch {
            try {
                val response = apiClient.audience.externalSearch(platform, query)
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(externalResults = response.items, searchingExternal = false))
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(searchingExternal = false, actionMessage = "External search failed."))
            }
        }
    }

    fun importExternal(item: ExternalSearchItem) {
        runAction {
            apiClient.audience.importExternal(
                ImportExternalRequest(
                    title = item.title,
                    artist = item.artist,
                    platform = item.platform,
                    externalUrl = item.externalUrl,
                    duration = item.duration,
                )
            )
            "External track imported."
        }
    }

    fun toggleAutomationsPaused(paused: Boolean) {
        runAction {
            apiClient.audience.pauseAutomations(PauseAutomationsRequest(paused))
            "Automation state updated."
        }
    }

    fun setRuleEnabled(id: Int, enabled: Boolean) {
        runAction {
            apiClient.audience.setRuleEnabled(id, UpdateRuleEnabledRequest(enabled))
            null
        }
    }

    fun setRuleMode(id: Int, mode: String) {
        runAction {
            apiClient.audience.setRuleMode(id, UpdateRuleModeRequest(mode))
            null
        }
    }

    fun sendAutomationRun(id: Int) {
        runAction {
            apiClient.audience.sendAutomationRun(id)
            "Automation delivery queued."
        }
    }

    fun sweepAutomations() {
        runAction {
            val result = apiClient.audience.sweepAutomations()
            "${result.created} recommendations created."
        }
    }

    fun createPoll(question: String, options: List<String>) {
        runAction {
            apiClient.audience.createPoll(CreatePollRequest(question.trim(), options))
            "Poll created."
        }
    }

    fun togglePollStatus(poll: Poll) {
        runAction {
            apiClient.audience.setPollStatus(poll.id, SetPollStatusRequest(if (poll.status == "open") "closed" else "open"))
            null
        }
    }

    fun updateRequestStatus(id: Int, status: String) {
        runAction {
            apiClient.audience.updateRequestStatus(id, UpdateRequestStatusRequest(status))
            "Request updated."
        }
    }

    fun toggleRadioHost() {
        runAction {
            val result = apiClient.audience.toggleRadioHost()
            if (result.radioHost) "Radio host mode on." else "Radio host mode off."
        }
    }

    private fun runAction(action: suspend () -> String?) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null))
        viewModelScope.launch {
            try {
                val message = action()
                val fresh = fetchState()
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.withCoreFrom(fresh).copy(actionInFlight = false, actionMessage = message)
                )
            } catch (e: HttpException) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = e.serverErrorMessage() ?: "That action didn't go through.")
                )
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = "That action didn't go through.")
                )
            }
        }
    }

    private suspend fun fetchState(): AudienceUiState {
        val today = apiClient.audience.today()
        val segments = apiClient.audience.segments().segments
        val people = apiClient.audience.people("").people
        val contacts = apiClient.audience.marketingContacts()
        val automations = apiClient.audience.automations()
        val polls = apiClient.audience.polls().polls
        val requests = apiClient.audience.requests().requests
        val creatorType = apiClient.audience.creatorType().creatorType
        // radio-host is desktop-platform gated; a 403 there shouldn't sink
        // the rest of this screen, matching react-query's per-query isolation.
        val radioHost = try {
            apiClient.audience.radioHostStatus()
        } catch (e: Exception) {
            RadioHostStatus(locked = true)
        }

        return AudienceUiState(
            outcomes = today.outcomes,
            insights = today.insights,
            segments = segments,
            people = people,
            contacts = contacts,
            automations = automations,
            polls = polls,
            requests = requests,
            creatorType = creatorType,
            radioHost = radioHost,
        )
    }

    // errorBody().string() does blocking I/O, so it must not run on Main.
    private suspend fun HttpException.serverErrorMessage(): String? {
        val body = withContext(Dispatchers.IO) { response()?.errorBody()?.string() } ?: return null
        return Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
    }
}
