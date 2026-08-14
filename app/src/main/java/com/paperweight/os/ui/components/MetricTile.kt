package com.paperweight.os.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Studio's Metric (components/primitives.tsx): label + big number + a
// change/sub-label line, with a small icon chip. accent picks the
// change-line color, matching Studio's lime/coral/blue variants.
enum class MetricAccent { Primary, Coral, Neutral }

@Composable
fun MetricTile(
    label: String,
    value: String,
    change: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: MetricAccent = MetricAccent.Primary,
) {
    PanelCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accent == MetricAccent.Coral) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = change,
            style = MaterialTheme.typography.labelMedium,
            color = if (accent == MetricAccent.Coral) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
