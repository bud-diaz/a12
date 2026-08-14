package com.paperweight.os.ui.dashboard.vault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperweight.os.di.ServiceLocator
import com.paperweight.os.network.models.UpdateCollectionRequest
import com.paperweight.os.network.models.VaultPricingRequest
import com.paperweight.os.network.models.VaultProject
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// The remote-backend client (ApiClient/Retrofit) this screen used to call
// was removed as part of the on-device-backend pivot (see HANDOFF.md).
// Phase 2 wires up local ingestion ("Add to vault": SAF picker ->
// VaultIngestor -> Room) and pricing edits for locally ingested tracks.
// Pricing/collections/tokens for the pre-pivot DTO shapes below are still
// not wired — those get their own phase (tokens: Phase 8) instead of being
// silently faked, per HANDOFF.md's "honest not-wired message" decision.
class VaultViewModel(private val application: Application) : AndroidViewModel(application) {

    private val serviceLocator get() = ServiceLocator.get(application)

    private val _state = MutableStateFlow<ScreenState<VaultUiState>>(ScreenState.Content(VaultUiState()))
    val state: StateFlow<ScreenState<VaultUiState>> = _state.asStateFlow()

    private var observeTracksJob: Job? = null

    init {
        load()
    }

    fun load() {
        observeTracksJob?.cancel()
        observeTracksJob = viewModelScope.launch {
            serviceLocator.vaultRepository.observeTracks().collect { tracks ->
                updateState { it.copy(localTracks = tracks) }
            }
        }
    }

    suspend fun hasVaultTreeAccess(): Boolean = serviceLocator.vaultIngestor.persistedTreeUri(application) != null

    fun persistVaultTreeGrant(treeUri: Uri) {
        serviceLocator.vaultIngestor.persistTreeGrant(application, treeUri)
    }

    fun ingestTracks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            updateState { it.copy(actionInFlight = true) }
            var succeeded = 0
            var failed = 0
            for (uri in uris) {
                try {
                    serviceLocator.vaultIngestor.ingest(application, uri)
                    succeeded++
                } catch (e: Exception) {
                    failed++
                }
            }
            updateState { it.copy(actionInFlight = false, actionMessage = ingestSummary(succeeded, failed)) }
        }
    }

    fun saveLocalTrackPricing(trackId: String, suggestedPriceCents: Int, minimumPriceCents: Int, allowFree: Boolean) {
        viewModelScope.launch {
            val existing = serviceLocator.vaultRepository.getTrack(trackId) ?: return@launch
            serviceLocator.vaultRepository.upsertTrack(
                existing.copy(
                    suggestedPriceCents = suggestedPriceCents,
                    minimumPriceCents = minimumPriceCents,
                    allowFree = allowFree,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun notify(message: String) {
        updateState { it.copy(actionMessage = message) }
    }

    fun saveTrackPricing(contentId: Int, request: VaultPricingRequest) = notify(LEGACY_NOT_WIRED)
    fun saveCollectionPricing(project: VaultProject, request: UpdateCollectionRequest) = notify(LEGACY_NOT_WIRED)
    fun deleteCollection(id: Int) = notify(LEGACY_NOT_WIRED)
    fun addCollectionTrack(projectId: Int, contentId: Int) = notify(LEGACY_NOT_WIRED)
    fun removeCollectionTrack(projectId: Int, contentId: Int) = notify(LEGACY_NOT_WIRED)
    fun moveCollectionTrack(project: VaultProject, contentId: Int, direction: Int) = notify(LEGACY_NOT_WIRED)
    fun toggleHighlight(type: String, id: Int) = notify(LEGACY_NOT_WIRED)
    fun uploadArtwork(trackId: Int, uri: Uri) = notify(LEGACY_NOT_WIRED)
    fun createToken(label: String, tier: String, email: String) = notify(LEGACY_NOT_WIRED)
    fun revokeToken(id: Int) = notify(LEGACY_NOT_WIRED)
    fun setTokenTier(id: Int, tier: String) = notify(LEGACY_NOT_WIRED)
    fun toggleTokenAssignments(id: Int) = notify(LEGACY_NOT_WIRED)
    fun assignToken(tokenId: Int, email: String) = notify(LEGACY_NOT_WIRED)
    fun unassignToken(tokenId: Int, assignmentId: Int) = notify(LEGACY_NOT_WIRED)

    private fun updateState(transform: (VaultUiState) -> VaultUiState) {
        val current = (_state.value as? ScreenState.Content)?.data ?: VaultUiState()
        _state.value = ScreenState.Content(transform(current))
    }

    private fun ingestSummary(succeeded: Int, failed: Int): String = buildString {
        if (succeeded > 0) append(if (succeeded == 1) "Added 1 track to the vault." else "Added $succeeded tracks to the vault.")
        if (failed > 0) {
            if (isNotEmpty()) append(" ")
            append(if (failed == 1) "1 file couldn't be added." else "$failed files couldn't be added.")
        }
        if (isEmpty()) append("Nothing was added.")
    }

    private companion object {
        const val LEGACY_NOT_WIRED =
            "Pricing, collections, and access tokens aren't wired to the on-device backend yet — coming in a later build phase."
    }
}
