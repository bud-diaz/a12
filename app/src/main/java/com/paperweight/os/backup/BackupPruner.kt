package com.paperweight.os.backup

import androidx.documentfile.provider.DocumentFile

object BackupPruner {
    fun prune(treeRoot: DocumentFile, keepCount: Int) {
        val backupsDir = BackupDocumentTree.backupsDirectory(treeRoot) ?: return
        val snapshots = backupsDir.listFiles()
            .filter { it.isDirectory && it.name != null }
            .sortedByDescending { it.name }
        snapshots.drop(keepCount.coerceAtLeast(0)).forEach { it.delete() }
    }
}
