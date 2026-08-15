package com.paperweight.os.ui.dashboard.station

data class StationUiState(
    val stationName: String = "",
    val slug: String = "",
    val lanUrl: String? = null,
    val publicUrl: String? = null,
    val tunnelStatusText: String = "Not connected",
    val tunnelError: String? = null,
    val lastReachableAt: Long? = null,
    val actionMessage: String? = null,
    val actionInFlight: Boolean = false,
)
