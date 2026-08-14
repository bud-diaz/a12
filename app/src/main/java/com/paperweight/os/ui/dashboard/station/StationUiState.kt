package com.paperweight.os.ui.dashboard.station

import com.paperweight.os.network.models.CloudflareZone
import com.paperweight.os.network.models.StationHealth
import com.paperweight.os.network.models.StationRequirements
import com.paperweight.os.network.models.TunnelStatus

data class StationUiState(
    val slug: String? = null,
    val url: String? = null,
    val searchable: Boolean = false,
    val telemetryConfigured: Boolean = false,
    val paperweighthqTunnelAvailable: Boolean? = null,
    val cloudflareTunnelPaused: Boolean = false,
    val cloudflareApiConfigured: Boolean = false,
    val cloudflareTunnelManaged: Boolean = false,
    val requirements: StationRequirements = StationRequirements(),
    val health: StationHealth? = null,
    val healthChecking: Boolean = false,
    val zones: List<CloudflareZone> = emptyList(),
    val tunnelStatus: TunnelStatus? = null,
    val setupMilestones: Map<String, String> = emptyMap(),
    val signupDismissed: Boolean = false,
    val actionMessage: String? = null,
    val actionInFlight: Boolean = false,
) {
    val statusText: String
        get() = when {
            slug.isNullOrBlank() -> "Unclaimed"
            cloudflareTunnelPaused -> "Tunnel paused"
            requirements.cloudflareTunnel && requirements.publicUrlSet -> "Public routing ready"
            else -> "Local setup"
        }
}
