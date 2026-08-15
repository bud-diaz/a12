package com.paperweight.os.reachability

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * `ProcessBuilder` supervisor for the bundled `frpc` binary, mirroring
 * paperweightv1's own `src/runtime/frp-supervisor.js`: scans stdout/stderr for
 * a small set of "connected" phrases, exponential-backoff reconnect on
 * unexpected exit (base 2s × attempt count, max 5 attempts), SIGTERM-then-
 * escalate stop. Owned by `BroadcastService` — long-lived, restart-on-crash,
 * not a periodic WorkManager job (plan decision #6: frpc needs to stay
 * connected continuously, not run on a schedule).
 */
class FrpcProcessSupervisor(
    private val frpcPath: File,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val _status = MutableStateFlow<TunnelStatus>(TunnelStatus.Stopped)
    val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private var process: Process? = null
    private var runLoopJob: Job? = null
    private var reconnectAttempts = 0
    private var stopping = false

    fun start(configFile: File, publicUrl: String) {
        stop()
        stopping = false
        reconnectAttempts = 0
        spawn(configFile, publicUrl)
    }

    fun stop() {
        stopping = true
        runLoopJob?.cancel()
        runLoopJob = null
        process?.let { proc ->
            proc.destroy()
            scope.launch {
                delay(KILL_ESCALATE_MS)
                if (proc.isAlive) proc.destroyForcibly()
            }
        }
        process = null
        _status.value = TunnelStatus.Stopped
    }

    private fun spawn(configFile: File, publicUrl: String) {
        if (stopping) return
        if (!frpcPath.exists()) {
            _status.value = TunnelStatus.Error("frpc binary is not bundled on this build — see HANDOFF.md's Phase 9 notes.")
            return
        }
        _status.value = TunnelStatus.Connecting
        runLoopJob = scope.launch {
            try {
                val proc = ProcessBuilder(frpcPath.absolutePath, "-c", configFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                process = proc
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var connected = false
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (!connected && CONNECTED_PATTERN.containsMatchIn(line)) {
                        connected = true
                        reconnectAttempts = 0
                        _status.value = TunnelStatus.Connected(publicUrl)
                    }
                }
                val exitCode = proc.waitFor()
                process = null
                if (!stopping) {
                    _status.value = TunnelStatus.Error("frpc exited unexpectedly (code $exitCode)")
                    scheduleReconnect(configFile, publicUrl)
                }
            } catch (error: Exception) {
                process = null
                if (!stopping) {
                    _status.value = TunnelStatus.Error(error.message ?: "frpc failed to start")
                    scheduleReconnect(configFile, publicUrl)
                }
            }
        }
    }

    private suspend fun scheduleReconnect(configFile: File, publicUrl: String) {
        if (stopping) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _status.value = TunnelStatus.Error("frpc failed to stay connected after $MAX_RECONNECT_ATTEMPTS attempts.")
            return
        }
        reconnectAttempts += 1
        delay(RECONNECT_BASE_DELAY_MS * reconnectAttempts)
        spawn(configFile, publicUrl)
    }

    private companion object {
        const val KILL_ESCALATE_MS = 2_000L
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val RECONNECT_BASE_DELAY_MS = 2_000L
        val CONNECTED_PATTERN = Regex(
            "start proxy success|login to server success|work connection registered|proxy .* started",
            RegexOption.IGNORE_CASE,
        )
    }
}
