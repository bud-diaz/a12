package com.paperweight.os.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PaperweightColorScheme = darkColorScheme(
    background = PaperweightBackground,
    surface = PaperweightSurface,
    primary = PaperweightAccent,
    onBackground = PaperweightOnBackground,
    onSurface = PaperweightOnBackground
)

// Typography.kt (DM Serif Display / Space Mono, bundled via res/font/) is pending —
// the font resources have not been supplied yet, so this theme falls back to
// MaterialTheme's default type scale until they are.
@Composable
fun PaperweightOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperweightColorScheme,
        shapes = PaperweightShapes,
        content = content
    )
}
