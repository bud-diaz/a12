package com.paperweight.os.ui.theme

import androidx.compose.ui.graphics.Color

// Matches paperweightv1/studio/src/index.css's production tokens (converted
// from HSL to hex), not the abandoned studio-mission-control.html mockup.
val PaperweightBackground = Color(0xFF000000)
val PaperweightForeground = Color(0xFFEBEEF4)

val PaperweightPrimary = Color(0xFFE4FF4D)
val PaperweightPrimaryForeground = Color(0xFF000000)

val PaperweightAccent = Color(0xFFF27969)
val PaperweightAccentForeground = Color(0xFF000000)

val PaperweightDestructive = Color(0xFFE75A50)
val PaperweightDestructiveForeground = Color(0xFFFFFFFF)

val PaperweightSecondaryForeground = Color(0xFFD2D8E4)
val PaperweightMutedForeground = Color(0xFF8B91A7)
val PaperweightSidebarForeground = Color(0xFFE2E5EE)

// White-on-black translucent surfaces (alpha channel carried over from
// index.css's `0 0% 100% / .NN` tokens) — Studio's .panel / .panel-subtle.
val PaperweightCard = Color(0x14FFFFFF) // 8% white
val PaperweightCardBorder = Color(0x29FFFFFF) // 16% white
val PaperweightBorder = Color(0x24FFFFFF) // 14% white
val PaperweightSecondary = Color(0x1AFFFFFF) // 10% white
val PaperweightMuted = Color(0x14FFFFFF) // 8% white
