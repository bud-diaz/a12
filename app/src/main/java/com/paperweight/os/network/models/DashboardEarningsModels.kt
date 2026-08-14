package com.paperweight.os.network.models

import kotlinx.serialization.Serializable

// GET /api/dashboard/earnings — union of fields referenced by both
// views/Overview.tsx (monthRevenueCents) and views/Earnings.tsx (the rest).
@Serializable
data class DashboardEarnings(
    val totals: EarningsTotals = EarningsTotals(),
    val unlocks: List<EarningsUnlock> = emptyList(),
    val tips: EarningsTips = EarningsTips(),
    val subscriptions: List<EarningsSubscription> = emptyList(),
)

@Serializable
data class EarningsTotals(
    val revenueCents: Long = 0,
    val monthRevenueCents: Long = 0,
    val todayRevenueCents: Long = 0,
    val unlockRevenueCents: Long = 0,
    val tipRevenueCents: Long = 0,
    val knownMonthlyRecurringCents: Long = 0,
    val activeSubscriptions: Int = 0,
)

@Serializable
data class EarningsUnlock(
    val unlockType: String,
    val targetId: Int,
    val title: String,
    val unitsSold: Int = 0,
    val revenueCents: Long = 0,
)

@Serializable
data class EarningsTips(
    val count: Int = 0,
    val grossCents: Long = 0,
    val recent: List<EarningsTip> = emptyList(),
)

@Serializable
data class EarningsTip(
    val amount_cents: Long = 0,
    val created_at: String = "",
)

@Serializable
data class EarningsSubscription(
    val tier: String,
    val count: Int = 0,
    val knownMonthlyCents: Long = 0,
)

// GET/PUT /api/dashboard/tip-config
@Serializable
data class TipConfig(
    val amounts: List<Int> = emptyList(),
    val customEnabled: Boolean = false,
)
