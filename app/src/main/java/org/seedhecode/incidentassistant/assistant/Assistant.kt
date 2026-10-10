package org.seedhecode.incidentassistant.assistant

/** One processed user turn, for the UI and timing logs. */
data class TurnLog(val turn: Int, val user: String, val note: String, val extracted: Map<String, Any>, val action: Action,
                   val classifierMs: Double, val llm: List<LlmStat>, val turnMs: Long)

/**
 * Port of laya/assistant.py Assistant: classify (user sentences only) -> settle type -> rules then LLM extraction
 * -> form policy picks the next question -> LLM rewrites the question -> routing.
 */
class Assistant(val tax: Taxonomy, private val classify: (List<List<String>>) -> Classification, llm: SmallLlm?,
                private val threshold: Double = 0.8, private val summarise: Boolean = true,
                private val llmQuestions: Boolean = true) {
    val policy = FormPolicy(tax)
    val extractor = Extractor(llm)
    private val hasLlm = llm != null
    var state = FormState(); private set
    val turns = mutableListOf<List<String>>()
    val chat = mutableListOf<Pair<String, String>>()
    var lastAction: Action? = null; private set
    var cls: Classification? = null; private set
    var note = ""; private set
    var extracted: Map<String, Any> = emptyMap(); private set
    var routing: Routing? = null; private set
    var summary = ""; private set
    val log = mutableListOf<TurnLog>()
    private var prevType: String? = null
    private var t0 = 0L

    fun reset() {
        state = FormState(); turns.clear(); chat.clear(); lastAction = null; cls = null; note = ""
        extracted = emptyMap(); routing = null; summary = ""; log.clear(); prevType = null; extractor.calls.clear()
    }

    /** A value the user typed into the form: stored like an extraction, evidence marks it as a manual edit. */
    fun edit(field: String, value: Any?) {
        if (value == null) state.values.remove(field) else state.set(field, value, "edited by user", turns.size)
        routing = policy.route(state, turns.flatten().joinToString(" "))
    }

    private fun confirm(text: String): String? {
        val opts = state.pendingConfirm ?: return null
        val words = Regex("[a-z0-9]+").findAll(text.lowercase()).map { it.value }.toList()
        for (w in words) ORDINALS[w]?.let { if (it < opts.size) return opts[it] }
        for (o in opts) {
            val lw = Regex("[a-z]+").findAll(tax.label(o).lowercase()).map { it.value }.toSet() - setOf("fault", "issue", "and", "or")
            if (lw.isNotEmpty() && lw.any { it in words }) return o
        }
        return extractor.choose(opts, opts.associateWith { tax.label(it) }, text)
    }

    fun step(text: String): Action {
        t0 = System.nanoTime()
        extractor.calls.clear()
        val st = state
        val sents = splitSentences(text).ifEmpty { listOf(text) }
        val ended = Taxonomy.isEnd(text)
        if (!ended) turns += sents
        var turn = turns.size
        chat += "user" to text
        val prior = cls
        if (ended && prior != null) return finish(policy.nextAction(st, prior.probs, threshold, turn, ended = true), turn, text)
        if (turns.isEmpty()) { turns += sents; turn = 1 }
        val c = classify(turns).also { cls = it }
        val probs = c.probs
        if (st.pendingConfirm != null) {
            val top = probs.maxByOrNull { it.value }!!
            if (!(top.key in tax.types && top.value >= threshold)) {   // a confident classifier beats reply matching
                confirm(text)?.let { st.type = it; st.typeSource = "user-confirmed"; st.typeConf = 1.0 }
            }
            st.pendingConfirm = null
        }
        note = policy.settleType(st, probs, threshold)

        // extraction: fields just asked + fields the classifier saw in this turn (relevant sentences only)
        val top3 = probs.entries.sortedByDescending { it.value }.map { it.key }.filter { it in tax.types }.take(3)
        val cand = tax.candidateFields(st.type?.let { listOf(it) } ?: top3)
        val cur = c.sentences.filter { it.turn == turn - 1 }
        val asked = lastAction?.takeIf { it.kind == "ask" }?.fields ?: emptyList()
        val detected = cur.filter { it.relevant >= 0.5 }.flatMap { it.fields }.filter { it in cand }
        var want = (asked + detected).distinct().take(5)
        var relText = cur.filter { it.relevant >= 0.5 }.joinToString(" ") { it.text }.ifEmpty { if (asked.isNotEmpty()) text else "" }
        val newly = st.type != null && (note.startsWith("type settled") || note.startsWith("type changed") || st.type != prevType)
        if (newly) {   // catch-up: the type just settled, read everything relevant so far for its required fields
            want = (want + tax.requiredFields(st.type).filter { !st.filled(it) }).distinct().take(5)
            relText = c.sentences.filter { it.relevant >= 0.5 }.joinToString(" ") { it.text }.ifEmpty { text }
        }
        prevType = st.type
        val locWant = (want + listOf("building", "floor", "occurred_at").filter { !st.filled(it) }).distinct()
        val ruled = Extractor.ruleExtract(text, locWant)   // deterministic first (building codes, floors, "just now")
        ruled.forEach { (f, v) -> st.set(f, v, text, turn) }
        want = want.filter { it !in ruled }
        val got = LinkedHashMap<String, Any>(ruled)
        if (want.isNotEmpty() && relText.isNotEmpty() && hasLlm) {
            val vals = extractor.extract(want.map { tax.fieldMeta(it) }, relText, lastAction?.text)
            vals.forEach { (f, v) -> st.set(f, v, relText, turn) }
            got.putAll(vals)
        }
        extracted = got
        if ("occurred_at" in asked && !st.filled("occurred_at"))   // taxonomy default_occurred_at
            st.set("occurred_at", "around report time (approximate)", "(no time given)", turn)
        return finish(policy.nextAction(st, probs, threshold, turn, ended = ended), turn, text)
    }

    private fun finish(action: Action, turn: Int, text: String): Action {
        val st = state
        action.template = action.text
        var out = action
        if (action.kind in setOf("ask", "detail") && hasLlm && llmQuestions) {   // LLM writes the question
            var q = extractor.question(action.fields.map { tax.fieldMeta(it) }, tax.label(st.type), text)
            if (q != null && action.fields.size > 1) {   // a two-field question must mention both, or use the template
                val qw = Regex("[a-z]+").findAll(q.lowercase()).map { it.value }.toSet()
                if (!action.fields.all { f -> f.split("_").any { it in qw } }) q = null
            }
            if (q != null) {
                val ph = tax.phrase(action.fields)
                val lead = if (action.text.contains(ph)) action.text.substring(0, action.text.indexOf(ph)) else ""
                out = action.copy(text = lead + q + (if (action.kind == "detail") Taxonomy.END_HINT else ""),
                    template = action.template, questionBy = "llm")
            }
        }
        lastAction = out
        chat += "assistant" to out.text
        routing = policy.route(st, turns.flatten().joinToString(" "))
        if (st.done && st.outcome == "submitted" && summarise && hasLlm)
            summary = extractor.summarise(cls?.sentences?.filter { it.relevant >= 0.5 }?.map { it.text } ?: emptyList())
        log += TurnLog(turn, text, note, extracted, out, cls?.ms ?: 0.0, extractor.calls.toList(), (System.nanoTime() - t0) / 1_000_000)
        return out
    }

    companion object {
        private val ORDINALS = mapOf("1" to 0, "one" to 0, "first" to 0, "2" to 1, "two" to 1, "second" to 1, "3" to 2,
            "three" to 2, "third" to 2)

        fun splitSentences(text: String): List<String> = text.lines()
            .flatMap { it.split(Regex("(?<=[.!?])\\s+")) }.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
