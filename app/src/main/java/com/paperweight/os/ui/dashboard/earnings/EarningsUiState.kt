package com.paperweight.os.ui.dashboard.earnings

import com.paperweight.os.network.models.EarningsSubscription
import com.paperweight.os.network.models.EarningsTotals
import com.paperweight.os.network.models.EarningsUnlock
import com.paperweight.os.network.models.TipConfig

data class EarningsUiState(
    val totals: EarningsTotals = EarningsTotals(),
    val unlocks: List<EarningsUnlock> = emptyList(),
    val tipsCount: Int = 0,
    val tipsGrossCents: Long = 0,
    val subscriptions: List<EarningsSubscription> = emptyList(),
    val tipConfig: TipConfig = TipConfig(),
    val actionMessage: String? = null,
    val actionInFlight: Boolean = false,
)
