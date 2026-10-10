package org.seedhecode.incidentassistant.ui.screens.incident

import org.seedhecode.incidentassistant.ui.components.AdaptiveActions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.seedhecode.incidentassistant.ui.components.PrimaryActionButton
import org.seedhecode.incidentassistant.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncidentFormScreen(
    viewModel: IncidentDraftViewModel,
    onNavigateToClarification: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showDraftSavedMessage by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Dynamic Incident Form",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Normal
                        )
                        StatusChip(text = "Step 1 of 3")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Review and refine auto-populated details. Category-specific fields update dynamically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Category Selector
        item {
            Text(text = "Incident Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IncidentCategory.values().forEach { cat ->
                    val selected = state.category == cat
                    OutlinedCard(
                        onClick = { viewModel.setCategory(cat) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = cat.displayName, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                            if (selected) {
                                StatusChip(text = "Selected", containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Common Fields: Location & Urgency
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "General Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.location,
                onValueChange = { viewModel.setLocation(it) },
                label = { Text("Incident Location *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.urgency,
                onValueChange = { viewModel.setUrgency(it) },
                label = { Text("Urgency (Low / Medium / High / Critical) *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Dynamic Category Specific Fields
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "${state.category.displayName} Specifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            
            when (state.category) {
                IncidentCategory.MEDICAL -> {
                    OutlinedTextField(
                        value = state.medicalPatientCondition,
                        onValueChange = { viewModel.updateField("condition", it) },
                        label = { Text("Patient Condition") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.medicalAffectedPeople,
                        onValueChange = { viewModel.updateField("affected", it) },
                        label = { Text("Number of Affected People") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                IncidentCategory.FIRE -> {
                    OutlinedTextField(
                        value = state.fireSmokeVisible,
                        onValueChange = { viewModel.updateField("smoke", it) },
                        label = { Text("Visible Smoke / Flames") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.fireEvacuationStatus,
                        onValueChange = { viewModel.updateField("evacuation", it) },
                        label = { Text("Evacuation Status") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                IncidentCategory.SECURITY -> {
                    OutlinedTextField(
                        value = state.securityDescription,
                        onValueChange = { viewModel.updateField("description", it) },
                        label = { Text("Incident Description") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.securityPeopleInvolved,
                        onValueChange = { viewModel.updateField("people", it) },
                        label = { Text("People Involved / Suspects") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                IncidentCategory.MAINTENANCE -> {
                    OutlinedTextField(
                        value = state.maintenanceIssueType,
                        onValueChange = { viewModel.updateField("issue", it) },
                        label = { Text("Issue Type") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.maintenanceEquipment,
                        onValueChange = { viewModel.updateField("equipment", it) },
                        label = { Text("Affected Equipment") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                IncidentCategory.GENERAL -> {
                    OutlinedTextField(
                        value = state.generalDescription,
                        onValueChange = { viewModel.updateField("description", it) },
                        label = { Text("General Assistance Details") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // Actions: Save Draft & Continue
        item {
            Spacer(modifier = Modifier.height(8.dp))
            AdaptiveActions() { itemModifier ->
                OutlinedButton(
                    onClick = { showDraftSavedMessage = true },
                    modifier = itemModifier
                        .heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Save Draft")
                }
                PrimaryActionButton(
                    text = "Continue",
                    onClick = onNavigateToClarification,
                    modifier = itemModifier,
                    icon = Icons.Default.ArrowForward
                )
            }
            if (showDraftSavedMessage) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Draft saved locally to device storage.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
