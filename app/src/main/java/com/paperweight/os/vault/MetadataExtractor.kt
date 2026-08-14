package com.paperweight.os.vault

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

data class AudioMetadata(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val mimeType: String,
)

// Reads embedded tags off a picked source file for the "Add to vault"
// ingestion path. Falls back to the source filename when a track has no
// title tag — MediaMetadataRetriever returns null for missing keys rather
// than throwing, so most fields degrade gracefully instead of failing
// ingestion outright.
object MetadataExtractor {
    fun extract(context: Context, sourceUri: Uri, fallbackTitle: String): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, sourceUri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: context.contentResolver.getType(sourceUri)
                ?: "audio/mpeg"
            AudioMetadata(title = title, artist = artist, album = album, durationMs = durationMs, mimeType = mimeType)
        } finally {
            retriever.release()
        }
    }
}
