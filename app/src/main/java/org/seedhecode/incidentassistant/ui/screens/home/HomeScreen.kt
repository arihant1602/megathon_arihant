package org.seedhecode.incidentassistant.ui.screens.home

import org.seedhecode.incidentassistant.ui.components.AdaptiveActions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.seedhecode.incidentassistant.demo.DemoDataProvider
import org.seedhecode.incidentassistant.ui.components.IncidentCard
import org.seedhecode.incidentassistant.ui.components.PrimaryActionButton
import org.seedhecode.incidentassistant.ui.components.SectionHeader
import org.seedhecode.incidentassistant.ui.components.StatusChip

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onNavigateToReport: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToForm: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Branding & Offline Status Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "SENTINEL AI",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Qualcomm Megathon X",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        StatusChip(
                            text = "● Offline Ready",
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Secure on-device voice incident reporting and offline intelligence.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Primary & Secondary Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryActionButton(
                    text = "Record Voice Incident",
                    onClick = onNavigateToReport,
                    icon = Icons.Default.Mic
                )
                OutlinedButton(
                    onClick = onNavigateToForm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Icon(imageVector = Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Dynamic Incident Form", fontWeight = FontWeight.Medium)
                }
                OutlinedButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(text = "View Recorded Incidents & History", fontWeight = FontWeight.Medium)
                }
            }
        }

        // Summary Cards Grid (Drafts, Submitted, Pending)
        item {
            AdaptiveActions(count = 3, minimumItemWidth = 88) { itemModifier ->
                SummaryCard(
                    title = "Drafts",
                    count = DemoDataProvider.summary.draftCount.toString(),
                    modifier = itemModifier
                )
                SummaryCard(
                    title = "Submitted",
                    count = DemoDataProvider.summary.submittedCount.toString(),
                    modifier = itemModifier
                )
                SummaryCard(
                    title = "Pending",
                    count = DemoDataProvider.summary.pendingReviewCount.toString(),
                    modifier = itemModifier
                )
            }
        }

        // Recent Activity Section Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(
                title = "Recent Activity",
                actionText = "See All",
                onActionClick = onNavigateToHistory
            )
        }

        // Recent Activities List
        items(DemoDataProvider.demoIncidents.value, key = { it.id }) { item ->
            IncidentCard(
                id = item.id,
                title = item.title,
                category = item.category,
                timestamp = item.timestamp,
                status = item.status.displayName,
                department = item.department
            )
        }
    }
}

@Composable
fun SummaryCard(title: String, count: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
