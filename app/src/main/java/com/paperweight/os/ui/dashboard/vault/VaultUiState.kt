package com.paperweight.os.ui.dashboard.vault

import com.paperweight.os.data.db.entity.VaultTrackEntity
import com.paperweight.os.network.models.LibraryTrack
import com.paperweight.os.network.models.TokenAssignment
import com.paperweight.os.network.models.VaultHighlight
import com.paperweight.os.network.models.VaultProject
import com.paperweight.os.network.models.VaultToken
import com.paperweight.os.network.models.VaultTrackPrice

data class VaultUiState(
    // Locally ingested tracks (Phase 2: SAF picker -> VaultFileStore ->
    // Room), live from VaultRepository.observeTracks(). Everything below
    // this field is still driven by the pre-pivot remote DTOs and stays
    // empty/not-wired until pricing/collections/tokens get their own phase.
    val localTracks: List<VaultTrackEntity> = emptyList(),
    val trackPrices: List<VaultTrackPrice> = emptyList(),
    val projects: List<VaultProject> = emptyList(),
    val unpricedVaultTracks: List<VaultTrackPrice> = emptyList(),
    val availableTracks: List<LibraryTrack> = emptyList(),
    val highlight: VaultHighlight = VaultHighlight(),
    val tokens: List<VaultToken> = emptyList(),
    val openTokenId: Int? = null,
    val tokenAssignments: List<TokenAssignment> = emptyList(),
    val createdToken: String? = null,
    val actionMessage: String? = null,
    val actionInFlight: Boolean = false,
) {
    val hasAnything: Boolean
        get() = trackPrices.isNotEmpty() || projects.isNotEmpty() || unpricedVaultTracks.isNotEmpty() || localTracks.isNotEmpty()

    // Mutations refetch pricing/tokens/etc., but shouldn't clobber the
    // independently-managed token-assignments panel state.
    fun withCoreFrom(fresh: VaultUiState): VaultUiState = copy(
        trackPrices = fresh.trackPrices,
        projects = fresh.projects,
        unpricedVaultTracks = fresh.unpricedVaultTracks,
        availableTracks = fresh.availableTracks,
        highlight = fresh.highlight,
        tokens = fresh.tokens,
    )
}
