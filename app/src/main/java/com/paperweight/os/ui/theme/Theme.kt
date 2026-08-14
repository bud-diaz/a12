package com.paperweight.os.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PaperweightColorScheme = darkColorScheme(
    background = PaperweightBackground,
    surface = PaperweightBackground,
    surfaceVariant = PaperweightCard,
    primary = PaperweightPrimary,
    onPrimary = PaperweightPrimaryForeground,
    secondary = PaperweightSecondary,
    onSecondary = PaperweightSecondaryForeground,
    tertiary = PaperweightAccent,
    onTertiary = PaperweightAccentForeground,
    error = PaperweightDestructive,
    onError = PaperweightDestructiveForeground,
    onBackground = PaperweightForeground,
    onSurface = PaperweightForeground,
    onSurfaceVariant = PaperweightMutedForeground,
    outline = PaperweightBorder,
    outlineVariant = PaperweightCardBorder,
)

@Composable
fun PaperweightOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperweightColorScheme,
        shapes = PaperweightShapes,
        typography = PaperweightTypography,
        content = content
    )
}
