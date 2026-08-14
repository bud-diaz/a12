package com.paperweight.os.ui.dashboard.earnings

// Mirrors views/Earnings.tsx: all-time revenue hero, revenue mix breakdown,
// tip/subscription support panel (with an inline tip-preset editor mirroring
// TipConfigModal.tsx), and top earners.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.network.models.EarningsSubscription
import com.paperweight.os.network.models.EarningsTotals
import com.paperweight.os.network.models.EarningsUnlock
import com.paperweight.os.network.models.TipConfig
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader

@Composable
fun EarningsScreen(viewModel: EarningsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        var configuringTips by rememberSaveable { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Signal / Earnings",
                    title = "Make the work pay.",
                    description = "A clean view of your listener support, tips, and streaming share.",
                    action = {
                        OutlinedButton(onClick = viewModel::openPaymentSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = null)
                            Text(text = "Payment settings", modifier = Modifier.padding(start = 8.dp))
                        }
                    },
                )
            }
            item { RevenueHeroPanel(data.totals) }
            item { RevenueMixPanel(data.totals) }
            item {
                SupportPanel(
                    tipsCount = data.tipsCount,
                    tipsGrossCents = data.tipsGrossCents,
                    subscriptions = data.subscriptions,
                    configuring = configuringTips,
                    actionInFlight = data.actionInFlight,
                    tipConfig = data.tipConfig,
                    onConfigureClick = { configuringTips = true },
                    onCancel = { configuringTips = false },
                    onSave = { config ->
                        viewModel.saveTipConfig(config)
                        configuringTips = false
                    },
                )
            }
            item { TopEarnersPanel(data.unlocks) }
            if (data.actionMessage != null) {
                item {
                    Text(
                        text = data.actionMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RevenueHeroPanel(totals: EarningsTotals) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "All-time revenue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = formatCents(totals.revenueCents), style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 10.dp))
        Text(
            text = "${formatCents(totals.todayRevenueCents)} today · ${totals.activeSubscriptions} active subscriptions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = "Payments settle directly to your connected Stripe/PayPal account — Paperweight doesn't hold or route funds.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun RevenueMixPanel(totals: EarningsTotals) {
    val breakdownTotal = totals.unlockRevenueCents + totals.tipRevenueCents + totals.knownMonthlyRecurringCents
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Revenue mix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = formatCents(breakdownTotal), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MixRow("Vault unlocks", totals.unlockRevenueCents)
            MixRow("Listener tips", totals.tipRevenueCents)
            MixRow("Recurring subscriptions (monthly)", totals.knownMonthlyRecurringCents)
        }
        RevenueMixBar(
            unlockPct = safePct(totals.unlockRevenueCents, breakdownTotal),
            tipPct = safePct(totals.tipRevenueCents, breakdownTotal),
            subscriptionPct = safePct(totals.knownMonthlyRecurringCents, breakdownTotal),
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun MixRow(label: String, cents: Long) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = formatCents(cents), style = MaterialTheme.typography.bodySmall)
    }
}

private fun safePct(cents: Long, total: Long): Float = if (total <= 0) 0f else (cents.toFloat() / total.toFloat()).coerceIn(0f, 1f)

@Composable
private fun RevenueMixBar(unlockPct: Float, tipPct: Float, subscriptionPct: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val primary = MaterialTheme.colorScheme.primary
        val tertiary = MaterialTheme.colorScheme.tertiary
        val neutral = MaterialTheme.colorScheme.onSurfaceVariant
        if (unlockPct > 0f) Box(modifier = Modifier.weight(unlockPct).fillMaxHeight().background(primary))
        if (tipPct > 0f) Box(modifier = Modifier.weight(tipPct).fillMaxHeight().background(tertiary))
        if (subscriptionPct > 0f) Box(modifier = Modifier.weight(subscriptionPct).fillMaxHeight().background(neutral))
    }
}

@Composable
private fun SupportPanel(
    tipsCount: Int,
    tipsGrossCents: Long,
    subscriptions: List<EarningsSubscription>,
    configuring: Boolean,
    actionInFlight: Boolean,
    tipConfig: TipConfig,
    onConfigureClick: () -> Unit,
    onCancel: () -> Unit,
    onSave: (TipConfig) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(text = "Support, your way", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Turn attention into momentum.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }
            Button(onClick = onConfigureClick, enabled = !actionInFlight) { Text("Configure tips") }
        }
        if (configuring) {
            TipConfigForm(config = tipConfig, enabled = !actionInFlight, onCancel = onCancel, onSave = onSave)
        }
        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row {
                    Icon(Icons.Outlined.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(text = "Tips", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "$tipsCount tips received", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(text = formatCents(tipsGrossCents), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (subscriptions.isEmpty()) {
                Text(
                    text = "No active subscriptions yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                subscriptions.forEach { sub ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row {
                            Icon(Icons.Outlined.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(text = sub.tier.replace('_', ' '), style = MaterialTheme.typography.bodyMedium)
                                Text(text = "${sub.count} active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(text = "${formatCents(sub.knownMonthlyCents)}/mo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TipConfigForm(config: TipConfig, enabled: Boolean, onCancel: () -> Unit, onSave: (TipConfig) -> Unit) {
    val initialAmounts = if (config.amounts.size == 3) config.amounts else listOf(300, 500, 1000)
    var preset1 by rememberSaveable { mutableStateOf(centsToDollarString(initialAmounts[0])) }
    var preset2 by rememberSaveable { mutableStateOf(centsToDollarString(initialAmounts[1])) }
    var preset3 by rememberSaveable { mutableStateOf(centsToDollarString(initialAmounts[2])) }
    var customEnabled by rememberSaveable { mutableStateOf(config.customEnabled) }

    val parsedCents = listOf(preset1, preset2, preset3).map { it.toDoubleOrNull()?.let { d -> (d * 100).toInt() } }
    val valid = parsedCents.all { it != null && it >= 100 }

    PanelCard(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            text = "These are the quick-pick amounts listeners see when they tip you. Each must be at least \$1.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            OutlinedTextField(value = preset1, onValueChange = { preset1 = it }, label = { Text("Preset 1") }, leadingIcon = { Text("$") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = preset2, onValueChange = { preset2 = it }, label = { Text("Preset 2") }, leadingIcon = { Text("$") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = preset3, onValueChange = { preset3 = it }, label = { Text("Preset 3") }, leadingIcon = { Text("$") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Allow custom amounts", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Let listeners type in their own amount instead of a preset.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = customEnabled, onCheckedChange = { customEnabled = it })
        }
        if (!valid) {
            Text(
                text = "Each preset must be a valid amount of at least \$1.00.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) { Text("Cancel") }
            Button(
                onClick = { onSave(TipConfig(amounts = parsedCents.map { it!! }, customEnabled = customEnabled)) },
                enabled = enabled && valid,
            ) { Text("Save presets") }
        }
    }
}

private fun centsToDollarString(cents: Int): String = if (cents % 100 == 0) (cents / 100).toString() else "%.2f".format(cents / 100.0)

@Composable
private fun TopEarnersPanel(unlocks: List<EarningsUnlock>) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Top earners", style = MaterialTheme.typography.titleMedium)
        if (unlocks.isEmpty()) {
            Text(
                text = "No vault unlocks yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                unlocks.take(8).forEach { unlock ->
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(text = unlock.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            text = "${unlock.unitsSold} unlock${if (unlock.unitsSold == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        Text(text = formatCents(unlock.revenueCents), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun formatCents(cents: Long): String = "$%.2f".format(cents / 100.0)
