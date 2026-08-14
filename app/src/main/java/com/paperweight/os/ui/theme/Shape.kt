package com.paperweight.os.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Matches the corner radii actually used across studio/src/views/*.tsx
// (Tailwind rounded-lg/xl/2xl/3xl), not the abstract shadcn --radius token.
val PaperweightShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), // rounded-lg: chips, small buttons
    small = RoundedCornerShape(12.dp), // rounded-xl: inputs, most buttons
    medium = RoundedCornerShape(16.dp), // rounded-2xl: panels/cards (most common)
    large = RoundedCornerShape(24.dp), // rounded-3xl: hero panels
    extraLarge = RoundedCornerShape(24.dp),
)
