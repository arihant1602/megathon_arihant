package org.seedhecode.incidentassistant.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.seedhecode.core.audio.VoiceRecording
import org.seedhecode.incidentassistant.ui.components.EmptyState
import org.seedhecode.incidentassistant.ui.components.IncidentCard
import org.seedhecode.incidentassistant.ui.screens.voice.RecordingCardItem

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    recordings: List<VoiceRecording>,
    playingFile: java.io.File?,
    isIdle: Boolean,
    onPlay: (VoiceRecording) -> Unit,
    onDelete: (VoiceRecording) -> Unit,
    onNavigateToReportDetail: (String) -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Demo Reports", "Local WAVs")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        if (selectedTabIndex == 0) {
            DemoReportsTab(viewModel, onNavigateToReportDetail)
        } else {
            LocalRecordingsTab(recordings, playingFile, isIdle, onPlay, onDelete)
        }
    }
}

@Composable
fun DemoReportsTab(
    viewModel: HistoryViewModel,
    onNavigateToReportDetail: (String) -> Unit
) {
    val filterState by viewModel.filterState.collectAsState()
    val reports by viewModel.filteredDemoReports.collectAsState()

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = filterState.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Search Incidents") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (reports.isEmpty()) {
            EmptyState(message = "No demo reports match your filters.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(reports, key = { it.id }) { item ->
                    Box(modifier = Modifier.clickable { onNavigateToReportDetail(item.id) }) {
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
        }
    }
}

@Composable
fun LocalRecordingsTab(
    recordings: List<VoiceRecording>,
    playingFile: java.io.File?,
    isIdle: Boolean,
    onPlay: (VoiceRecording) -> Unit,
    onDelete: (VoiceRecording) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Recorded Incident Library",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Secure local WAV recordings stored on-device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recordings.isEmpty()) {
            item {
                EmptyState(message = "No recorded incidents found in local storage.")
            }
        } else {
            items(recordings, key = { it.file.absolutePath }) { recording ->
                RecordingCardItem(
                    recording = recording,
                    isPlaying = playingFile == recording.file,
                    isIdle = isIdle,
                    onPlay = { onPlay(recording) },
                    onDelete = { onDelete(recording) }
                )
            }
        }
    }
}
