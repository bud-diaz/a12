package com.paperweight.os.backup

import androidx.documentfile.provider.DocumentFile

internal object BackupDocumentTree {
    private const val ROOT_FOLDER_NAME = "Paperweight"
    private const val BACKUPS_FOLDER_NAME = "backups"

    fun paperweightRoot(treeRoot: DocumentFile): DocumentFile? = if (treeRoot.name == ROOT_FOLDER_NAME) {
        treeRoot
    } else {
        treeRoot.findFile(ROOT_FOLDER_NAME) ?: treeRoot.createDirectory(ROOT_FOLDER_NAME)
    }

    fun backupsDirectory(treeRoot: DocumentFile): DocumentFile? {
        val paperweight = paperweightRoot(treeRoot) ?: return null
        return paperweight.findDirectory(BACKUPS_FOLDER_NAME) ?: paperweight.createDirectory(BACKUPS_FOLDER_NAME)
    }

    private fun DocumentFile.findDirectory(name: String): DocumentFile? = findFile(name)?.takeIf { it.isDirectory }
}
