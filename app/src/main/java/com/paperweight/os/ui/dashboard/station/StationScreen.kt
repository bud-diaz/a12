package com.paperweight.os.ui.dashboard.station

// Rewired for Phase 9 (frp reachability) — see StationViewModel's class doc
// for what this replaced.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.QrCodeImage
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader

@Composable
fun StationScreen(viewModel: StationViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val copy: (String) -> Unit = { url -> clipboard.setText(AnnotatedString(url)); viewModel.notify("Copied.") }

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        var slugInput by rememberSaveable(data.slug) { mutableStateOf(data.slug) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Signal / Station",
                    title = "Make the station reachable.",
                    description = "A LAN URL for listeners on this Wi-Fi, and an optional public *.paperweighthq.com address via frp.",
                )
            }
            item { LanUrlPanel(lanUrl = data.lanUrl, onCopy = copy) }
            item {
                PublicUrlPanel(
                    data = data,
                    slugInput = slugInput,
                    onSlugChange = { slugInput = it },
                    onRegister = { viewModel.register(slugInput) },
                    onDisconnect = viewModel::disconnect,
                    onCopy = copy,
                )
            }
            data.actionMessage?.let { message ->
                item {
                    Text(text = message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun LanUrlPanel(lanUrl: String?, onCopy: (String) -> Unit) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "LAN listening URL", style = MaterialTheme.typography.titleMedium)
        Text(
            text = lanUrl ?: "Waiting for a Wi-Fi address — make sure the broadcast service is running and the device is on Wi-Fi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (lanUrl != null) {
            OutlinedButton(onClick = { onCopy(lanUrl) }, modifier = Modifier.padding(top = 12.dp)) { Text("Copy LAN URL") }
        }
    }
}

@Composable
private fun PublicUrlPanel(
    data: StationUiState,
    slugInput: String,
    onSlugChange: (String) -> Unit,
    onRegister: () -> Unit,
    onDisconnect: () -> Unit,
    onCopy: (String) -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Public address (frp)", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Tunnel: ${data.tunnelStatusText}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        data.tunnelError?.let {
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
        OutlinedTextField(
            value = slugInput,
            onValueChange = onSlugChange,
            label = { Text("Station slug") },
            placeholder = { Text("yourstation") },
            enabled = !data.actionInFlight,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
            Button(onClick = onRegister, enabled = slugInput.isNotBlank() && !data.actionInFlight) {
                Text(if (data.publicUrl == null) "Register & get public URL" else "Re-register")
            }
            if (data.publicUrl != null) {
                OutlinedButton(onClick = onDisconnect, enabled = !data.actionInFlight) { Text("Disconnect") }
            }
        }
        data.publicUrl?.let { url ->
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(text = url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { onCopy(url) }) { Text("Copy public URL") }
                }
                QrCodeImage(content = url, modifier = Modifier.size(180.dp).padding(top = 14.dp))
            }
        }
    }
}
