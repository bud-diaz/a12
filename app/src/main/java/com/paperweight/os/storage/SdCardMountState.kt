package com.paperweight.os.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Live "is a valid SD card present" signal. Emits an initial synchronous
// check on collection, then re-checks on every media mount/eject broadcast
// so the running app can react to a card pulled mid-session (degrade to the
// required-card gate) instead of crashing on a broadcast/vault read.
object SdCardMountState {
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        trySend(SdCardDetector.hasValidCard(context))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                trySend(SdCardDetector.hasValidCard(context))
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        context.registerReceiver(receiver, filter)

        awaitClose { context.unregisterReceiver(receiver) }
    }
}
