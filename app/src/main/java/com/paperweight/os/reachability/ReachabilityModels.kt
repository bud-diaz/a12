package com.paperweight.os.reachability

/** system.pape's `/frp/tunnel/create` response — see docs referenced in HANDOFF.md's Phase 9 section. */
data class FrpTunnelCredentials(
    val hostname: String,
    val serverAddr: String,
    val serverPort: Int,
    val authToken: String,
    val proxyName: String,
    val subdomain: String,
)

sealed interface TunnelStatus {
    data object Stopped : TunnelStatus
    data object Connecting : TunnelStatus
    data class Connected(val publicUrl: String) : TunnelStatus
    data class Error(val message: String) : TunnelStatus
}
