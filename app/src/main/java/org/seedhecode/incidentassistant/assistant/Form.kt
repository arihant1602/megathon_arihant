package org.seedhecode.incidentassistant.assistant

import org.json.JSONArray
import org.json.JSONObject

/**
 * Port of laya/form.py: form schema, state, ask policy and priority/routing, all from incident_taxonomy.json.
 * Plain code, no ML: the classifier decides the type, the LLM extracts values, this decides what to ask next.
 */
data class FieldMeta(val name: String, val kind: String, val values: List<String>, val safety: Boolean, val question: String)

class Taxonomy(json: String) {
    val root = JSONObject(json)
    private val policy = root.getJSONObject("completion_policy")
    val types: Map<String, JSONObject> = root.getJSONArray("incident_types").objects().associateBy { it.getString("id") }
    val maxPerQuestion = policy.getInt("max_fields_per_question")
    val essential = policy.optJSONArray("essential_fields")?.strings() ?: listOf("incident_type", "building", "floor")
    val essentialLocation = essential.filter { it != "incident_type" }
    val deptNames = root.getJSONArray("departments").objects().associate { it.getString("id") to it.getString("name") }
    val roleNames = root.getJSONArray("departments").objects().flatMap { d ->
        d.getJSONArray("roles").objects().map { "${d.getString("id")}.${it.getString("id")}" to it.getString("name") }
    }.toMap()
    val buildingCodes = root.getJSONObject("location_model").getJSONArray("buildings").objects().map { it.getString("code") }

    fun fieldMeta(name: String): FieldMeta {
        val library = root.getJSONObject("field_library")
        if (library.has(name)) {
            val f = library.getJSONObject(name)
            val kind = when (f.getString("type")) { "enum" -> "enum"; "bool" -> "bool"; "int" -> "int"; else -> "text" }
            return FieldMeta(name, kind, f.optJSONArray("values")?.strings() ?: emptyList(), f.optBoolean("safety"),
                f.optString("question", "What is the ${name.replace('_', ' ')}?"))
        }
        val c = root.getJSONObject("common_fields").getJSONObject(name)
        if (name == "space_type") return FieldMeta(name, "enum",
            root.getJSONObject("location_model").getJSONArray("space_types").strings(), false, "")
        val kind = if (c.optString("type") == "int") "int" else "text"
        return FieldMeta(name, kind, if (name == "building") buildingCodes else emptyList(), false,
            c.optString("question", "What is the ${name.replace('_', ' ')}?"))
    }

    fun label(type: String?): String = when (type) {
        "undetermined" -> "not clear yet"; "none" -> "not an incident"
        else -> types[type]?.getString("label") ?: type.orEmpty()
    }
    fun team(type: String): String = deptNames.getValue(types.getValue(type).getString("dept"))

    fun requiredFields(type: String?): List<String> =
        CORE + (types[type]?.getJSONArray("required")?.strings() ?: emptyList())

    /** Fields worth extracting while the type is (un)settled: core + optional common + those types' fields. */
    fun candidateFields(typeIds: List<String>): List<String> {
        val out = (CORE + OPTIONAL_COMMON).toMutableList()
        for (t in typeIds) types[t]?.let { def ->
            (def.getJSONArray("required").strings() + (def.optJSONArray("optional")?.strings() ?: emptyList()))
                .forEach { if (it !in out) out += it }
        }
        return out
    }

    /** completion_policy.ask_order: safety-critical -> location -> type_required -> occurred_at. */
    fun askOrder(missing: List<String>): List<String> {
        val safety = missing.filter { fieldMeta(it).safety }
        val loc = LOCATION.filter { it in missing && it !in safety }
        val typ = missing.filter { it !in safety && it !in loc && it != "occurred_at" }
        return safety + loc + typ + (if ("occurred_at" in missing) listOf("occurred_at") else emptyList())
    }

    /** Join the taxonomy's question texts for up to two fields into one natural question. */
    fun phrase(fields: List<String>): String {
        val qs = fields.map { fieldMeta(it).question }
        return if (qs.size == 1) qs[0] else qs[0] + " And " + qs[1][0].lowercase() + qs[1].substring(1)
    }

