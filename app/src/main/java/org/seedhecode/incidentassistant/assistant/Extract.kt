package org.seedhecode.incidentassistant.assistant

import org.json.JSONObject

/** Chat message for the small LLM (role = system | user). */
data class Msg(val role: String, val content: String)

/** Small local LLM (Qwen3-0.6B via llama.cpp). [grammar] is GBNF that constrains the JSON reply. */
interface SmallLlm {
    fun chat(messages: List<Msg>, grammar: String, maxTokens: Int): String
}

data class LlmStat(val kind: String, val ms: Long)

/**
 * Port of laya/llm.py: the exact extraction / choose / question / summary prompts, the evidence validator
 * and the deterministic rule extractor. The LLM never picks the incident type.
 */
class Extractor(private val llm: SmallLlm?) {
    val calls = mutableListOf<LlmStat>()

    private fun chat(kind: String, messages: List<Msg>, grammar: String, maxTokens: Int): String {
        val t0 = System.nanoTime()
        try { return llm!!.chat(messages, grammar, maxTokens) }
        finally { calls += LlmStat(kind, (System.nanoTime() - t0) / 1_000_000) }
    }

    /** metas: 1-5 fields. Returns validated {field: value}. One retry on invalid JSON. */
    fun extract(metas: List<FieldMeta>, userText: String, question: String?): Map<String, Any> {
        if (llm == null || metas.isEmpty() || userText.isBlank()) return emptyMap()
        val msgs = buildPrompt(metas, userText, question)
        var data: JSONObject? = null
        for (attempt in 0 until 2) {
            data = runCatching { JSONObject(chat("extract", msgs, schemaGrammar(metas), 80)) }.getOrNull()
            if (data != null) break
        }
        return validate(data, metas, userText)
    }

    /** Map a reply to an offered option ("the second one"). Returns an option id or null. */
    fun choose(options: List<String>, labels: Map<String, String>, userText: String): String? {
        if (llm == null) return null
        val prompt = "Options: " + options.mapIndexed { i, o -> "${i + 1} = ${labels[o]}" }.joinToString("; ") +
            "\nUser said: $userText\nWhich option did the user pick? Answer 0 if none of them."
        val k = runCatching {
            JSONObject(chat("choose", listOf(Msg("user", prompt)), objectGrammar("option", "integer"), 10)).getInt("option")
        }.getOrNull() ?: return null
        return if (k in 1..options.size) options[k - 1] else null
    }

    /** One short, specific question asking for these fields (LLM-written), or null. */
    fun question(metas: List<FieldMeta>, incident: String, userSaid: String): String? {
        if (llm == null) return null
        val hint = metas.joinToString("; ") { "${it.name.replace('_', ' ')} (e.g. '${it.question}')" }
        val prompt = "Incident: $incident. The user just said: \"${userSaid.take(200)}\"\n" +
            "Ask the user for: $hint.\nWrite ONE short, friendly, specific question. Do not repeat what they said."
        val q = runCatching {
            JSONObject(chat("question", listOf(Msg("system", "You are an incident reporting assistant."), Msg("user", prompt)),
                objectGrammar("question", "string"), 48)).getString("question").trim()
        }.getOrNull() ?: return null
        return if (q.length in 10..220 && q.contains('?') && !q.contains('{')) q else null
    }

    fun summarise(sentences: List<String>): String {
        if (llm == null || sentences.isEmpty()) return ""
        return runCatching {
            JSONObject(chat("summary", listOf(Msg("user", "Summarise this incident report in one short sentence:\n" +
                sentences.joinToString(" "))), objectGrammar("summary", "string"), 50)).getString("summary")
        }.getOrDefault("")
    }

