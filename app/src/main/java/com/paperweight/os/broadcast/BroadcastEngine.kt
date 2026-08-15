package com.paperweight.os.broadcast

import android.content.Context
import android.net.Uri
import com.paperweight.os.data.db.entity.VaultTrackEntity
import com.paperweight.os.data.repository.BroadcastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class BroadcastEngine(
    private val context: Context,
    private val repository: BroadcastRepository,
    private val segmentStore: SegmentStore = SegmentStore(File(context.filesDir, "hls")),
    private val trackDecoder: TrackDecoder = TrackDecoder(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(BroadcastState())
    val state: StateFlow<BroadcastState> = _state.asStateFlow()
    private var observerJob: Job? = null
    private var renderJob: Job? = null
    private var mode: String = MODE_SHUFFLE

    fun start() {
        if (observerJob?.isActive == true) return
        observerJob = scope.launch {
            repository.vaultRepository.observeTracks().collectLatest { tracks ->
                val publicTracks = tracks.filter { it.visibility == VISIBILITY_PUBLIC && it.storagePath.isNotBlank() }
                startRotation(publicTracks)
            }
        }
    }

    fun stop() {
        observerJob?.cancel()
        observerJob = null
        renderJob?.cancel()
        renderJob = null
        _state.value = _state.value.copy(isRunning = false, actionMessage = "Broadcast stopped.")
    }

    fun restart() {
        val currentQueue = _state.value.queue
        if (currentQueue.isEmpty()) {
            _state.value = _state.value.copy(isRunning = true, actionMessage = "Broadcast idle: no public vault tracks yet.")
        } else {
            renderJob?.cancel()
            observerJob?.cancel()
            observerJob = null
            start()
            _state.value = _state.value.copy(actionMessage = "Broadcast restarted.")
        }
    }

    fun toggleMode() {
        mode = if (mode == MODE_SHUFFLE) MODE_SCHEDULED else MODE_SHUFFLE
        _state.value = _state.value.copy(mode = mode, actionMessage = "Broadcast mode: $mode")
    }

    fun removeFromQueue(index: Int) {
        val current = _state.value.queue
        if (index !in current.indices) return
        _state.value = _state.value.copy(queue = current.toMutableList().also { it.removeAt(index) }, actionMessage = "Removed from queue.")
    }

    private fun startRotation(publicTracks: List<VaultTrackEntity>) {
        renderJob?.cancel()
        if (publicTracks.isEmpty()) {
            _state.value = BroadcastState(isRunning = true, mode = mode, actionMessage = "Broadcast idle: no public vault tracks yet.")
            return
        }
        renderJob = scope.launch {
            var index = 0
            while (isActive) {
                val track = publicTracks[index % publicTracks.size]
                renderAndPublishTrack(track, publicTracks)
                index += 1
            }
        }
    }

    private suspend fun renderAndPublishTrack(track: VaultTrackEntity, publicTracks: List<VaultTrackEntity>) {
        runCatching {
            val decoded = trackDecoder.decodeToPcm(Uri.parse(track.storagePath))
            val encoded = AacEncoder.encode(decoded)
            val segments = segmentStore.writeEncodedSegments(encoded)
            val queue = publicTracks.map { BroadcastQueueTrack(id = it.id, title = it.title, artist = it.artist) }
            segments.forEachIndexed { segmentIndex, segment ->
                segmentStore.publishLiveWindow(segments, currentIndex = segmentIndex)
                _state.value = BroadcastState(
                    isRunning = true,
                    mode = mode,
                    nowPlayingTrackId = track.id,
                    nowPlayingTitle = track.title,
                    nowPlayingArtist = track.artist,
                    elapsedMs = segments.take(segmentIndex).sumOf { (it.durationSeconds * 1_000).toLong() },
                    durationMs = decoded.durationUs / 1_000,
                    queue = queue,
                    playlistPath = segmentStore.playlistFile().absolutePath,
                    segmentCount = segments.size,
                    actionMessage = "Broadcast engine running: encoded ${segments.size} real audio segment(s).",
                )
                if (currentCoroutineContext().isActive && segmentIndex < segments.lastIndex) {
                    delay((segment.durationSeconds * 1_000).toLong().coerceAtLeast(MIN_SEGMENT_DELAY_MS))
                }
            }
        }.onFailure { error ->
            _state.value = _state.value.copy(
                isRunning = true,
                queue = publicTracks.map { BroadcastQueueTrack(id = it.id, title = it.title, artist = it.artist) },
                actionMessage = "Broadcast encode failed for ${track.title}: ${error.message ?: error::class.java.simpleName}",
            )
            delay(ERROR_BACKOFF_MS)
        }
    }

    private companion object {
        const val VISIBILITY_PUBLIC = "public"
        const val MODE_SHUFFLE = "shuffle"
        const val MODE_SCHEDULED = "scheduled"
        const val MIN_SEGMENT_DELAY_MS = 250L
        const val ERROR_BACKOFF_MS = 5_000L
    }
}
