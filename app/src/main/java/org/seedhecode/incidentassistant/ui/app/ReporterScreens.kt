package org.seedhecode.incidentassistant.ui.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.seedhecode.incidentassistant.data.FiledReport
import org.seedhecode.incidentassistant.engine.AssistantView
import org.seedhecode.incidentassistant.feature.assistant.AssistantUiState
import org.seedhecode.incidentassistant.feature.assistant.Phase
import org.seedhecode.incidentassistant.feature.report.CaptureState
import org.seedhecode.incidentassistant.ui.components.AuroraVoiceOrb
import org.seedhecode.incidentassistant.ui.theme.Success
import org.seedhecode.incidentassistant.ui.theme.SuccessContainer

private val Margins = Modifier.padding(horizontal = 20.dp)

// ------------------------------------------------------------------------------------------------ home
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(state: AssistantUiState, reports: List<FiledReport>, onStart: () -> Unit, onOpenReport: (String) -> Unit,
               onHistory: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(Margins).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DataCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text("Report an incident", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text("Say what happened and where. The assistant asks only for what's missing, then sends it to the right team.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(20.dp))
            PillButton("Start talking", onStart, icon = Icons.Default.Mic, enabled = state.phase != Phase.LOADING && state.phase != Phase.ERROR)
        }
        ModelStatus(state)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your reports", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            if (reports.size > 3) TextButton(onClick = onHistory) { Text("See all ${reports.size}") }
        }
        if (reports.isEmpty()) EmptyNote("No reports yet", "Reports you file appear here with their status.")
        else reports.take(3).forEach { ReportCard(it) { onOpenReport(it.id) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelStatus(state: AssistantUiState) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (state.phase) {
            Phase.LOADING -> Badge("Loading on-device models…", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            Phase.ERROR -> Badge("Models not loaded", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            else -> {
                Badge("Runs offline on this device", SuccessContainer, Success)
                Badge("Incident type on NPU")
                Badge(if (state.view?.whisperOnNpu == true) "Speech on NPU" else "Speech on CPU")
            }
        }
    }
    if (state.phase == Phase.ERROR) Text(state.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

// ------------------------------------------------------------------------------------------------ voice report
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(state: AssistantUiState, onMic: () -> Unit, onSend: (String) -> Unit, onFile: () -> Unit, onNewReport: () -> Unit,
                 onForm: () -> Unit, onQuestions: () -> Unit, onOpenReport: (String) -> Unit, onRetry: () -> Unit) {
    val v = state.view
    if (state.phase == Phase.ERROR) {
        Column(Margins.padding(vertical = 24.dp)) {
            EmptyNote("The on-device models didn't load", state.error.orEmpty()) { PillButton("Try again", onRetry) }
        }
        return
    }
    val list = rememberLazyListState()
    val turns = v?.chat?.size ?: 0
    LaunchedEffect(turns) { if (turns > 0) list.animateScrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { VoiceStage(state) }
            if (v != null) item {
                DataCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReadyBadge(v)
                        v.routing?.let { PriorityBadge(it.priority, it.priorityLabel) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Essentials(v)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedPill("Form", onForm, Modifier.weight(1f), icon = Icons.Default.Assignment)
                        if (!v.done && (v.confirmOptions.isNotEmpty() || v.actionFields.isNotEmpty()))
                            OutlinedPill("Answer options", onQuestions, Modifier.weight(1f), icon = Icons.Default.TouchApp)
                    }
                }
            }
            if (v != null && v.done && state.filedReportId != null) item {
                DataCard(container = SuccessContainer) {
                    Text("Report filed", style = MaterialTheme.typography.headlineSmall, color = Success)
                    Text("${v.team} has it. Reference ${state.filedReportId}.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedPill("View report", { onOpenReport(state.filedReportId) }, Modifier.weight(1f))
                        OutlinedPill("New report", onNewReport, Modifier.weight(1f))
                    }
                }
            }
            if (v != null && v.chat.isNotEmpty()) {
                item { SectionTitle("Conversation") }
                items(v.chat.reversed()) { (who, text) -> Turn(who, text) }
            }
        }
        Controls(state, onMic, onSend, onFile, onNewReport)
    }
}

/** The orb and, under it, the one thing to read: what the assistant wants to know. */
@Composable
private fun VoiceStage(state: AssistantUiState) {
    val v = state.view
    val capture = when (state.phase) { Phase.LISTENING -> CaptureState.RECORDING; Phase.THINKING -> CaptureState.SAVING; else -> CaptureState.IDLE }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (state.phase) {
            Phase.LISTENING -> Badge("● Listening", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            Phase.THINKING -> Badge("Thinking")
            Phase.SPEAKING -> Badge("Speaking")
            Phase.LOADING -> Badge("Loading models", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Badge("Ready", SuccessContainer, Success)
        }
        AuroraVoiceOrb(audioLevel = state.level, captureState = capture, modifier = Modifier.size(180.dp))
        Text(v?.question?.takeIf { v.chat.isNotEmpty() } ?: "Tell me what happened and where you are.",
            style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        val live = state.caption.takeIf { it.isNotBlank() && (state.phase == Phase.LISTENING || state.phase == Phase.THINKING) }
        if (live != null) {
            Spacer(Modifier.height(10.dp))
            Text("“$live”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Turn(who: String, text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(if (who == "user") "You" else "Assistant", Modifier.width(84.dp), style = MaterialTheme.typography.labelMedium,
            color = if (who == "user") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Controls(state: AssistantUiState, onMic: () -> Unit, onSend: (String) -> Unit, onFile: () -> Unit, onNewReport: () -> Unit) {
    val v = state.view
    var text by rememberSaveable { mutableStateOf("") }
    val busy = state.phase == Phase.THINKING || state.phase == Phase.LOADING
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (v?.done == true) { PillButton("Start a new report", onNewReport, icon = Icons.Default.Add); return@Column }
            if (v?.ready == true) PillButton("File report now", onFile, enabled = !busy, icon = Icons.Default.Send)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val listening = state.phase == Phase.LISTENING
                Button(onMic, Modifier.heightIn(min = 56.dp), enabled = !busy, shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
                    Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (listening) "Stop" else "Talk", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Or type") }, singleLine = true,
                    enabled = !busy, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text); text = "" } }),
                    trailingIcon = {
                        IconButton({ onSend(text); text = "" }, enabled = text.isNotBlank() && !busy) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send")
                        }
                    })
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ form
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormScreen(state: AssistantUiState, onEdit: (String, String) -> Unit, onReview: () -> Unit) {
    val v = state.view ?: return
    var editing by remember { mutableStateOf<String?>(null) }
    val said = v.chat.filter { it.first == "user" }.joinToString(" ") { it.second }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(Margins).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DataCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text("✨ Auto-filled from voice input", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(if (said.isBlank()) "Nothing said yet. Talk to the assistant and this form fills itself." else "“${said.take(400)}”",
                style = MaterialTheme.typography.bodyMedium, fontStyle = if (said.isBlank()) FontStyle.Normal else FontStyle.Italic,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        DataCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Essentials", style = MaterialTheme.typography.titleMedium); ReadyBadge(v) }
            Spacer(Modifier.height(8.dp))
            KeyValue("Incident type", v.typeLabel?.let { "$it" + (v.typeSource?.let { s -> if (s == "user-confirmed") " (you confirmed)" else "" } ?: "") }
                ?: "Not clear yet")
            KeyValue("Handled by", v.team ?: "Decided once the incident type is clear")
        }
        val fields = (listOf("building", "floor") + v.requiredFields + v.values.keys).distinct()
        if (v.values.isNotEmpty()) Badge("✨ Auto-extracted")
        fields.forEach { f -> FieldRow(v, f) { editing = f } }
        Spacer(Modifier.height(4.dp))
        PillButton("Review report", onReview, enabled = v.type != null)
    }
    editing?.let { f -> EditDialog(v, f, { onEdit(f, it); editing = null }) { editing = null } }
}

@Composable
private fun FieldRow(v: AssistantView, field: String, onEdit: () -> Unit) {
    val value = v.values[field]
    val essential = field in v.essential
    val safety = v.fieldMeta[field]?.safety == true
    OutlinedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (essential && value == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(humanize(field), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (essential) Badge("Required", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    if (safety) Badge("Safety")
                    if (value?.evidence == "edited by user") Badge("Edited", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value?.let { display(it.value) } ?: "Not given yet", style = MaterialTheme.typography.bodyLarge,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
            if (value != null) Icon(Icons.Default.CheckCircle, "Filled", tint = Success)
            else Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditDialog(v: AssistantView, field: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val meta = v.fieldMeta[field]
    val current = v.values[field]?.value
    val options = when (meta?.kind) {
        "bool" -> listOf("yes" to "Yes", "no" to "No", "unknown" to "Not known")
        "enum" -> meta.values.map { it to display(it) } + ("unknown" to "Not known")
        else -> if (field == "building") meta?.values?.map { it to "Building $it" } else null
    }
    var text by remember { mutableStateOf(current?.let { if (it is Boolean) (if (it) "yes" else "no") else it.toString() } ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(humanize(field)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (meta?.question?.isNotBlank() == true) Text(meta.question, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (options != null && meta?.kind != "text") options.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(text == value, { text = value }); Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                } else OutlinedTextField(text, { text = it }, singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = if (meta?.kind == "int") KeyboardType.Number else KeyboardType.Text))
            }
        },
        confirmButton = { TextButton({ onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

// ------------------------------------------------------------------------------------------------ questions
@Composable
fun QuestionsScreen(state: AssistantUiState, onAnswer: (String) -> Unit, onBack: () -> Unit) {
    val v = state.view ?: return
    val busy = state.phase == Phase.THINKING
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(Margins).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("The assistant asks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v.question, style = MaterialTheme.typography.headlineMedium)
        val answers: List<Pair<String, String>> = when {
            v.done -> emptyList()
            v.confirmOptions.isNotEmpty() -> v.confirmOptions.mapIndexed { i, (_, label) -> label to "The ${listOf("first", "second", "third")[i]} one, $label." } +
                ("Something else" to "Something else.")
            v.actionFields.size == 1 -> v.fieldMeta[v.actionFields[0]]?.let { m ->
                when (m.kind) {
                    "bool" -> listOf("Yes" to "Yes.", "No" to "No.", "Not sure" to "I don't know.")
                    "enum" -> m.values.filter { it != "unknown" }.map { display(it).replaceFirstChar { c -> c.uppercase() } to display(it) + "." } +
                        ("Not sure" to "I don't know.")
                    else -> if (m.name == "building") m.values.map { "Building $it" to "Building $it." } else emptyList()
                }
            } ?: emptyList()
            else -> emptyList()
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (answers.isEmpty()) Text(if (v.done) "This report is closed." else "Answer by talking or typing on the Report screen.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        answers.forEach { (label, reply) ->
            OutlinedCard(onClick = { if (!busy) onAnswer(reply) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedPill("Back to the conversation", onBack)
    }
}

// ------------------------------------------------------------------------------------------------ review
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(state: AssistantUiState, onFile: () -> Unit, onEdit: () -> Unit, onOpenReport: (String) -> Unit, onNewReport: () -> Unit) {
    val v = state.view ?: return
    val r = v.routing
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(Margins).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ReadyBadge(v); r?.let { PriorityBadge(it.priority, it.priorityLabel) }
        }
        Text(v.typeLabel ?: "Incident type not clear yet", style = MaterialTheme.typography.displaySmall)
        if (r != null) {
            DataCard {
                KeyValue("Sent to", r.team)
                KeyValue("People notified", r.notify.joinToString(", "))
                KeyValue("Response target", "Within ${r.slaMin} minutes")
                if (r.secondary.isNotEmpty()) KeyValue("Also flagged", r.secondary.joinToString(", ") { humanize(it) })
            }
        }
        DataCard {
            Text("Where", style = MaterialTheme.typography.titleMedium)
            Essentials(v)
        }
        DataCard {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            val details = (v.requiredFields + v.values.keys).distinct().filter { it !in setOf("building", "floor", "space_id") }
            details.forEach { f -> KeyValue(humanize(f), display(v.values[f]?.value)) }
        }
        if (v.summary.isNotBlank()) DataCard { KeyValue("Summary", v.summary) }
        val filed = state.filedReportId
        when {
            filed != null -> {
                PillButton("View filed report", { onOpenReport(filed) })
                OutlinedPill("Start a new report", onNewReport)
            }
            v.done -> OutlinedPill("Start a new report", onNewReport)
            else -> {
                PillButton("File report", onFile, enabled = v.ready && state.phase != Phase.THINKING, icon = Icons.Default.Send)
                if (!v.ready) Text("Building and floor are needed before this can be filed.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                OutlinedPill("Edit form", onEdit, icon = Icons.Default.Edit)
            }
        }
    }
}
