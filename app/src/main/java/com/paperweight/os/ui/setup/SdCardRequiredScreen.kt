package com.paperweight.os.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paperweight.os.storage.SdCardDetector

// Blocking gate: Paperweight OS requires a removable SD card of at least
// 2GB (SdCardDetector.MIN_CAPACITY_BYTES) as its default vault + backup
// storage. This screen is shown instead of the dashboard whenever
// SdCardMountState reports no valid card present — it clears itself
// automatically (no manual recheck needed) once a qualifying card is
// inserted, since the caller observes a live Flow.
@Composable
fun SdCardRequiredScreen(modifier: Modifier = Modifier) {
    val minGb = SdCardDetector.MIN_CAPACITY_BYTES / (1024L * 1024L * 1024L)
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "SD card required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Paperweight OS stores its media vault and backups on a removable " +
                "SD card, not internal storage. Insert a card of at least ${minGb}GB " +
                "to continue — this screen clears itself automatically once one is detected.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
