package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

// GET /api/dashboard/station — views/Station.tsx's `StationData`.
@Serializable
data class StationData(
    val slug: String? = null,
    val url: String? = null,
    val searchable: Boolean = false,
    val telemetryConfigured: Boolean = false,
    val paperweighthqTunnelAvailable: Boolean? = null,
    val cloudflareTunnelPaused: Boolean = false,
    val cloudflareApiConfigured: Boolean = false,
    val cloudflareTunnelManaged: Boolean = false,
    val requirements: StationRequirements = StationRequirements(),
)

@Serializable
data class StationRequirements(
    val cloudflareTunnel: Boolean = false,
    val publicUrlSet: Boolean = false,
)

// GET /api/dashboard/station/health
@Serializable
data class StationHealth(
    val reachable: Boolean = false,
    val latencyMs: Int = 0,
    val error: String? = null,
)

// GET /api/dashboard/station/cloudflare/zones
@Serializable
data class CloudflareZone(val id: String, val name: String = "")

@Serializable
data class CloudflareZonesResponse(val zones: List<CloudflareZone> = emptyList())

// GET /api/dashboard/station/cloudflare/tunnel/status — 5s poll.
@Serializable
data class TunnelStatus(
    val status: String? = null,
    val lastError: String? = null,
    val reconnectAttempts: Int = 0,
    val running: Boolean = false,
)

// GET /api/dashboard/setup-progress — milestone values are occurred_at
// timestamp strings (present = reached), not booleans.
@Serializable
data class SetupProgress(
    val milestones: Map<String, String> = emptyMap(),
    val signupDismissed: Boolean = false,
)

@Serializable
data class UpdateStationUrlRequest(val url: String)

@Serializable
data class SetSearchableRequest(val enabled: Boolean)

@Serializable
data class SaveCloudflareTokenRequest(val apiToken: String)

@Serializable
data class AutoCreateTunnelRequest(val zoneId: String, val hostname: String)

@Serializable
data class SaveTelemetrySecretRequest(val secret: String)

@Serializable
data class SignupRequest(val email: String, val updatesOptIn: Boolean)

// Shared response shape across the station/signup mutation endpoints — each
// only populates a subset of these fields; distinguished by HTTP status
// (2xx success vs. 4xx with `error`), not by payload shape.
@Serializable
data class StationMutationResponse(
    val error: String? = null,
    val url: String? = null,
    val note: String? = null,
    val checks: Map<String, Boolean> = emptyMap(),
    val paused: Boolean? = null,
    val ok: Boolean = false,
)
