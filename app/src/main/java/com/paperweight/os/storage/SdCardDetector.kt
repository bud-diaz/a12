package com.paperweight.os.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import java.io.File

data class SdCardInfo(val path: File, val totalBytes: Long, val availableBytes: Long)

// Paperweight OS requires a removable SD card (>= 2GB total capacity) to be
// inserted — it's the default vault + backup storage location, not internal
// storage. ContextCompat.getExternalFilesDirs returns one app-specific dir
// per storage volume (internal first, then any removable ones); we only use
// these paths to identify/measure the volume, not to store data directly in
// them (vault/backup storage goes through a SAF tree grant instead, added in
// a later phase).
object SdCardDetector {
    const val MIN_CAPACITY_BYTES: Long = 2L * 1024 * 1024 * 1024

    fun findRemovableCard(context: Context): SdCardInfo? {
        val dirs = ContextCompat.getExternalFilesDirs(context, null)
        for (dir in dirs) {
            if (dir == null) continue
            if (!Environment.isExternalStorageRemovable(dir)) continue
            if (Environment.getExternalStorageState(dir) != Environment.MEDIA_MOUNTED) continue
            val stat = StatFs(dir.path)
            return SdCardInfo(
                path = dir,
                totalBytes = stat.blockSizeLong * stat.blockCountLong,
                availableBytes = stat.blockSizeLong * stat.availableBlocksLong,
            )
        }
        return null
    }

    fun hasValidCard(context: Context): Boolean =
        (findRemovableCard(context)?.totalBytes ?: 0L) >= MIN_CAPACITY_BYTES
}
