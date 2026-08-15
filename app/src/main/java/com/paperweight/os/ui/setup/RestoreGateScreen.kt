package com.paperweight.os.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paperweight.os.backup.BackupSnapshot

sealed interface RestoreGateState {
    data object NeedsFolderGrant : RestoreGateState
    data object Checking : RestoreGateState
    data class OfferRestore(val snapshot: BackupSnapshot) : RestoreGateState
    data object NoBackupFound : RestoreGateState
    data object Restoring : RestoreGateState
    data class Error(val message: String) : RestoreGateState
}

@Composable
fun RestoreGateScreen(
    state: RestoreGateState,
    onChooseFolder: () -> Unit,
    onRestore: () -> Unit,
    onStartFresh: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text(text = "Backup & recovery", style = MaterialTheme.typography.headlineSmall)
            when (state) {
                RestoreGateState.NeedsFolderGrant -> {
                    Text(
                        text = "Before the dashboard opens, choose the SD-card folder named Paperweight. Paperweight OS will check it for existing backups and reuse the same folder for Vault media.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onChooseFolder) { Text("Choose Paperweight folder") }
                }
                RestoreGateState.Checking -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text("Checking the SD card for backups…")
                    }
                }
                is RestoreGateState.OfferRestore -> {
                    Text(
                        text = "Found backup ${state.snapshot.displayName}. Restore it before Room opens, or start fresh.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onRestore) { Text("Restore backup") }
                        OutlinedButton(onClick = onStartFresh) { Text("Start fresh") }
                    }
                }
                RestoreGateState.NoBackupFound -> {
                    Text(
                        text = "No existing backup was found in Paperweight/backups/. Start fresh to continue to the dashboard.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onStartFresh) { Text("Start fresh") }
                }
                RestoreGateState.Restoring -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Text("Restoring backup before opening the database…")
                    }
                }
                is RestoreGateState.Error -> {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onRetry) { Text("Retry") }
                        OutlinedButton(onClick = onChooseFolder) { Text("Choose folder again") }
                    }
                }
            }
        }
    }
}
