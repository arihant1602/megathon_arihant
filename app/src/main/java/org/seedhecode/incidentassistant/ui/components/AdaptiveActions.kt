package org.seedhecode.incidentassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Reflow controls when their available width cannot comfortably hold scaled labels. */
@Composable
fun AdaptiveActions(count: Int = 2, minimumItemWidth: Int = 136, content: @Composable (Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < (count * minimumItemWidth * fontScale + (count - 1) * 12).dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                content(Modifier.weight(1f))
            }
        }
    }
}
