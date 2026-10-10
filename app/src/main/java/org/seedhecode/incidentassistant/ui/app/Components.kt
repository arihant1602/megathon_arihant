package org.seedhecode.incidentassistant.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.seedhecode.incidentassistant.data.FiledReport
import org.seedhecode.incidentassistant.data.ReportStatus
import org.seedhecode.incidentassistant.engine.AssistantView
import org.seedhecode.incidentassistant.ui.theme.Success
import org.seedhecode.incidentassistant.ui.theme.SuccessContainer
import org.seedhecode.incidentassistant.ui.theme.Warning
import org.seedhecode.incidentassistant.ui.theme.WarningContainer
import java.text.DateFormat
import java.util.Date

/** Status chip / micro-badge: 6dp corners, labelSmall semibold, paired container + content colors. */
@Composable
fun Badge(text: String, container: Color = MaterialTheme.colorScheme.primaryContainer,
          content: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Text(text, Modifier.background(container, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        color = content, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
fun PriorityBadge(priority: String, label: String) = when (priority) {
    "P1" -> Badge("$priority $label", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    "P2" -> Badge("$priority $label", WarningContainer, Warning)
    else -> Badge("$priority $label")
}

@Composable
fun StatusBadge(status: ReportStatus) = when (status) {
    ReportStatus.NEW -> Badge(status.label, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
    ReportStatus.IN_PROGRESS -> Badge(status.label, WarningContainer, Warning)
    ReportStatus.RESOLVED -> Badge(status.label, SuccessContainer, Success)
}

/** Adaptive data card: SurfaceVariant, 16dp corners, 16dp padding. */
@Composable
fun DataCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, container: Color = MaterialTheme.colorScheme.surfaceVariant,
             content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) Card(onClick, modifier.fillMaxWidth(), shape = shape, colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), content = content)
    } else Card(modifier.fillMaxWidth(), shape = shape, colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Selection card: 48dp icon well, weighted title/description, trailing radio. */
@Composable
fun SelectionCard(icon: ImageVector, title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null,
               container: Color = MaterialTheme.colorScheme.primary, content: Color = MaterialTheme.colorScheme.onPrimary) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun OutlinedPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(onClick, modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled, shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** WHAT / WHO / WHERE: the three things a report cannot be filed without. Missing ones are shown in crimson. */
@Composable
fun Essentials(v: AssistantView) {
    val building = v.values["building"]?.value; val floor = v.values["floor"]?.value
    EssentialRow("What", v.typeLabel)
    EssentialRow("Who", v.team)
    EssentialRow("Where", if (building == null && floor == null) null else
        listOfNotNull(building?.let { "Building $it" } ?: "Building ?", floor?.let { "floor $it" } ?: "floor ?",
            v.values["space_id"]?.value?.toString()).joinToString(", "))
}

@Composable
private fun EssentialRow(label: String, value: String?) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "Still needed", style = MaterialTheme.typography.bodyLarge,
            color = if (value == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ReadyBadge(v: AssistantView) = when {
    v.done && v.outcome == "submitted" -> Badge("Filed", SuccessContainer, Success)
    v.done -> Badge("Not an incident", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    v.ready -> Badge("Ready to file", SuccessContainer, Success)
    else -> Badge("Needs ${v.missingEssentials.joinToString(" and ") { if (it == "incident_type") "what happened" else it }}",
        MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportCard(r: FiledReport, onClick: () -> Unit) {
    DataCard(onClick = onClick) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.id, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusBadge(r.status)
            PriorityBadge(r.priority, r.priorityLabel)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.typeLabel, style = MaterialTheme.typography.titleMedium)
                Text(r.where().ifEmpty { "Location not given" }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${r.team} · ${when_(r.filedAt)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun when_(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

/** Response deadline from the device clock: filing time + the taxonomy's SLA for the priority (never model-written). */
fun respondBy(filedAt: Long, slaMin: Int): String =
    "By ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(filedAt + slaMin * 60_000L))} ($slaMin min)"

private val FieldLabels = mapOf("space_id" to "Room or area", "space_type" to "Type of space", "occurred_at" to "When it happened",
    "immediate_actions_taken" to "Action already taken", "persons_involved" to "People involved", "asset_tag" to "Asset tag",
    "landmark" to "Nearby landmark")

/** Field name as people say it ("Room or area", not "space_id"). */
fun humanize(field: String) = FieldLabels[field] ?: field.replace('_', ' ').replaceFirstChar { it.uppercase() }

fun display(value: Any?): String = when (value) { null -> "—"; true -> "Yes"; false -> "No"; "unknown" -> "Not known"
    else -> value.toString().replace('_', ' ').replaceFirstChar { it.uppercase() } }

@Composable
fun EmptyNote(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.Start) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 6.dp)) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
