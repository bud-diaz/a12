package com.paperweight.os.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.paperweight.os.data.db.entity.VaultTrackEntity
import com.paperweight.os.data.prefs.AppPreferences
import com.paperweight.os.data.repository.VaultRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// Orchestrates the "Add to vault" path end to end: the one-time SAF tree
// grant over the SD card (plan decision #10), metadata extraction, the
// VaultFileStore copy into Paperweight/vault/, and persisting the result as
// a VaultTrackEntity. The granted tree URI is reused by the Phase 3 backup
// system, which is why it lives behind AppPreferences rather than
// in-memory-only state.
class VaultIngestor(
    private val vaultRepository: VaultRepository,
    private val appPreferences: AppPreferences,
) {
    // Returns the granted tree URI only if the app still actually holds a
    // live read+write grant for it — a prior grant can be revoked (e.g. the
    // SD card was reformatted, or the OS revoked it), so this re-checks
    // persistedUriPermissions rather than trusting the stored string alone.
    suspend fun persistedTreeUri(context: Context): Uri? {
        val stored = appPreferences.vaultTreeUri.first() ?: return null
        val uri = Uri.parse(stored)
        val stillGranted = context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission && permission.isWritePermission
        }
        return if (stillGranted) uri else null
    }

    fun persistTreeGrant(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        appPreferences.setVaultTreeUri(treeUri)
    }

    suspend fun ingest(context: Context, sourceUri: Uri): VaultTrackEntity = withContext(Dispatchers.IO) {
        val treeUri = persistedTreeUri(context)
            ?: throw IllegalStateException("SD card vault folder isn't accessible — grant folder access first.")

        val displayName = queryDisplayName(context, sourceUri) ?: "Untitled"
        val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "Untitled" }
        val metadata = MetadataExtractor.extract(context, sourceUri, fallbackTitle)
        val vaultFileUri = VaultFileStore.copyIntoVault(context, treeUri, sourceUri, displayName)

        val now = System.currentTimeMillis()
        val track = VaultTrackEntity(
            id = UUID.randomUUID().toString(),
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            sourceUri = sourceUri.toString(),
            storagePath = vaultFileUri.toString(),
            durationMs = metadata.durationMs,
            mimeType = metadata.mimeType,
            visibility = "public",
            suggestedPriceCents = 0,
            minimumPriceCents = 0,
            allowFree = true,
            createdAt = now,
            updatedAt = now,
        )
        vaultRepository.upsertTrack(track)
        track
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
        }
        return uri.lastPathSegment
    }
}
