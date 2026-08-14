package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

// GET /api/library/structure — exact shape from paperweightv1's
// src/api/library.js (router.get('/structure', ...) + formatItem()).
@Serializable
data class LibraryStructure(
    val projects: List<LibraryProject> = emptyList(),
    val standalone: List<LibraryTrack> = emptyList(),
)

@Serializable
data class LibraryProject(
    val id: Int,
    val name: String,
    val description: String? = null,
    val tracks: List<LibraryTrack> = emptyList(),
)

@Serializable
data class LibraryTrack(
    val id: Int,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val category: String? = null,
    val duration: Double? = null,
    val tags: List<String> = emptyList(),
    val visibility: String? = null,
    val mimeType: String? = null,
    val isVideo: Boolean = false,
    val isVault: Boolean = false,
    val artworkUrl: String? = null,
    val previewUrl: String? = null,
)
