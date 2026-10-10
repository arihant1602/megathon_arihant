package org.seedhecode.incidentassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentinelTopBar(title: String, onSwitchRole: () -> Unit, navigationEnabled: Boolean = true) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Column(Modifier.align(Alignment.CenterVertically).padding(end = 12.dp)) {
                if (title == "Admin Demo · History") {
                    Text("Admin Demo", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (title.endsWith("History")) "History" else title,
                    style = if (title.endsWith("History")) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onSwitchRole, enabled = navigationEnabled) {
                Text("Switch Role", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
