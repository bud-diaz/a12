package com.paperweight.os.ui.dashboard.vault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.network.ApiClient
import com.paperweight.os.network.models.AddCollectionTrackRequest
import com.paperweight.os.network.models.AssignTokenRequest
import com.paperweight.os.network.models.CreateTokenRequest
import com.paperweight.os.network.models.ReorderCollectionTracksRequest
import com.paperweight.os.network.models.SetHighlightRequest
import com.paperweight.os.network.models.SetTierRequest
import com.paperweight.os.network.models.UpdateCollectionRequest
import com.paperweight.os.network.models.VaultPricingRequest
import com.paperweight.os.network.models.VaultProject
import com.paperweight.os.network.models.VaultTrackPrice
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

// Mirrors views/Vault.tsx. No refetchInterval on any of its queries — a
// one-shot load like Schedule/Earnings, not a poll. "Access control" and
// "Add to vault" (new media upload) open Studio modals with no equivalent
// screen built yet in this app, so they're stubbed as a notice from the
// Screen rather than invented here.
class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = ApiClient(application)
    private val _state = MutableStateFlow<ScreenState<VaultUiState>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<VaultUiState>> = _state.asStateFlow()

    private var job: Job? = null
    private var assignmentsJob: Job? = null

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
                _state.value = ScreenState.Error("Can't reach the vault right now.")
            }
        }
    }

    fun notify(message: String) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionMessage = message))
    }

    fun saveTrackPricing(contentId: Int, request: VaultPricingRequest) {
        runAction("Pricing updated.") { apiClient.vault.updateTrackPricing(contentId, request) }
    }

    fun saveCollectionPricing(project: VaultProject, request: UpdateCollectionRequest) {
        runAction("Pricing updated.") { apiClient.vault.updateCollection(project.id, request) }
    }

    fun deleteCollection(id: Int) {
        runAction("Collection deleted.") { apiClient.vault.deleteCollection(id) }
    }

    fun addCollectionTrack(projectId: Int, contentId: Int) {
        runAction("Track added to collection.") { apiClient.vault.addCollectionTrack(projectId, AddCollectionTrackRequest(contentId)) }
    }

    fun removeCollectionTrack(projectId: Int, contentId: Int) {
        runAction("Track removed from collection.") { apiClient.vault.removeCollectionTrack(projectId, contentId) }
    }

    fun moveCollectionTrack(project: VaultProject, contentId: Int, direction: Int) {
        val ids = project.items.map { it.content_id }.toMutableList()
        val index = ids.indexOf(contentId)
        val nextIndex = index + direction
        if (index < 0 || nextIndex < 0 || nextIndex >= ids.size) return
        val temp = ids[index]
        ids[index] = ids[nextIndex]
        ids[nextIndex] = temp
        runAction("Collection order updated.") { apiClient.vault.reorderCollectionTracks(project.id, ReorderCollectionTracksRequest(ids)) }
    }

    fun toggleHighlight(type: String, id: Int) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        val active = current.highlight.highlight_type == type && current.highlight.highlight_id == id
        runAction("Vault highlight updated.") {
            apiClient.vault.setHighlight(if (active) SetHighlightRequest(null, null) else SetHighlightRequest(type, id))
        }
    }

    fun uploadArtwork(trackId: Int, uri: Uri) {
        runAction("Artwork uploaded.") {
            val contentResolver = getApplication<Application>().contentResolver
            val part = withContext(Dispatchers.IO) {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read image")
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                MultipartBody.Part.createFormData("artwork", "artwork", bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            }
            apiClient.vault.uploadArtwork(trackId, part)
        }
    }

    fun createToken(label: String, tier: String, email: String) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null))
        viewModelScope.launch {
            try {
                val result = apiClient.vault.createToken(CreateTokenRequest(label.trim(), tier))
                if (email.isNotBlank() && result.id != null) {
                    apiClient.vault.assignToken(result.id, AssignTokenRequest(email.trim()))
                }
                val fresh = fetchState()
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.withCoreFrom(fresh).copy(
                        actionInFlight = false,
                        actionMessage = "Token created.",
                        createdToken = result.token,
                    )
                )
            } catch (e: HttpException) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = e.serverErrorMessage() ?: "Failed to create token.")
                )
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(actionInFlight = false, actionMessage = "Failed to create token."))
            }
        }
    }

    fun revokeToken(id: Int) {
        runAction("Token revoked.") { apiClient.vault.revokeToken(id) }
    }

    fun setTokenTier(id: Int, tier: String) {
        runAction("Token tier updated.") { apiClient.vault.setTokenTier(id, SetTierRequest(tier)) }
    }

    fun toggleTokenAssignments(id: Int) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        val nextId = if (current.openTokenId == id) null else id
        _state.value = ScreenState.Content(current.copy(openTokenId = nextId, tokenAssignments = emptyList()))
        if (nextId != null) loadAssignments(nextId)
    }

    private fun loadAssignments(tokenId: Int) {
        assignmentsJob?.cancel()
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        assignmentsJob = viewModelScope.launch {
            try {
                val assignments = apiClient.vault.tokenAssignments(tokenId)
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                if (latest.openTokenId == tokenId) {
                    _state.value = ScreenState.Content(latest.copy(tokenAssignments = assignments))
                }
            } catch (e: Exception) {
                // Best-effort; leave the assignments panel empty on failure.
            }
        }
    }

    fun assignToken(tokenId: Int, email: String) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        if (email.isBlank()) return
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null))
        viewModelScope.launch {
            try {
                apiClient.vault.assignToken(tokenId, AssignTokenRequest(email.trim()))
                val assignments = apiClient.vault.tokenAssignments(tokenId)
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = "Account assigned.", tokenAssignments = assignments)
                )
            } catch (e: HttpException) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = e.serverErrorMessage() ?: "Failed to assign account.")
                )
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(actionInFlight = false, actionMessage = "Failed to assign account."))
            }
        }
    }

    fun unassignToken(tokenId: Int, assignmentId: Int) {
        val current = (_state.value as? ScreenState.Content)?.data ?: return
        _state.value = ScreenState.Content(current.copy(actionInFlight = true, actionMessage = null))
        viewModelScope.launch {
            try {
                apiClient.vault.unassignToken(tokenId, assignmentId)
                val assignments = apiClient.vault.tokenAssignments(tokenId)
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = "Assignment removed.", tokenAssignments = assignments)
                )
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(latest.copy(actionInFlight = false, actionMessage = "Failed to remove assignment."))
            }
        }
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
                    latest.copy(actionInFlight = false, actionMessage = e.serverErrorMessage() ?: "That vault action didn't go through.")
                )
            } catch (e: Exception) {
                val latest = (_state.value as? ScreenState.Content)?.data ?: current
                _state.value = ScreenState.Content(
                    latest.copy(actionInFlight = false, actionMessage = "That vault action didn't go through.")
                )
            }
        }
    }

    private suspend fun fetchState(): VaultUiState {
        val pricing = apiClient.vault.pricing()
        val highlight = apiClient.vault.highlight()
        val structure = apiClient.library.structure()
        val mediaList = apiClient.vault.mediaList()
        val tokens = apiClient.vault.tokens()

        val pricedIds = pricing.trackPrices.map { it.content_id }.toSet()
        val projectTrackIds = pricing.projects.flatMap { it.items.map { item -> item.content_id } }.toSet()
        val unpriced = mediaList
            .filter { it.visibility == "vault" && it.id !in pricedIds && it.id !in projectTrackIds }
            .map {
                VaultTrackPrice(
                    content_id = it.id,
                    title = it.title,
                    filename = it.filename,
                    suggested_price = 0,
                    minimum_price = 0,
                    allow_free = 1,
                    payment_type = "one_time",
                    recurring_interval = null,
                )
            }

        return VaultUiState(
            trackPrices = pricing.trackPrices,
            projects = pricing.projects,
            unpricedVaultTracks = unpriced,
            availableTracks = structure.standalone,
            highlight = highlight,
            tokens = tokens,
        )
    }

    // errorBody().string() does blocking I/O, so it must not run on Main.
    private suspend fun HttpException.serverErrorMessage(): String? {
        val body = withContext(Dispatchers.IO) { response()?.errorBody()?.string() } ?: return null
        return Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
    }
}
