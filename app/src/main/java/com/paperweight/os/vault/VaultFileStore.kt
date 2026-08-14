package com.paperweight.os.vault

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

// Copies ingested source audio into Paperweight/vault/ on the SD card
// (plan decision #11) so the working vault stays portable and
// self-contained on removable media, independent of where the source file
// originally lived. Takes an already-granted tree URI — acquiring and
// persisting that grant is VaultIngestor's job (decision #10). Callers are
// expected to run this off the main thread; SAF document operations are
// blocking.
object VaultFileStore {
    private const val ROOT_FOLDER_NAME = "Paperweight"
    private const val VAULT_FOLDER_NAME = "vault"

    fun copyIntoVault(context: Context, treeUri: Uri, sourceUri: Uri, fileName: String): Uri {
        val vaultDir = vaultDirectory(context, treeUri)
            ?: throw IOException("Could not open the vault folder on the SD card.")
        val uniqueName = uniqueChildName(vaultDir, fileName)
        val destination = vaultDir.createFile("application/octet-stream", uniqueName)
            ?: throw IOException("Could not create vault file $uniqueName.")
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Could not read source file $fileName.")
        input.use { source ->
            val output = context.contentResolver.openOutputStream(destination.uri)
                ?: throw IOException("Could not write vault file $uniqueName.")
            output.use { source.copyTo(it) }
        }
        return destination.uri
    }

    private fun vaultDirectory(context: Context, treeUri: Uri): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val paperweight = root.findFile(ROOT_FOLDER_NAME) ?: root.createDirectory(ROOT_FOLDER_NAME) ?: return null
        return paperweight.findFile(VAULT_FOLDER_NAME) ?: paperweight.createDirectory(VAULT_FOLDER_NAME)
    }

    private fun uniqueChildName(dir: DocumentFile, fileName: String): String {
        if (dir.findFile(fileName) == null) return fileName
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var suffix = 1
        var candidate = "$base-$suffix$ext"
        while (dir.findFile(candidate) != null) {
            suffix++
            candidate = "$base-$suffix$ext"
        }
        return candidate
    }
}
