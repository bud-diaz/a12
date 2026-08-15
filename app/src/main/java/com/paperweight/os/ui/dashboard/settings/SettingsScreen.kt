package com.paperweight.os.ui.dashboard.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperweight.os.ui.components.PanelCard
import com.paperweight.os.ui.components.ScreenStateScaffold
import com.paperweight.os.ui.components.ViewHeader

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    ScreenStateScaffold(state = state, onRetry = viewModel::load) { data ->
        var retentionText by rememberSaveable(data.backupRetentionCount) { mutableStateOf(data.backupRetentionCount.toString()) }
        var intervalText by rememberSaveable(data.backupIntervalHours) { mutableStateOf(data.backupIntervalHours.toString()) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ViewHeader(
                    eyebrow = "Device / Settings",
                    title = "Local backup and recovery.",
                    description = "Back up the on-device database and non-secret config to the same SD-card Paperweight folder used by the Vault.",
                )
            }
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Backup target", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (data.vaultTreeGranted) {
                            "Ready. The saved SD-card Paperweight folder grant will be reused for Paperweight/backups/."
                        } else {
                            "Not ready. Open Vault first and choose the SD-card folder named Paperweight."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    ) {
                        OutlinedTextField(
                            value = retentionText,
                            onValueChange = { retentionText = it.filter(Char::isDigit) },
                            label = { Text("Snapshots to keep") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it.filter(Char::isDigit) },
                            label = { Text("Interval hours") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                        Button(
                            onClick = {
                                viewModel.saveBackupSettings(
                                    retentionText.toIntOrNull() ?: data.backupRetentionCount,
                                    intervalText.toIntOrNull() ?: data.backupIntervalHours,
                                )
                            },
                            enabled = !data.actionInFlight,
                        ) { Text("Save schedule") }
                        OutlinedButton(
                            onClick = viewModel::backUpNow,
                            enabled = !data.actionInFlight && data.vaultTreeGranted,
                        ) { Text("Back up now") }
                    }
                    data.lastBackupName?.let {
                        Text(
                            text = "Last backup: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
            item {
                PanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Recovery info", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Automatic backups intentionally exclude secrets that cannot survive Android Keystore reset. Use this before real reprovisioning once reachability secrets exist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(
                        onClick = viewModel::showRecoveryInfo,
                        enabled = !data.actionInFlight,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Show recovery info") }
                    data.recoveryInfo?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            data.actionMessage?.let { message ->
                item {
                    Text(text = message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
