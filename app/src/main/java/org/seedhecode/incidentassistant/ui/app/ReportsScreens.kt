package org.seedhecode.incidentassistant.ui.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.seedhecode.incidentassistant.data.FiledReport
import org.seedhecode.incidentassistant.data.ReportStatus

@Composable
fun HistoryScreen(reports: List<FiledReport>, onOpen: (String) -> Unit, onStart: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Your reports", style = MaterialTheme.typography.headlineLarge) }
        if (reports.isEmpty()) item {
            EmptyNote("No reports yet", "When you file a report it's listed here, with what the facilities desk has done about it.") {
                PillButton("Report an incident", onStart)
            }
        }
        items(reports, key = { it.id }) { r -> ReportCard(r) { onOpen(r.id) } }
    }
}

/** Facilities desk: everything filed on this device, open work first, most urgent first. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DeskScreen(reports: List<FiledReport>, onOpen: (String) -> Unit) {
    var status by rememberSaveable { mutableStateOf<ReportStatus?>(null) }
    var team by rememberSaveable { mutableStateOf<String?>(null) }
    val teams = reports.map { it.team }.distinct().sorted()
    val shown = reports.filter { (status == null || it.status == status) && (team == null || it.team == team) }
        .sortedWith(compareBy<FiledReport>({ it.status == ReportStatus.RESOLVED }, { it.priority }, { -it.filedAt }))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Facilities desk", style = MaterialTheme.typography.headlineLarge)
            val open = reports.count { it.status != ReportStatus.RESOLVED }
            val urgent = reports.count { it.status != ReportStatus.RESOLVED && it.priority == "P1" }
            Text(if (reports.isEmpty()) "Nothing filed yet." else "$open open" + (if (urgent > 0) ", $urgent critical" else ""),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (reports.isNotEmpty()) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(status == null, { status = null }, { Text("All") })
                ReportStatus.values().forEach { s -> FilterChip(status == s, { status = if (status == s) null else s }, { Text(s.label) }) }
            }
            if (teams.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                teams.forEach { t -> FilterChip(team == t, { team = if (team == t) null else t }, { Text(t) }) }
            }
        }
        if (reports.isEmpty()) item { EmptyNote("No reports", "Reports filed through the assistant on this device appear here, most urgent first.") }
        else if (shown.isEmpty()) item {
            EmptyNote("No reports match these filters", "Clear a filter to see more.") {
                OutlinedPill("Clear filters", { status = null; team = null })
            }
        }
        items(shown, key = { it.id }) { r -> ReportCard(r) { onOpen(r.id) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportDetailScreen(report: FiledReport?, desk: Boolean, reading: Boolean, onStatus: (ReportStatus) -> Unit, onListen: () -> Unit) {
    if (report == null) {
        Column(Modifier.padding(20.dp)) { EmptyNote("Report not found", "It may have been filed on another device.") }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(report.id, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusBadge(report.status); PriorityBadge(report.priority, report.priorityLabel)
        }
        Text(report.typeLabel, style = MaterialTheme.typography.displaySmall)
        Text("Filed ${when_(report.filedAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (desk) OutlinedPill(if (reading) "Stop reading" else "Listen to report", onListen,
            icon = if (reading) Icons.Default.Stop else Icons.Default.VolumeUp)
        if (desk && report.status != ReportStatus.RESOLVED) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (report.status == ReportStatus.NEW) PillButton("Start work", { onStatus(ReportStatus.IN_PROGRESS) }, Modifier.weight(1f))
            OutlinedPill("Mark resolved", { onStatus(ReportStatus.RESOLVED) }, Modifier.weight(1f))
        }
        DataCard {
            KeyValue("Where", report.where().ifEmpty { "Not given" })
            KeyValue("Sent to", report.team)
            KeyValue("People notified", report.notify.joinToString(", "))
            KeyValue("Response target", respondBy(report.filedAt, report.slaMin))
            if (report.secondary.isNotEmpty()) KeyValue("Also flagged", report.secondary.joinToString(", ") { humanize(it) })
        }
        val details = report.fields.filter { it.first !in setOf("building", "floor", "space_id") }
        if (details.isNotEmpty()) DataCard {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            details.forEach { (f, value) -> KeyValue(humanize(f), display(value)) }
        }
        DataCard {
            Text("Conversation", style = MaterialTheme.typography.titleMedium)
            report.transcript.forEach { (who, text) ->
                KeyValue(if (who == "user") "Reporter" else "Assistant", text)
            }
        }
    }
}
