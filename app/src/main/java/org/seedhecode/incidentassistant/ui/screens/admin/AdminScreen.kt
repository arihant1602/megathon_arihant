package org.seedhecode.incidentassistant.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.seedhecode.incidentassistant.demo.DemoActivityItem
import org.seedhecode.incidentassistant.demo.DemoDataProvider
import org.seedhecode.incidentassistant.demo.DemoIncidentStatus
import org.seedhecode.incidentassistant.ui.theme.IncidentAssistantTheme

@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onSwitchRole: () -> Unit,
    navigationEnabled: Boolean = true,
    onNavigateToAdminDetail: (String) -> Unit
) {
    val queue by viewModel.adminQueue.collectAsState()
    AdminDashboard(queue, onSwitchRole, navigationEnabled, onNavigateToAdminDetail)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminDashboard(
    queue: List<DemoActivityItem>,
    onSwitchRole: () -> Unit,
    navigationEnabled: Boolean,
    onNavigateToAdminDetail: (String) -> Unit
) {
    var department by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    val departments = queue.map { it.department }.filter { it.isNotBlank() }.distinct()
    val filtered = queue.filter {
        (department == null || it.department == department) &&
            (status == null || it.status.name == status)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gutter = if (maxWidth < 360.dp) 16.dp else 20.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth()) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "SENTINEL AI / ADMIN",
                            modifier = Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                        TextButton(onClick = onSwitchRole, enabled = navigationEnabled) {
                            Text("Switch Role")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Incident command.",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Monitor and manage incident reports.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    AdminBadge("Demo")
                    Spacer(Modifier.height(16.dp))
                }
            }
            item(key = "queue-heading") {
                Text(
                    "Incident queue",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminFilters("Department", listOf(null to "All departments") + departments.map { it to it }, department) {
                        department = it
                    }
                    AdminFilters("Status", listOf(null to "All statuses") + DemoIncidentStatus.values().map {
                        it.name to it.displayName.removeSuffix(" (Demo)")
                    }, status) { status = it }
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No matching incidents", style = MaterialTheme.typography.titleMedium)
                        Text("Try another department or status.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { department = null; status = null }) { Text("Clear filters") }
                    }
                }
            }
            items(filtered, key = { it.id }) { incident ->
                AdminIncidentCard(incident, navigationEnabled) { onNavigateToAdminDetail(incident.id) }
            }
        }
    }
}

@Composable
private fun AdminFilters(
    label: String,
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options, key = { it.first ?: "all" }) { (value, title) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = { Text(title, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (value == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun AdminBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminIncidentCard(incident: DemoActivityItem, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Flow onto a second line at large font scales instead of squeezing the ID or badge.
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(incident.id, modifier = Modifier.padding(end = 12.dp, top = 5.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                AdminBadge(incident.status.displayName.removeSuffix(" (Demo)"))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(incident.category, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(incident.title, style = MaterialTheme.typography.titleMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(incident.location, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (incident.department.isNotBlank()) {
                    Text("Assigned to ${incident.department}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${incident.priority.name.lowercase().replaceFirstChar { it.uppercase() }} priority",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("View details", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Preview(name = "320dp · 1.0", widthDp = 320, heightDp = 800, fontScale = 1f)
@Preview(name = "320dp · 1.3", widthDp = 320, heightDp = 800, fontScale = 1.3f)
@Preview(name = "320dp · 1.5", widthDp = 320, heightDp = 800, fontScale = 1.5f)
@Preview(name = "Phone · 1.0", widthDp = 393, heightDp = 851, fontScale = 1f)
@Preview(name = "Phone · 1.3", widthDp = 393, heightDp = 851, fontScale = 1.3f)
@Preview(name = "Phone · 1.5", widthDp = 393, heightDp = 851, fontScale = 1.5f)
@Composable
private fun AdminDashboardPreview() {
    IncidentAssistantTheme {
        Surface { AdminDashboard(DemoDataProvider.demoIncidents.value, {}, true, {}) }
    }
}
