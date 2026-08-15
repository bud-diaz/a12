package com.paperweight.os.broadcast

import android.content.Context
import android.net.Uri
import com.paperweight.os.broadcast.mic.MicCapture
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
import kotlin.math.ceil

class BroadcastEngine(
    private val context: Context,
    private val repository: BroadcastRepository,
    private val segmentStore: SegmentStore = SegmentStore(File(context.filesDir, "hls")),
    private val trackDecoder: TrackDecoder = TrackDecoder(context),
    private val micCapture: MicCapture = MicCapture(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(BroadcastState())
    val state: StateFlow<BroadcastState> = _state.asStateFlow()
    private var observerJob: Job? = null
    private var renderJob: Job? = null
    private var mode: String = MODE_SHUFFLE
    private var latestPublicTracks: List<VaultTrackEntity> = emptyList()
    @Volatile private var micLive = false
    private var nextMediaSequence = 0L
    private var markNextSegmentDiscontinuity = false
    private val liveWindow = ArrayDeque<HlsSegment>()

    fun start() {
        if (observerJob?.isActive == true) return
        observerJob = scope.launch {
            repository.vaultRepository.observeTracks().collectLatest { tracks ->
                latestPublicTracks = tracks.filter { it.visibility == VISIBILITY_PUBLIC && it.storagePath.isNotBlank() }
                if (!micLive) startRotation(latestPublicTracks)
            }
        }
    }

    fun stop() {
        micLive = false
        observerJob?.cancel()
        observerJob = null
        renderJob?.cancel()
        renderJob = null
        _state.value = _state.value.copy(isRunning = false, isMicLive = false, actionMessage = "Broadcast stopped.")
    }

    fun restart() {
        val currentQueue = _state.value.queue
        micLive = false
        if (currentQueue.isEmpty() && latestPublicTracks.isEmpty()) {
            _state.value = _state.value.copy(isRunning = true, isMicLive = false, actionMessage = "Broadcast idle: no public vault tracks yet.")
        } else {
            renderJob?.cancel()
            observerJob?.cancel()
            observerJob = null
            start()
            _state.value = _state.value.copy(isMicLive = false, actionMessage = "Broadcast restarted.")
        }
    }

    fun toggleMode() {
        mode = if (mode == MODE_SHUFFLE) MODE_SCHEDULED else MODE_SHUFFLE
        _state.value = _state.value.copy(mode = mode, actionMessage = "Broadcast mode: $mode")
    }

    fun goLive() {
        if (micLive) return
        micLive = true
        markNextSegmentDiscontinuity = true
        renderJob?.cancel()
        renderJob = scope.launch { renderMicLoop() }
        _state.value = _state.value.copy(
            isRunning = true,
            isMicLive = true,
            mode = MODE_LIVE_MIC,
            nowPlayingTrackId = null,
            nowPlayingTitle = LIVE_MIC_TITLE,
            nowPlayingArtist = LIVE_MIC_ARTIST,
            elapsedMs = 0,
            durationMs = MicCapture.DEFAULT_CAPTURE_MS,
            actionMessage = "Mic is live. Stream will cut over on the next HLS segment.",
        )
    }

    fun stopLive() {
        if (!micLive) return
        micLive = false
        markNextSegmentDiscontinuity = true
        renderJob?.cancel()
        renderJob = null
        _state.value = _state.value.copy(isMicLive = false, mode = mode, actionMessage = "Mic stopped. Returning to station rotation.")
        startRotation(latestPublicTracks)
    }

    fun removeFromQueue(index: Int) {
        val current = _state.value.queue
        if (index !in current.indices) return
        _state.value = _state.value.copy(queue = current.toMutableList().also { it.removeAt(index) }, actionMessage = "Removed from queue.")
    }

    private fun startRotation(publicTracks: List<VaultTrackEntity>) {
        renderJob?.cancel()
        if (micLive) return
        if (publicTracks.isEmpty()) {
            val silentSegments = segmentStore.writeInitialSilentWindow()
            liveWindow.clear()
            liveWindow.addAll(silentSegments.takeLast(LIVE_WINDOW_SIZE))
            nextMediaSequence = ((silentSegments.maxOfOrNull { it.sequence } ?: -1L) + 1)
                .coerceAtLeast(nextMediaSequence)
            _state.value = BroadcastState(
                isRunning = true,
                mode = mode,
                playlistPath = segmentStore.playlistFile().absolutePath,
                segmentCount = silentSegments.size,
                actionMessage = "Broadcast idle: no public vault tracks yet.",
            )
            return
        }
        renderJob = scope.launch {
            var index = 0
            while (isActive && !micLive) {
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
            val segments = segmentStore.writeEncodedSegments(
                encodedAudio = encoded,
                startSequence = nextMediaSequence,
                clearExisting = false,
                discontinuityOnFirstSegment = consumePendingDiscontinuity(),
                publishImmediately = false,
            )
            advanceNextSequence(segments)
            val queue = publicTracks.map { BroadcastQueueTrack(id = it.id, title = it.title, artist = it.artist) }
            segments.forEachIndexed { segmentIndex, segment ->
                if (micLive) return
                publishSegment(segment)
                _state.value = BroadcastState(
                    isRunning = true,
                    mode = mode,
                    isMicLive = false,
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
                isMicLive = false,
                mode = mode,
                queue = publicTracks.map { BroadcastQueueTrack(id = it.id, title = it.title, artist = it.artist) },
                actionMessage = "Broadcast encode failed for ${track.title}: ${error.message ?: error::class.java.simpleName}",
            )
            delay(ERROR_BACKOFF_MS)
        }
    }

    private suspend fun renderMicLoop() {
        while (currentCoroutineContext().isActive && micLive) {
            runCatching {
                val pcm = micCapture.capturePcmSegment(MicCapture.DEFAULT_CAPTURE_MS)
                val encoded = AacEncoder.encode(pcm)
                val segments = segmentStore.writeEncodedSegments(
                    encodedAudio = encoded,
                    startSequence = nextMediaSequence,
                    clearExisting = false,
                    discontinuityOnFirstSegment = consumePendingDiscontinuity(),
                    publishImmediately = false,
                )
                advanceNextSequence(segments)
                segments.forEachIndexed { segmentIndex, segment ->
                    if (!micLive) return
                    publishSegment(segment)
                    _state.value = BroadcastState(
                        isRunning = true,
                        mode = MODE_LIVE_MIC,
                        isMicLive = true,
                        nowPlayingTitle = LIVE_MIC_TITLE,
                        nowPlayingArtist = LIVE_MIC_ARTIST,
                        elapsedMs = (segmentIndex + 1) * segment.durationSeconds.toLong() * 1_000L,
                        durationMs = MicCapture.DEFAULT_CAPTURE_MS,
                        queue = latestPublicTracks.map { BroadcastQueueTrack(id = it.id, title = it.title, artist = it.artist) },
                        playlistPath = segmentStore.playlistFile().absolutePath,
                        segmentCount = segments.size,
                        actionMessage = "Mic live: publishing ${segments.size} microphone HLS segment(s).",
                    )
                    if (currentCoroutineContext().isActive && micLive && segmentIndex < segments.lastIndex) {
                        delay((segment.durationSeconds * 1_000).toLong().coerceAtLeast(MIN_SEGMENT_DELAY_MS))
                    }
                }
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    isRunning = true,
                    isMicLive = micLive,
                    mode = MODE_LIVE_MIC,
                    nowPlayingTitle = LIVE_MIC_TITLE,
                    nowPlayingArtist = LIVE_MIC_ARTIST,
                    actionMessage = "Mic live failed: ${error.message ?: error::class.java.simpleName}",
                )
                delay(ERROR_BACKOFF_MS)
            }
        }
    }

    private fun consumePendingDiscontinuity(): Boolean {
        val shouldMark = markNextSegmentDiscontinuity
        markNextSegmentDiscontinuity = false
        return shouldMark
    }

    private fun advanceNextSequence(segments: List<HlsSegment>) {
        nextMediaSequence = ((segments.maxOfOrNull { it.sequence } ?: (nextMediaSequence - 1)) + 1)
            .coerceAtLeast(nextMediaSequence)
    }

    private fun publishSegment(segment: HlsSegment) {
        liveWindow += segment
        while (liveWindow.size > LIVE_WINDOW_SIZE) liveWindow.removeFirst()
        val targetDurationSeconds = ceil(liveWindow.maxOf { it.durationSeconds }).toInt().coerceAtLeast(1)
        segmentStore.publishLiveWindow(
            liveWindow.toList(),
            currentIndex = liveWindow.lastIndex,
            targetDurationSeconds = targetDurationSeconds,
        )
    }

    private companion object {
        const val VISIBILITY_PUBLIC = "public"
        const val MODE_SHUFFLE = "shuffle"
        const val MODE_SCHEDULED = "scheduled"
        const val MODE_LIVE_MIC = "live_mic"
        const val LIVE_MIC_TITLE = "Live from the A12 mic"
        const val LIVE_MIC_ARTIST = "Paperweight OS"
        const val LIVE_WINDOW_SIZE = 5
        const val MIN_SEGMENT_DELAY_MS = 250L
        const val ERROR_BACKOFF_MS = 5_000L
    }
}
