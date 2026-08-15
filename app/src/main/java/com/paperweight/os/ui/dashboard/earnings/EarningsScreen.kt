package com.paperweight.os.ui.dashboard.earnings

// Static "coming soon" shell — see EarningsViewModel's class doc for why the
// old Retrofit-era revenue/tips/subscriptions UI this replaced is gone
// entirely rather than kept dormant behind the state check.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader

@Composable
fun EarningsScreen(viewModel: EarningsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Signal / Earnings",
                    title = "Coming soon.",
                    description = "Payments and payouts aren't part of this build. Vault pricing is still yours to set below.",
                    action = {
                        OutlinedButton(onClick = viewModel::openPaymentSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = null)
                            Text(text = "Payment settings", modifier = Modifier.padding(start = 8.dp))
                        }
                    },
                )
            }
            item { PricingSummaryPanel(data) }
            data.actionMessage?.let { message ->
                item {
                    Text(text = message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun PricingSummaryPanel(data: EarningsUiState) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Vault pricing", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (data.pricedPublicTrackCount == 0) {
                "No public tracks have a suggested price yet. Set one from Vault — it's stored now, ready for when payments ship."
            } else {
                "${data.pricedPublicTrackCount} public track${if (data.pricedPublicTrackCount == 1) "" else "s"} priced" +
                    priceRangeText(data)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "This is inert metadata — nothing is charged. Payment processing isn't part of Paperweight OS v1.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun priceRangeText(data: EarningsUiState): String {
    val low = data.lowestSuggestedPriceCents
    val high = data.highestSuggestedPriceCents
    if (low == null || high == null) return "."
    return if (low == high) ", suggested at ${formatCents(low)}." else ", suggested ${formatCents(low)}–${formatCents(high)}."
}

private fun formatCents(cents: Int): String = "$%.2f".format(cents / 100.0)
