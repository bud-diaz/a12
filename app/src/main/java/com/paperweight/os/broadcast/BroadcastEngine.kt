package com.paperweight.os.broadcast

import android.content.Context
import com.paperweight.os.data.db.entity.VaultTrackEntity
import com.paperweight.os.data.repository.BroadcastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class BroadcastEngine(
    private val context: Context,
    private val repository: BroadcastRepository,
    private val segmentStore: SegmentStore = SegmentStore(File(context.filesDir, "hls")),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(BroadcastState())
    val state: StateFlow<BroadcastState> = _state.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            repository.vaultRepository.observeTracks().collectLatest { tracks ->
                val publicTracks = tracks.filter { it.visibility == VISIBILITY_PUBLIC }
                publish(publicTracks)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(isRunning = false, actionMessage = "Broadcast stopped.")
    }

    fun restart() {
        segmentStore.writeInitialSilentWindow()
        _state.value = _state.value.copy(isRunning = true, playlistPath = segmentStore.playlistFile().absolutePath, actionMessage = "Broadcast restarted.")
    }

    fun toggleMode() {
        val nextMode = if (_state.value.mode == MODE_SHUFFLE) MODE_SCHEDULED else MODE_SHUFFLE
        _state.value = _state.value.copy(mode = nextMode, actionMessage = "Broadcast mode: $nextMode")
    }

    fun removeFromQueue(index: Int) {
        val current = _state.value.queue
        if (index !in current.indices) return
        _state.value = _state.value.copy(queue = current.toMutableList().also { it.removeAt(index) }, actionMessage = "Removed from queue.")
    }

    private fun publish(publicTracks: List<VaultTrackEntity>) {
        if (publicTracks.isEmpty()) {
            _state.value = BroadcastState(isRunning = true, actionMessage = "Broadcast idle: no public vault tracks yet.")
            return
        }
        segmentStore.writeInitialSilentWindow()
        val nowPlaying = publicTracks.first()
        _state.value = BroadcastState(
            isRunning = true,
            mode = _state.value.mode,
            nowPlayingTrackId = nowPlaying.id,
            nowPlayingTitle = nowPlaying.title,
            nowPlayingArtist = nowPlaying.artist,
            queue = publicTracks.map { BroadcastQueueTrack(id = it.id, title = it.title, artist = it.artist) },
            playlistPath = segmentStore.playlistFile().absolutePath,
            actionMessage = "Broadcast engine running.",
        )
    }

    private companion object {
        const val VISIBILITY_PUBLIC = "public"
        const val MODE_SHUFFLE = "shuffle"
        const val MODE_SCHEDULED = "scheduled"
    }
}
