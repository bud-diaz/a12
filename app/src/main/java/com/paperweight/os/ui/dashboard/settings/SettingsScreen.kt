package com.paperweight.os.ui.dashboard.settings

// Mirrors views/SettingsView.tsx: workspace preferences, notifications, RSS
// feed, track glow color, listener account recovery, and docs viewer.
// DesktopSection is intentionally not ported (no Electron-equivalent bridge
// on Android — CLAUDE.md decision). The Docs modal becomes an inline panel,
// matching this app's no-modal-system convention.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.network.models.DocEntry
import com.paperweight.os.ui.components.DropdownField
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader

private val FEED_SCOPES = listOf("podcasts" to "Podcast-tagged tracks only", "all" to "Everything public")

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        var motion by rememberSaveable { mutableStateOf(true) }
        var webhookUrl by rememberSaveable(data.notifyWebhookUrl) { mutableStateOf(data.notifyWebhookUrl) }
        var liveEnabled by rememberSaveable { mutableStateOf(data.notifyLiveEnabled) }
        var feedEnabled by rememberSaveable { mutableStateOf(data.feedEnabled) }
        var feedScope by rememberSaveable { mutableStateOf(data.feedScope) }
        var glowColor by rememberSaveable(data.trackGlowColor) { mutableStateOf(data.trackGlowColor) }
        var recoveryEmail by rememberSaveable { mutableStateOf("") }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Account / Settings",
                    title = "Your studio, your rules.",
                    description = "Quiet preferences that make this workspace feel like yours.",
                )
            }
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Workspace preferences", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Motion", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Keep the studio's small movements alive. Stored on this device only.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = motion, onCheckedChange = { motion = it })
                    }
                }
            }
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Notifications", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Notify on go-live", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Ping your webhook when you start broadcasting.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = liveEnabled, onCheckedChange = { liveEnabled = it })
                    }
                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = { webhookUrl = it },
                        label = { Text("Discord-compatible webhook URL") },
                        placeholder = { Text("https://discord.com/api/webhooks/…") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Text(
                        text = if (data.emailConfigured) {
                            "Email is also configured — supporters get emailed on new posts."
                        } else {
                            "Email is not configured — only this webhook fires on new posts/go-live."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(
                        onClick = { viewModel.saveNotifications(webhookUrl, liveEnabled) },
                        enabled = !data.actionInFlight,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Save notifications") }
                }
            }
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "RSS / podcast feed", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Enable feed.xml", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Publish your catalog as a podcast feed at /feed.xml.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = feedEnabled, onCheckedChange = { feedEnabled = it })
                    }
                    if (feedEnabled) {
                        DropdownField(
                            label = "Feed scope",
                            options = FEED_SCOPES,
                            selected = feedScope,
                            onSelect = { feedScope = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                    Button(
                        onClick = { viewModel.saveFeed(feedEnabled, feedScope) },
                        enabled = !data.actionInFlight,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Save feed settings") }
                }
            }
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Track glow color", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(parseHexColor(glowColor), RoundedCornerShape(8.dp)),
                        )
                        OutlinedTextField(
                            value = glowColor,
                            onValueChange = { glowColor = it },
                            label = { Text("Hex color") },
                            placeholder = { Text("#39ff14") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "The accent color for the now-playing indicator on your public player.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(
                        onClick = { viewModel.saveGlowColor(glowColor) },
                        enabled = !data.actionInFlight,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Save") }
                }
            }
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Listener account recovery", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Generate a password reset link to hand a listener directly — works even when email delivery isn't configured.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        OutlinedTextField(
                            value = recoveryEmail,
                            onValueChange = { recoveryEmail = it },
                            label = { Text("Listener email") },
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { viewModel.generateResetLink(recoveryEmail) },
                            enabled = recoveryEmail.isNotBlank() && !data.actionInFlight,
                        ) { Text("Generate") }
                    }
                    if (data.resetLinkUrl != null) {
                        PanelCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentPadding = PaddingValues(14.dp)) {
                            val expiry = data.resetLinkExpiresAt?.let { " — valid until $it" } ?: ""
                            Text(
                                text = "Reset link for ${data.resetLinkEmail}$expiry.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                Text(text = data.resetLinkUrl, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(data.resetLinkUrl))
                                    viewModel.notify("Reset link copied.")
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy reset link")
                                }
                            }
                        }
                    }
                }
            }
            item {
                DocsPanel(
                    docs = data.docs,
                    selectedDocId = data.selectedDocId,
                    selectedDocTitle = data.selectedDocTitle,
                    selectedDocText = data.selectedDocText,
                    selectedDocLoading = data.selectedDocLoading,
                    onSelect = viewModel::selectDoc,
                    onClose = viewModel::closeDoc,
                )
            }
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
private fun DocsPanel(
    docs: List<DocEntry>,
    selectedDocId: String?,
    selectedDocTitle: String,
    selectedDocText: String?,
    selectedDocLoading: Boolean,
    onSelect: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Docs", style = MaterialTheme.typography.titleMedium)
        if (docs.isEmpty()) {
            Text(
                text = "No docs available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                docs.forEach { doc ->
                    OutlinedButton(onClick = { onSelect(doc.id, doc.title) }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = doc.title, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (selectedDocId != null) {
            PanelCard(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), contentPadding = PaddingValues(14.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = selectedDocTitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
                }
                if (selectedDocLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        text = selectedDocText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Color.Gray
}
