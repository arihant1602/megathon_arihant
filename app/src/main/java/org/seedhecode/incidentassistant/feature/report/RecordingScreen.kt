package org.seedhecode.incidentassistant.feature.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.seedhecode.core.audio.VoiceRecording
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    uiState: ReportUiState,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onPlay: (VoiceRecording) -> Unit,
    onDelete: (VoiceRecording) -> Unit,
    onRequestPermission: () -> Unit,
    permissionRationale: Boolean,
    settingsRequired: Boolean,
    onDismissDialog: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var recordingToDelete by remember { mutableStateOf<VoiceRecording?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incident Assistant", fontWeight = FontWeight.Medium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status & Timer Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatDuration(uiState.durationMs),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    org.seedhecode.incidentassistant.ui.components.AuroraVoiceOrb(
                        audioLevel = uiState.audioLevel,
                        captureState = uiState.captureState,
                        modifier = Modifier.size(160.dp)
                    )
                }
            }

            // Record / Stop Button
            val isRecording = uiState.captureState == CaptureState.RECORDING
            val isBusy = uiState.captureState == CaptureState.STARTING || uiState.captureState == CaptureState.SAVING

            Button(
                onClick = {
                    if (isRecording) {
                        onStopCapture()
                    } else if (!isBusy) {
                        onStartCapture()
                    }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (uiState.captureState) {
                        CaptureState.IDLE -> "Record Incident"
                        CaptureState.STARTING -> "Starting..."
                        CaptureState.RECORDING -> "Stop and Save"
                        CaptureState.SAVING -> "Saving..."
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Library Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recorded Incidents",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = "${uiState.recordings.size} clips",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Recordings List
            if (uiState.recordings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recordings yet. Tap Record Incident to start.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.recordings, key = { it.file.absolutePath }) { recording ->
                        RecordingCard(
                            recording = recording,
                            isPlaying = uiState.playingFile == recording.file,
                            isIdle = uiState.captureState == CaptureState.IDLE,
                            onPlay = { onPlay(recording) },
                            onDelete = { recordingToDelete = recording }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (recordingToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = { Text("Delete Recording") },
            text = { Text("Are you sure you want to delete this incident recording? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordingToDelete?.let(onDelete)
                        recordingToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Permission Rationale Dialog
    if (permissionRationale) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("Microphone Permission Required") },
            text = { Text("SentinelAI needs microphone access to record incident audio securely on-device.") },
            confirmButton = {
                TextButton(onClick = {
                    onDismissDialog()
                    onRequestPermission()
                }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // Settings Required Dialog
    if (settingsRequired) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("Permission Required") },
            text = { Text("Microphone permission was denied. Please enable it in application settings to record incidents.") },
            confirmButton = {
                TextButton(onClick = {
                    onDismissDialog()
                    onOpenSettings()
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RecordingCard(
    recording: VoiceRecording,
    isPlaying: Boolean,
    isIdle: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val date = Date(recording.createdAtMs)
    val timeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    val dateStr = DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Incident Clip ($timeStr)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatDuration(recording.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$dateStr • ${String.format(Locale.US, "%.1f", recording.sampleRateHz / 1000.0)} kHz WAV",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlay,
                    enabled = isIdle,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isPlaying) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isPlaying) "Stop" else "Play")
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = isIdle,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Delete")
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds)
}
