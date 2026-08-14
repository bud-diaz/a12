package com.paperweight.os.ui.dashboard.vault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.paperweight.os.network.models.UpdateCollectionRequest
import com.paperweight.os.network.models.VaultPricingRequest
import com.paperweight.os.network.models.VaultProject
import com.paperweight.os.ui.components.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The remote-backend client (ApiClient/Retrofit) this screen used to call
// was removed as part of the on-device-backend pivot (see HANDOFF.md).
// Rewiring to a local VaultRepository/VaultIngestor lands in a later phase;
// per the pivot's scope cut, email-based token assignment is being dropped
// in favor of label-only local bearer tokens when that happens.
class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ScreenState<VaultUiState>>(ScreenState.Error(NOT_YET_WIRED))
    val state: StateFlow<ScreenState<VaultUiState>> = _state.asStateFlow()

    fun load() {
        _state.value = ScreenState.Error(NOT_YET_WIRED)
    }

    fun notify(message: String) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun saveTrackPricing(contentId: Int, request: VaultPricingRequest) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun saveCollectionPricing(project: VaultProject, request: UpdateCollectionRequest) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun deleteCollection(id: Int) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun addCollectionTrack(projectId: Int, contentId: Int) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun removeCollectionTrack(projectId: Int, contentId: Int) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun moveCollectionTrack(project: VaultProject, contentId: Int, direction: Int) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun toggleHighlight(type: String, id: Int) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun uploadArtwork(trackId: Int, uri: Uri) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun createToken(label: String, tier: String, email: String) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun revokeToken(id: Int) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun setTokenTier(id: Int, tier: String) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun toggleTokenAssignments(id: Int) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun assignToken(tokenId: Int, email: String) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    fun unassignToken(tokenId: Int, assignmentId: Int) {
        // Not yet wired — see NOT_YET_WIRED.
    }

    private companion object {
        const val NOT_YET_WIRED =
            "Vault isn't wired to the on-device backend yet — coming in a later build phase."
    }
}