    companion object {
        val CORE = listOf("building", "floor", "space_id", "occurred_at")
        val OPTIONAL_COMMON = listOf("space_type", "landmark", "immediate_actions_taken", "persons_involved", "witnesses", "asset_tag")
        val LOCATION = listOf("building", "floor", "space_id")
        const val END_HINT = " (You can also say 'done' to file the report now.)"
        private val END_RE = Regex("^\\W*(done|i'?m done|that'?s (all|it|everything)|that is (all|it|everything)|" +
            "no(pe)?,? that'?s (all|it)|no(pe)?,? that is (all|it)|nothing (else|more)|" +
            "no(thing)? more|end|finish(ed)?|submit( it)?|file it|that will be all|no,? thanks?|bye|goodbye)\\W*$",
            RegexOption.IGNORE_CASE)

        /** User wants to stop (short closing replies only, so "done, it's on floor 3" is not an ending). */
        fun isEnd(text: String) = END_RE.matches(text.trim()) && text.trim().split(Regex("\\s+")).size <= 6
    }
}

data class FieldValue(val value: Any, val evidence: String, val turn: Int)

class FormState {
    val values = linkedMapOf<String, FieldValue>()
    var type: String? = null
    var typeSource: String? = null        // "classifier" | "user-confirmed"
    var typeConf = 0.0
    var pendingConfirm: List<String>? = null
    var followups = 0
    val asked = mutableListOf<Pair<Int, List<String>>>()
    var fireNoticeGiven = false
    var ready = false
    var readyTurn: Int? = null
    val askedFields = mutableSetOf<String>()
    var done = false
    var outcome: String? = null           // "submitted" | "out_of_scope"

    fun set(field: String, value: Any, evidence: String, turn: Int) { values[field] = FieldValue(value, evidence, turn) }
    fun filled(field: String) = field in values
    fun missing(tax: Taxonomy) = if (type != null) tax.requiredFields(type).filter { !filled(it) } else Taxonomy.CORE
    fun missingEssentials(tax: Taxonomy) =
        (if (type == null) listOf("incident_type") else emptyList()) + tax.essentialLocation.filter { !filled(it) }
}

data class Action(val kind: String, val text: String, val fields: List<String>, val reason: String,
                  val essential: Boolean = false, var template: String = text, var questionBy: String = "template")

data class Routing(val priority: String, val priorityLabel: String, val team: String, val notify: List<String>,
                   val rulesFired: List<String>, val rerouteTo: String?, val secondary: List<String>, val slaMin: Int)

class FormPolicy(val tax: Taxonomy) {
    private fun top(probs: Map<String, Double>) = probs.maxByOrNull { it.value }!!.key

    /** Update the settled type from the classifier. Returns a note describing what happened. */
    fun settleType(state: FormState, probs: Map<String, Double>, threshold: Double): String {
        val top = top(probs); val p = probs.getValue(top)
        if (state.typeSource == "user-confirmed" && !(top in tax.types && top != state.type && p >= threshold))
            return "type confirmed by user: ${state.type}"
        if (top in tax.types && p >= threshold) {
            val note = when {
                state.type != null && state.type != top -> "type changed ${state.type} -> $top (${f2(p)})"
                state.type != top -> "type settled: $top (${f2(p)} >= $threshold)"
                else -> "type holds: $top (${f2(p)})"
            }
            state.type = top; state.typeSource = "classifier"; state.typeConf = p
            return note
        }
        val cur = state.type
        if (cur != null && (probs[cur] ?: 0.0) < 0.3 && top != cur) {
            state.type = null; state.typeSource = null; state.typeConf = 0.0
            return "un-settled $cur (now ${f2(probs[cur] ?: 0.0)}); top $top ${f2(p)} < $threshold, will ask"
        }
        if (cur != null) return "keeping $cur; top now $top ${f2(p)} < $threshold"
        return "not settled: top $top ${f2(p)} < $threshold"
    }

