package com.paperweight.os.ui.dashboard.station

// Mirrors views/Station.tsx: public URL / health, Cloudflare tunnel setup,
// directory searchability, telemetry secret, and local setup-progress
// checklist. Studio's two-column desktop layout collapses to a single
// stacked column here.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.network.models.CloudflareZone
import com.paperweight.os.ui.components.DropdownField
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader

private val SETUP_STEPS = listOf(
    "install_completed" to "Installed and running",
    "first_track_scanned" to "First track added to your vault",
    "went_public" to "Station went public",
    "first_listener" to "First listener tuned in",
)

@Composable
fun StationScreen(viewModel: StationViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        var publicUrl by rememberSaveable(data.url) { mutableStateOf(data.url ?: "") }
        var apiToken by rememberSaveable { mutableStateOf("") }
        var zoneId by rememberSaveable(data.zones) { mutableStateOf(data.zones.firstOrNull()?.id ?: "") }
        var hostname by rememberSaveable { mutableStateOf("") }
        var telemetrySecret by rememberSaveable { mutableStateOf("") }
        var signupEmail by rememberSaveable { mutableStateOf("") }

        val allDone = SETUP_STEPS.all { (key, _) -> data.setupMilestones.containsKey(key) }
        val missing = buildList {
            if (!data.requirements.cloudflareTunnel) add("a public tunnel connection")
            if (!data.requirements.publicUrlSet) add("a registered public URL")
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Signal / Station",
                    title = "Make the station reachable.",
                    description = "Public URL, managed tunnel, directory searchability, and PaperweightHQ registration live here.",
                )
            }
            item {
                StatusPanel(
                    data = data,
                    onCopyUrl = {
                        data.url?.let {
                            clipboard.setText(AnnotatedString(it))
                            viewModel.notify("Station URL copied.")
                        }
                    },
                    onRecheckHealth = viewModel::refetchHealth,
                    onToggleTunnel = viewModel::toggleTunnel,
                )
            }
            item {
                PublicUrlPanel(
                    publicUrl = publicUrl,
                    onPublicUrlChange = { publicUrl = it },
                    actionInFlight = data.actionInFlight,
                    onSave = { viewModel.updateUrl(publicUrl) },
                )
            }
            item {
                CloudflarePanel(
                    apiToken = apiToken,
                    onApiTokenChange = { apiToken = it },
                    zones = data.zones,
                    zoneId = zoneId,
                    onZoneChange = { zoneId = it },
                    hostname = hostname,
                    onHostnameChange = { hostname = it },
                    paperweighthqAvailable = data.paperweighthqTunnelAvailable != false,
                    actionInFlight = data.actionInFlight,
                    onSaveToken = { viewModel.saveCloudflareToken(apiToken) },
                    onCreateTunnel = { viewModel.autoCreateTunnel(zoneId, hostname) },
                    onHqTunnel = viewModel::createHqTunnel,
                )
            }
            item {
                SearchablePanel(
                    searchable = data.searchable,
                    missing = missing,
                    actionInFlight = data.actionInFlight,
                    onToggle = viewModel::toggleSearchable,
                )
            }
            item {
                TelemetryPanel(
                    configured = data.telemetryConfigured,
                    secret = telemetrySecret,
                    onSecretChange = { telemetrySecret = it },
                    hasSlug = !data.slug.isNullOrBlank(),
                    actionInFlight = data.actionInFlight,
                    onSave = { viewModel.saveTelemetrySecret(telemetrySecret) },
                    onRegister = viewModel::registerTelemetry,
                )
            }
            if (!allDone) {
                item {
                    SetupProgressPanel(
                        milestones = data.setupMilestones,
                        signupDismissed = data.signupDismissed,
                        signupEmail = signupEmail,
                        onSignupEmailChange = { signupEmail = it },
                        actionInFlight = data.actionInFlight,
                        onSignup = { viewModel.signup(signupEmail) },
                        onDismiss = viewModel::dismissSignup,
                    )
                }
            }
            if (!data.tunnelStatus?.lastError.isNullOrBlank()) {
                item {
                    PanelCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = data.tunnelStatus?.lastError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
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
private fun StatusPanel(
    data: StationUiState,
    onCopyUrl: () -> Unit,
    onRecheckHealth: () -> Unit,
    onToggleTunnel: () -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = data.statusText.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(text = data.slug ?: "Station not claimed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                Text(
                    text = data.url ?: "No public URL registered yet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (data.url != null) {
                OutlinedButton(onClick = onCopyUrl) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text(text = "Copy", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            StatusTile(
                label = "Health",
                value = when {
                    data.healthChecking -> "Checking…"
                    data.health?.reachable == true -> "Reachable · ${data.health.latencyMs}ms"
                    else -> data.health?.error ?: "Unreachable"
                },
                highlighted = data.health?.reachable == true,
                modifier = Modifier.weight(1f),
            )
            StatusTile(
                label = "Tunnel",
                value = data.tunnelStatus?.status ?: if (data.requirements.cloudflareTunnel) "Configured" else "Missing",
                highlighted = false,
                modifier = Modifier.weight(1f),
            )
            StatusTile(
                label = "Directory",
                value = if (data.searchable) "Searchable" else "Hidden",
                highlighted = data.searchable,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
            OutlinedButton(onClick = onRecheckHealth) { Text("Recheck health") }
            OutlinedButton(onClick = onToggleTunnel, enabled = data.requirements.cloudflareTunnel && !data.actionInFlight) {
                Text(if (data.cloudflareTunnelPaused) "Reconnect tunnel" else "Disconnect tunnel")
            }
        }
    }
}

@Composable
private fun StatusTile(label: String, value: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    PanelCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun PublicUrlPanel(publicUrl: String, onPublicUrlChange: (String) -> Unit, actionInFlight: Boolean, onSave: () -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Public URL", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = publicUrl,
            onValueChange = onPublicUrlChange,
            label = { Text("Station public URL") },
            placeholder = { Text("https://radio.yoursite.com") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Button(onClick = onSave, enabled = publicUrl.isNotBlank() && !actionInFlight, modifier = Modifier.padding(top = 10.dp)) {
            Text("Save URL")
        }
    }
}

@Composable
private fun CloudflarePanel(
    apiToken: String,
    onApiTokenChange: (String) -> Unit,
    zones: List<CloudflareZone>,
    zoneId: String,
    onZoneChange: (String) -> Unit,
    hostname: String,
    onHostnameChange: (String) -> Unit,
    paperweighthqAvailable: Boolean,
    actionInFlight: Boolean,
    onSaveToken: () -> Unit,
    onCreateTunnel: () -> Unit,
    onHqTunnel: () -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Cloudflare tunnel", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = apiToken,
            onValueChange = onApiTokenChange,
            label = { Text("Cloudflare API token") },
            placeholder = { Text("Paste a scoped Cloudflare token") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        OutlinedButton(onClick = onSaveToken, enabled = apiToken.isNotBlank() && !actionInFlight, modifier = Modifier.padding(top = 10.dp)) {
            Text("Save and verify token")
        }
        if (zones.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                DropdownField(
                    label = "Zone",
                    options = zones.map { it.id to it.name },
                    selected = zoneId,
                    onSelect = onZoneChange,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = hostname,
                    onValueChange = onHostnameChange,
                    label = { Text("Hostname") },
                    placeholder = { Text("radio.yoursite.com") },
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = onCreateTunnel,
                enabled = zoneId.isNotBlank() && hostname.isNotBlank() && !actionInFlight,
                modifier = Modifier.padding(top = 10.dp),
            ) { Text("Create tunnel") }
        }
        PanelCard(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentPadding = PaddingValues(14.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "paperweighthq.com address", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Provision a managed address for the claimed station slug.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onHqTunnel, enabled = paperweighthqAvailable && !actionInFlight) {
                    Text("Get free address")
                }
            }
        }
    }
}

@Composable
private fun SearchablePanel(searchable: Boolean, missing: List<String>, actionInFlight: Boolean, onToggle: (Boolean) -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Station search", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (missing.isNotEmpty()) "Requires ${missing.joinToString(" and ")}." else "Reachability is verified when you switch this on.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Switch(
            checked = searchable,
            onCheckedChange = onToggle,
            enabled = missing.isEmpty() && !actionInFlight,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun TelemetryPanel(
    configured: Boolean,
    secret: String,
    onSecretChange: (String) -> Unit,
    hasSlug: Boolean,
    actionInFlight: Boolean,
    onSave: () -> Unit,
    onRegister: () -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Telemetry secret", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (configured) "Configured with PaperweightHQ." else "Not configured.",
            style = MaterialTheme.typography.labelSmall,
            color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        OutlinedTextField(
            value = secret,
            onValueChange = onSecretChange,
            label = { Text("Shared secret") },
            placeholder = { Text("Paste system.pape secret") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            OutlinedButton(onClick = onSave, enabled = secret.isNotBlank() && !actionInFlight) { Text("Save") }
            Button(onClick = onRegister, enabled = hasSlug && !actionInFlight) { Text("Register") }
        }
    }
}

@Composable
private fun SetupProgressPanel(
    milestones: Map<String, String>,
    signupDismissed: Boolean,
    signupEmail: String,
    onSignupEmailChange: (String) -> Unit,
    actionInFlight: Boolean,
    onSignup: () -> Unit,
    onDismiss: () -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Setup progress", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SETUP_STEPS.forEach { (key, label) ->
                val done = milestones.containsKey(key)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!signupDismissed && milestones.containsKey("first_listener")) {
            OutlinedTextField(
                value = signupEmail,
                onValueChange = onSignupEmailChange,
                label = { Text("Product updates email") },
                placeholder = { Text("you@example.com") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                Button(onClick = onSignup, enabled = signupEmail.isNotBlank() && !actionInFlight) { Text("Sign up") }
                OutlinedButton(onClick = onDismiss, enabled = !actionInFlight) { Text("Dismiss") }
            }
        }
    }
}
