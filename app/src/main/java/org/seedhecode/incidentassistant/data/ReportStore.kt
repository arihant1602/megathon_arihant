package org.seedhecode.incidentassistant.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ReportStatus(val label: String) { NEW("New"), IN_PROGRESS("In progress"), RESOLVED("Resolved") }

/** A report the assistant filed: everything the desk needs, captured at filing time. */
data class FiledReport(
    val id: String,
    val filedAt: Long,
    val type: String,
    val typeLabel: String,
    val team: String,
    val priority: String,
    val priorityLabel: String,
    val slaMin: Int,
    val notify: List<String>,
    val secondary: List<String>,
    val fields: List<Pair<String, String>>,   // field name -> display value, in form order
    val summary: String,
    val transcript: List<Pair<String, String>>,  // (role, text)
    val status: ReportStatus,
) {
    val building get() = fields.firstOrNull { it.first == "building" }?.second
    val floor get() = fields.firstOrNull { it.first == "floor" }?.second
    val place get() = fields.firstOrNull { it.first == "space_id" }?.second
    fun where() = listOfNotNull(building?.let { "Building $it" }, floor?.let { "floor $it" }, place).joinToString(", ")
}

/** Filed reports on this device (JSON file in private storage, atomic writes). */
class ReportStore(dir: File) {
    private val file = File(dir, "reports.json")

    @Synchronized fun all(): List<FiledReport> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { parse(a.getJSONObject(it)) }
    }.getOrDefault(emptyList()).sortedByDescending { it.filedAt }

    @Synchronized fun save(report: FiledReport) = write(all().filter { it.id != report.id } + report)

    @Synchronized fun setStatus(id: String, status: ReportStatus) = write(all().map { if (it.id == id) it.copy(status = status) else it })

    private fun write(reports: List<FiledReport>) {
        val tmp = File(file.parentFile, "reports.json.tmp")
        tmp.writeText(JSONArray(reports.map { json(it) }).toString())
        check(tmp.renameTo(file)) { "Could not save reports." }
    }

    private fun pairs(list: List<Pair<String, String>>) = JSONArray(list.map { JSONArray().put(it.first).put(it.second) })
    private fun pairs(a: JSONArray) = (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } }
    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }

    private fun json(r: FiledReport) = JSONObject().put("id", r.id).put("filedAt", r.filedAt).put("type", r.type)
        .put("typeLabel", r.typeLabel).put("team", r.team).put("priority", r.priority).put("priorityLabel", r.priorityLabel)
        .put("slaMin", r.slaMin).put("notify", JSONArray(r.notify)).put("secondary", JSONArray(r.secondary))
        .put("fields", pairs(r.fields)).put("summary", r.summary).put("transcript", pairs(r.transcript)).put("status", r.status.name)

    private fun parse(o: JSONObject) = FiledReport(o.getString("id"), o.getLong("filedAt"), o.getString("type"),
        o.getString("typeLabel"), o.getString("team"), o.getString("priority"), o.getString("priorityLabel"), o.getInt("slaMin"),
        strings(o.getJSONArray("notify")), strings(o.getJSONArray("secondary")), pairs(o.getJSONArray("fields")),
        o.optString("summary"), pairs(o.getJSONArray("transcript")), ReportStatus.valueOf(o.getString("status")))
}
