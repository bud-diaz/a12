package com.paperweight.os.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.paperweight.os.ui.theme.PaperweightCard

// Studio's .panel / .panel-subtle — a translucent-white-over-black elevated
// surface (see studio/src/index.css). Compose has no native "vibrancy" blur
// on arbitrary content, so this matches the flat translucent-fill look,
// which is what actually renders in Studio (no backdrop-filter blur there).
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(color = PaperweightCard, shape = shape)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = shape)
            .padding(contentPadding),
    ) {
        content()
    }
}
