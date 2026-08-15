package com.paperweight.os.broadcast

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri

data class DecodedTrackInfo(
    val sourceUri: Uri,
    val durationUs: Long,
    val mimeType: String?,
)

class TrackDecoder(private val context: Context) {
    fun inspect(sourceUri: Uri): DecodedTrackInfo {
        val extractor = MediaExtractor()
        return try {
            context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
                val format = (0 until extractor.trackCount)
                    .asSequence()
                    .map { extractor.getTrackFormat(it) }
                    .firstOrNull { it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    ?: error("No audio track found in $sourceUri")
                DecodedTrackInfo(
                    sourceUri = sourceUri,
                    durationUs = if (format.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                        format.getLong(android.media.MediaFormat.KEY_DURATION)
                    } else {
                        0L
                    },
                    mimeType = format.getString(android.media.MediaFormat.KEY_MIME),
                )
            } ?: error("Unable to open $sourceUri")
        } finally {
            extractor.release()
        }
    }
}