    /** Phase 1: essentials (type -> who, building + floor -> where). Phase 2: details one question at a time. */
    fun nextAction(state: FormState, probs: Map<String, Double>, threshold: Double, turn: Int, ended: Boolean = false): Action {
        val top = top(probs)
        val type = state.type
        if (type == null) {
            if (top == "none" && probs.getValue(top) >= threshold) {
                state.done = true; state.outcome = "out_of_scope"
                return Action("out_of_scope", "This assistant is only for reporting facility incidents (leaks, faults, safety, security). " +
                    "Nothing has been filed.", emptyList(), "classifier: not an incident (${f2(probs.getValue(top))})")
            }
            val ranked = probs.entries.sortedByDescending { it.value }.map { it.key }.filter { it in tax.types }.take(3)
            val lead = if (ended) "I can't file this yet: I still need to know what the problem is. " else ""
            if (ranked.sumOf { probs.getValue(it) } >= 0.5 && top != "undetermined") {
                state.pendingConfirm = ranked
                val opts = ranked.mapIndexed { i, t -> "(${i + 1}) ${tax.label(t)}" }.joinToString(", ")
                return Action("confirm", lead + "Just to make sure I file this right, is it about $opts, or something else?",
                    emptyList(), "essential: type below $threshold, offer top-3")
            }
            return Action("open", lead + "Can you tell me a bit more about what's happening?", emptyList(), "essential: type not clear yet")
        }
        var prefix = ""
        if (type == "fire_smoke" && !state.fireNoticeGiven) {
            prefix = tax.types.getValue("fire_smoke").getString("user_prompt_override") + " "
            state.fireNoticeGiven = true
        }
        val em = tax.essentialLocation.filter { !state.filled(it) }
        if (em.isNotEmpty()) {
            state.followups++; state.asked += turn to em
            val lead = if (ended) "I can't file this yet without the location. " else ""
            val ask = em.take(tax.maxPerQuestion)
            return Action("ask", prefix + lead + tax.phrase(ask), ask, "essential: location missing", essential = true)
        }
        val team = tax.team(type)
        if (!state.ready) {
            state.ready = true; state.readyTurn = turn
            prefix += "Got it, your report is ready and $team will handle it. "
        }
        if (ended) {
            state.done = true; state.outcome = "submitted"
            return Action("submit", prefix + "Report filed. $team has been notified. Thanks!", emptyList(), "user ended the conversation")
        }
        val details = tax.askOrder(state.missing(tax)).filter { it !in state.askedFields }
        if (details.isNotEmpty()) {
            val ask = details.take(tax.maxPerQuestion)
            state.askedFields += ask; state.followups++; state.asked += turn to ask
            val why = if (tax.fieldMeta(ask[0]).safety) "detail: safety field" else "detail: more information"
            return Action("detail", prefix + tax.phrase(ask) + Taxonomy.END_HINT, ask, why)
        }
        return Action("offer_end", prefix + "That's everything I need. Anything else you'd like to add? Say 'done' to file the report.",
            emptyList(), "no more details to ask")
    }

    private fun matches(cond: JSONObject, values: Map<String, FieldValue>): Boolean {
        val f = cond.getString("field")
        val v = values[f]?.value ?: return false
        if (v == "unknown" && tax.root.getJSONObject("field_library").optJSONObject(f)?.optBoolean("safety") == true) return true
        val target = cond.get("value")
        return when (cond.getString("op")) {
            "eq" -> same(v, target)
            "in" -> target is JSONArray && (0 until target.length()).any { same(v, target.get(it)) }
            else -> false
        }
    }
    private fun same(a: Any, b: Any) = a == b || (a is Number && b is Number && a.toDouble() == b.toDouble())

    /** Priority, owning team, people to notify, SLA, secondary incidents, from the taxonomy rules. */
    fun route(state: FormState, transcript: String): Routing? {
        val type = state.type ?: return null
        val t = tax.types.getValue(type)
        var pri = t.getString("default_priority")
        val notify = mutableListOf("${t.getString("dept")}.${t.getString("role")}")
        val fired = mutableListOf<String>(); var reroute: String? = null
        t.optJSONArray("rules")?.objects()?.forEach { r ->
            val w = r.getJSONObject("when")
            if (matches(w, state.values)) {
                fired += "${w.getString("field")} ${w.getString("op")} ${w.get("value")}"
                r.optString("set_priority").takeIf { it.isNotEmpty() }?.let { if (rank(it) < rank(pri)) pri = it }
                r.optJSONArray("add_notify")?.strings()?.forEach { if (it !in notify) notify += it }
                if (r.has("reroute_to")) reroute = r.getString("reroute_to")
            }
        }
        val words = Regex("[a-z]+").findAll(transcript.lowercase()).map { it.value }.toSet()
        val secondary = tax.root.getJSONArray("global_rules").objects().filter { g ->
            val w = g.getJSONObject("when")
            w.getString("field") == "transcript_mentions" && w.getJSONArray("value").strings().any { it in words } &&
                g.getString("type") != type
        }.map { it.getString("type") }
        val p = tax.root.getJSONObject("priorities").getJSONObject(pri)
        return Routing(pri, p.getString("label"), tax.team(type), notify.map { tax.roleNames[it] ?: it }, fired, reroute,
            secondary, p.getInt("response_sla_min"))
    }
    private fun rank(p: String) = p.removePrefix("P").toInt()
}

internal fun f2(x: Double) = String.format(java.util.Locale.US, "%.2f", x)
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
