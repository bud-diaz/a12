package com.paperweight.os.ui.dashboard.earnings

data class EarningsUiState(
    val pricedPublicTrackCount: Int = 0,
    val lowestSuggestedPriceCents: Int? = null,
    val highestSuggestedPriceCents: Int? = null,
    val actionMessage: String? = null,
)
