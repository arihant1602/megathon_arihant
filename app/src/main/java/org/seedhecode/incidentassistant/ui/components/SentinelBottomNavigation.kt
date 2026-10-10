package org.seedhecode.incidentassistant.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import org.seedhecode.incidentassistant.ui.navigation.Screen
import org.seedhecode.incidentassistant.ui.session.AppRole

@Composable
fun SentinelBottomNavigation(
    currentRoute: String?, role: AppRole, enabled: Boolean = true, onNavigate: (String) -> Unit
) {
    val destinations = if (role == AppRole.EMPLOYEE) listOf(
        Triple(Screen.Home.route, "Home", Icons.Default.Home),
        Triple(Screen.Report.route, "Report", Icons.Default.Mic),
        Triple(Screen.History.route, "History", Icons.Default.History)
    ) else listOf(
        Triple(Screen.Admin.route, "Admin Demo", Icons.Default.AdminPanelSettings),
        Triple(Screen.History.route, "History", Icons.Default.History)
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        destinations.forEach { (route, label, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) }, 
                label = { Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) },
                selected = currentRoute == route, enabled = enabled,
                onClick = { onNavigate(route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
