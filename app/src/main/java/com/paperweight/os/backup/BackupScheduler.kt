package com.paperweight.os.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.prefs.AppPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object BackupScheduler {
    const val UNIQUE_PERIODIC_WORK = "paperweight-periodic-backup"

    fun schedule(context: Context, intervalHours: Int) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun backUpNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BackupWorker>().build())
    }
}

class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val preferences = AppPreferences.create(applicationContext)
        val treeUri = preferences.vaultTreeUri.first() ?: return Result.retry()
        val treeRoot = DocumentFile.fromTreeUri(applicationContext, Uri.parse(treeUri)) ?: return Result.retry()
        val database = AppDatabase.getInstance(applicationContext)
        return runCatching {
            BackupWriter(applicationContext, database, preferences).writeBackup(treeRoot)
            BackupPruner.prune(treeRoot, preferences.backupRetentionCount.first())
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
