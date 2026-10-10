package org.seedhecode.incidentassistant.ui.screens.admin

import org.seedhecode.incidentassistant.ui.components.AdaptiveActions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.seedhecode.incidentassistant.demo.DemoDataProvider
import org.seedhecode.incidentassistant.demo.DemoIncidentStatus
import org.seedhecode.incidentassistant.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDetailScreen(
    incidentId: String,
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val incident = DemoDataProvider.getIncidentById(incidentId)

    if (incident == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Incident not found.")
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Admin Demo: ${incident.id}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Map Placeholder
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Demo Floor Plan — No live positioning", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Text(
                text = incident.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AdminDetailRow("Location", incident.location)
                    AdminDetailRow("Department", incident.department)
                    AdminDetailRow("Priority", incident.priority.name)
                    AdminDetailRow("Current Status", incident.status.displayName)
                }
            }

            Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(text = incident.description, style = MaterialTheme.typography.bodyLarge)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Admin Actions (Demo)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)

            AdaptiveActions() { itemModifier ->
                if (incident.status != DemoIncidentStatus.PENDING_REVIEW) {
                    Button(
                        onClick = { viewModel.updateIncidentStatus(incident.id, DemoIncidentStatus.PENDING_REVIEW) },
                        modifier = itemModifier
                    ) {
                        Text("Mark Pending")
                    }
                }
                if (incident.status != DemoIncidentStatus.RESOLVED) {
                    Button(
                        onClick = { viewModel.updateIncidentStatus(incident.id, DemoIncidentStatus.RESOLVED) },
                        modifier = itemModifier,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Mark Resolved")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminDetailRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label,  style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value,  style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
