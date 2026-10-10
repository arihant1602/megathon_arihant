package org.seedhecode.incidentassistant.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.seedhecode.incidentassistant.demo.DemoDataProvider
import org.seedhecode.incidentassistant.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    incidentId: String,
    onBack: () -> Unit
) {
    val incident = DemoDataProvider.getIncidentById(incidentId)

    if (incident == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Report not found.")
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(incident.id, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium) },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(text = incident.status.displayName)
                Text(
                    text = incident.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
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
                    DetailRow("Category", incident.category)
                    DetailRow("Department", incident.department)
                    DetailRow("Priority", incident.priority.name)
                    DetailRow("Location", incident.location)
                }
            }

            Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(text = incident.description, style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Demo Preview — Simulated Incident Data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label,  style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value,  style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