    companion object {
        const val SYSTEM = "You fill an incident report form from what the user said. Reply with JSON only. " +
            "Only include fields the user actually stated. Use \"unknown\" if the user says they don't know. Never guess."

        fun describe(m: FieldMeta) = when (m.kind) {
            "enum" -> "${m.name}: one of ${m.values.joinToString(", ")}"
            "bool" -> "${m.name}: yes or no"
            "int" -> "${m.name}: a number"
            else -> "${m.name}: short text" + if (m.values.isNotEmpty()) " (known: ${m.values.joinToString(", ")}, or as said)" else ""
        }

        /** The exact extraction prompt from llm.build_prompt (also what the Qwen fine-tune is trained on). */
        fun buildPrompt(metas: List<FieldMeta>, userText: String, question: String?): List<Msg> {
            val prompt = (if (question != null) "Assistant asked: $question\n" else "") + "User said: $userText\nFields:\n" +
                metas.joinToString("\n") { "- " + describe(it) }
            return listOf(Msg("system", SYSTEM), Msg("user", prompt))
        }

        private fun lit(s: String) = "\"\\\"" + s.replace("\\", "\\\\\\\\").replace("\"", "\\\\\\\"") + "\\\"\""

        /**
         * GBNF equivalent of llm._schema (what Ollama's `format` enforces): a JSON object whose keys are a subset of
         * the fields, enums limited to their values + "unknown", bools to yes/no/unknown, everything else a string.
         */
        fun schemaGrammar(metas: List<FieldMeta>): String {
            val rules = StringBuilder()
            val kvs = metas.mapIndexed { i, m ->
                val value = when (m.kind) {
                    "enum" -> (m.values + "unknown").distinct().joinToString(" | ") { lit(it) }
                    "bool" -> listOf("yes", "no", "unknown").joinToString(" | ") { lit(it) }
                    else -> "string"
                }
                rules.append("kv$i ::= ${lit(m.name)} ws \":\" ws ($value) ws\n")
                "kv$i"
            }
            return "root ::= \"{\" ws (kv (\",\" ws kv)*)? \"}\" ws\nkv ::= ${kvs.joinToString(" | ")}\n" + rules + BASE
        }

        fun objectGrammar(key: String, type: String): String =
            "root ::= \"{\" ws ${lit(key)} ws \":\" ws ${if (type == "integer") "integer" else "string"} \"}\" ws\n" + BASE

        private const val BASE = "string ::= \"\\\"\" ([^\"\\\\\\x7F\\x00-\\x1F] | \"\\\\\" ([\"\\\\/bfnrt] | \"u\" [0-9a-fA-F]{4}))* \"\\\"\" ws\n" +
            "integer ::= \"-\"? [0-9]{1,6} ws\nws ::= | \" \" | \"\\n\" [ \\t]{0,20}\n"

        private val BUILDING_WORDS = setOf("building", "block", "tower", "hall", "house", "annex", "wing", "campus", "centre",
            "center", "pavilion", "office", "site", "plaza", "park")
        private val DONT_KNOW = Regex("\\b(don'?t know|do not know|not sure|no idea|unsure|can'?t tell|cannot tell|dunno|not certain)\\b",
            RegexOption.IGNORE_CASE)
        val WORD_NUM: Map<String, Int> = "zero one two three four five six seven eight nine ten eleven twelve".split(" ")
            .mapIndexed { i, w -> w to i }.toMap() + mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
            "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10, "eleventh" to 11, "twelfth" to 12, "ground" to 1)

        private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(" ").filter { it.isNotEmpty() }

        /** Keep only values that are allowed and grounded in what the user said (llm.validate). */
        fun validate(data: JSONObject?, metas: List<FieldMeta>, userText: String): Map<String, Any> {
            val out = linkedMapOf<String, Any>()
            val words = norm(userText).toSet()
            val by = metas.associateBy { it.name }
            for (k in data?.keys()?.asSequence()?.toList() ?: emptyList()) {
                val raw = data!!.opt(k)
                val m = by[k] ?: continue
                if (raw == null || raw == JSONObject.NULL || raw.toString().isEmpty()) continue
                val v = raw.toString().trim()
                when {
                    v.lowercase() == "unknown" -> if (DONT_KNOW.containsMatchIn(userText)) out[k] = "unknown"
                    m.kind == "enum" -> if (v in m.values) out[k] = v
                    m.kind == "bool" -> if (v.lowercase() in setOf("yes", "no")) out[k] = v.lowercase() == "yes"
                    m.kind == "int" -> {
                        val nums = Regex("-?\\d+").findAll(v).map { it.value }.toList()
                            .ifEmpty { norm(v).mapNotNull { WORD_NUM[it]?.toString() } }
                        val said = Regex("\\d+").findAll(userText).map { it.value }.toSet() + words.mapNotNull { WORD_NUM[it]?.toString() }
                        if (nums.isNotEmpty() && nums[0].trimStart('-') in said) out[k] = nums[0].toInt()
                    }
                    else -> {
                        val vw = norm(v)
                        if (k == "building" && v.uppercase() !in setOf("N", "QRC") && vw.none { it in BUILDING_WORDS }) continue
                        if (v.length <= 3) {
                            if (Regex("\\b${Regex.escape(v)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(userText))
                                out[k] = if (v.all { it.isLetter() }) v.uppercase() else v
                        } else if (vw.isNotEmpty() && vw.count { it in words }.toDouble() / vw.size >= 0.6) out[k] = v
                    }
                }
            }
            return out
        }

        private val NUM_ALT = WORD_NUM.keys.joinToString("|")
        private val FLOOR_RE = Regex("\\b(?:(\\d+|$NUM_ALT)(?:st|nd|rd|th)?\\s+(?:floor|level)|(?:floor|level)\\s+(\\d+|$NUM_ALT))\\b",
            RegexOption.IGNORE_CASE)
        private val BUILDING_RE = Regex("\\b(QRC|N)\\b")
        private val N_CONTEXT = Regex("\\b(building|block|of|in|at)\\s+N\\b|\\bN\\b\\s*(building|,|floor)")
        private val NOW_RE = Regex("\\b(just now|right now|a (?:minute|moment) ago|just happened)\\b", RegexOption.IGNORE_CASE)

        /** Deterministic extraction for the most common location/time answers (runs before the LLM). */
        fun ruleExtract(text: String, want: Collection<String>): Map<String, Any> {
            val out = linkedMapOf<String, Any>()
            if ("floor" in want) {
                val m = FLOOR_RE.find(text)
                if (m != null) {
                    val w = (m.groups[1]?.value ?: m.groups[2]!!.value).lowercase()
                    out["floor"] = w.toIntOrNull() ?: WORD_NUM.getValue(w)
                } else if (Regex("\\bground floor\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) out["floor"] = 1
            }
            if ("building" in want) {
                val m = BUILDING_RE.find(text)
                if (m != null && (m.groupValues[1] == "QRC" || N_CONTEXT.containsMatchIn(text))) out["building"] = m.groupValues[1]
            }
            if ("occurred_at" in want) NOW_RE.find(text)?.let { out["occurred_at"] = it.groupValues[1].lowercase() }
            return out
        }
    }
}
