package org.seedhecode.incidentassistant.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.seedhecode.incidentassistant.engine.AssistantView
import org.seedhecode.incidentassistant.feature.assistant.AssistantUiState
import org.seedhecode.incidentassistant.feature.assistant.Phase
import org.seedhecode.incidentassistant.feature.report.CaptureState
import org.seedhecode.incidentassistant.ui.components.AuroraVoiceOrb
import org.seedhecode.incidentassistant.ui.components.StatusChip
import java.util.Locale

/** The incident assistant: talk (or type), it asks back, the form fills live. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    state: AssistantUiState,
    onMic: () -> Unit,
    onSend: (String) -> Unit,
    onDone: () -> Unit,
    onNewReport: () -> Unit,
    onRetryLoad: () -> Unit,
    onToggleSpeech: () -> Unit,
) {
    val view = state.view
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Header(state, onToggleSpeech)
        when (state.phase) {
            Phase.LOADING -> Centered {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Loading Laya (NPU), Whisper and Qwen…", style = MaterialTheme.typography.bodyLarge)
            }
            Phase.ERROR -> Centered {
                Text("The on-device models could not be loaded.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(state.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetryLoad) { Text("Retry") }
            }
            else -> {
                Box(Modifier.weight(1f)) { Conversation(state) }
                if (view != null) FormCard(view)
                Controls(state, onMic, onSend, onDone, onNewReport)
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally, content = content)

@Composable
private fun Header(state: AssistantUiState, onToggleSpeech: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Sentinel AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 2)
        }
        IconButton(onClick = onToggleSpeech) {
            Icon(if (state.speakQuestions) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = if (state.speakQuestions) "Mute spoken questions" else "Speak questions")
        }
    }
}

@Composable
private fun Conversation(state: AssistantUiState) {
    val view = state.view
    val chat = view?.chat ?: emptyList()
    val listening = state.phase == Phase.LISTENING || state.phase == Phase.THINKING
    val live = state.caption.takeIf { listening && it.isNotBlank() }
    val items = buildList {
        add("assistant" to "Hi, I'm the incident assistant. Tell me what happened and where you are.")
        addAll(chat)
        if (live != null) add("live" to live)
        if (state.phase == Phase.THINKING) add("thinking" to "")
    }
    val list = rememberLazyListState()
    LaunchedEffect(items.size, live) { list.animateScrollToItem(items.size - 1) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = list,
        verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(items) { (who, text) -> Bubble(who, text) }
    }
}

@Composable
private fun Bubble(who: String, text: String) {
    val mine = who == "user" || who == "live"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        val color = when (who) {
            "user" -> MaterialTheme.colorScheme.primary
            "live" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val textColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        Surface(color = color, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            if (who == "thinking") Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp)); Text("Thinking…", color = textColor)
            } else Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = textColor,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormCard(v: AssistantView) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    v.done && v.outcome == "submitted" -> StatusChip("Filed", Color(0xFF1B5E20), Color.White)
                    v.done -> StatusChip("Not an incident", MaterialTheme.colorScheme.outline, Color.White)
                    v.ready -> StatusChip("Ready to file · say 'done'", Color(0xFF2E7D32), Color.White)
                    else -> StatusChip("Collecting essentials", Color(0xFFF9A825), Color.Black)
                }
                v.routing?.let { StatusChip("${it.priority} ${it.priorityLabel} · SLA ${it.slaMin} min",
                    if (it.priority == "P1") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, Color.White) }
            }
            Spacer(Modifier.height(8.dp))
            Essential("WHAT", v.typeLabel?.let { label -> label + (v.typeSource?.let { " (${if (it == "user-confirmed") "you confirmed" else String.format(Locale.US, "%.0f%%", v.typeConf * 100)})" } ?: "") })
            Essential("WHO", v.team)
            val building = v.values["building"]?.value; val floor = v.values["floor"]?.value
            Essential("WHERE", if (building == null && floor == null) null else
                "building ${building ?: "?"}, floor ${floor ?: "?"}" + (v.values["space_id"]?.value?.let { ", $it" } ?: ""))
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val shown = v.requiredFields + v.values.keys.filter { it !in v.requiredFields }
                for (f in shown.filter { it !in setOf("building", "floor") }) {
                    val value = v.values[f]?.value
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(f.replace('_', ' '), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                        Text(value?.let { fmt(it) } ?: "—", style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (value != null) FontWeight.Medium else FontWeight.Normal)
                    }
                }
                v.routing?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Notify: ${it.notify.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    if (it.secondary.isNotEmpty()) Text("Also check: ${it.secondary.joinToString()}", style = MaterialTheme.typography.bodySmall)
                }
                if (v.summary.isNotBlank()) Text("Summary: ${v.summary}", style = MaterialTheme.typography.bodySmall)
                v.timings?.let { t ->
                    Spacer(Modifier.height(6.dp))
                    Text(buildString {
                        t.whisperMs?.let { append("Whisper $it ms · ") }
                        append(String.format(Locale.US, "Laya NPU %.0f ms", t.classifierMs))
                        t.llm.forEach { append(" · Qwen ${it.kind} ${it.ms} ms") }
                        append(" · turn ${t.turnMs} ms")
                    }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                if (v.topTypes.isNotEmpty()) Text("Classifier: " + v.topTypes.take(3).joinToString { (t, p) ->
                    "$t ${String.format(Locale.US, "%.2f", p)}" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            } else Text("Tap for all fields", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun fmt(v: Any) = when (v) { true -> "yes"; false -> "no"; else -> v.toString() }

@Composable
private fun Essential(label: String, value: String?) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("! $label", Modifier.width(76.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Text(value ?: "needed", style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Controls(state: AssistantUiState, onMic: () -> Unit, onSend: (String) -> Unit, onDone: () -> Unit, onNewReport: () -> Unit) {
    val view = state.view
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (view?.done == true) {
            Button(onClick = onNewReport, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New report")
            }
            return@Column
        }
        val capture = when (state.phase) {
            Phase.LISTENING -> CaptureState.RECORDING
            Phase.THINKING -> CaptureState.SAVING
            else -> CaptureState.IDLE
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (view?.ready == true) OutlinedButton(onClick = onDone, enabled = state.phase != Phase.THINKING) { Text("Done · file") }
            Box(Modifier.size(96.dp).clip(CircleShape).clickable(enabled = state.phase != Phase.THINKING) { onMic() },
                contentAlignment = Alignment.Center) {
                AuroraVoiceOrb(audioLevel = state.level, captureState = capture, modifier = Modifier.fillMaxSize())
                Icon(if (state.phase == Phase.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (state.phase == Phase.LISTENING) "Stop listening" else "Talk",
                    tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        Text(when (state.phase) {
            Phase.LISTENING -> "Listening — pause when you're done"
            Phase.THINKING -> "Thinking…"
            Phase.SPEAKING -> "Speaking — tap the mic to interrupt"
            else -> "Tap to talk"
        }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("…or type here") },
                singleLine = true, shape = RoundedCornerShape(24.dp), enabled = state.phase != Phase.THINKING)
            IconButton(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank() && state.phase != Phase.THINKING) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
