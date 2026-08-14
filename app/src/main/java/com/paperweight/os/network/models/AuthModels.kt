package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

// Matches POST /api/auth/dashboard/device/redeem in paperweightv1's
// src/api/auth.js exactly — see that file's JSDoc for the contract.
@Serializable
data class DeviceRedeemRequest(val pairToken: String)

@Serializable
data class DeviceRedeemResponse(val ok: Boolean = false, val error: String? = null)
