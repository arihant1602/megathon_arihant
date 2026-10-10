package org.seedhecode.incidentassistant.assistant

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Kotlin port vs. the laya/ Python reference (fixture made by running laya code on self-written text). */
class LayaParityTest {
    private val ref = JSONObject(File("src/test/resources/laya_reference.json").readText())
    private val tax = Taxonomy(File("src/main/assets/incident_taxonomy.json").readText())
    private val tokenizer = LayaTokenizer(File("src/main/assets/laya_tokenizer.json").readText())
    private val labels = File("src/main/assets/laya_labels.json").readText()

    private fun ints(a: JSONArray) = IntArray(a.length()) { a.getInt(it) }
    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    private fun plain(v: Any?): Any? = when (v) {
        is JSONObject -> v.keys().asSequence().associateWith { plain(v.get(it)) }
        is JSONArray -> (0 until v.length()).map { plain(v.get(it)) }
        JSONObject.NULL -> null
        is Number -> v.toLong()
        else -> v
    }

    @Test fun tokenizerMatchesHuggingFace() {
        val cases = ref.getJSONArray("tokens")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            assertEquals(c.getString("text"), ints(c.getJSONArray("ids")).toList(), tokenizer.encode(" " + c.getString("text")).toList())
        }
    }

    @Test fun classifierInputMatchesBuildSamples() {
        val clf = LayaClassifier(tokenizer, labels, object : LayaRunner {
            override fun run(ids: IntArray, mask: IntArray) = FloatArray(0); override fun close() {}
        })
        assertEquals(30, clf.types.size); assertEquals(60, clf.fields.size)
        val cases = ref.getJSONArray("samples")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val turns = (0 until c.getJSONArray("turns").length()).map { strings(c.getJSONArray("turns").getJSONArray(it)) }
            val s = clf.sample(turns)
            assertEquals(ints(c.getJSONArray("ids")).toList(), s.ids.toList())
            assertEquals(ints(c.getJSONArray("mask")).toList(), s.mask.toList())
            assertEquals(ints(c.getJSONArray("sent")).toList(), s.sentIndex.toList())
        }
    }

    @Test fun ruleExtractMatches() {
        val cases = ref.getJSONArray("rules")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val out = Extractor.ruleExtract(c.getString("text"), strings(c.getJSONArray("want")))
            assertEquals(c.getString("text"), plain(c.getJSONObject("out")), plain(JSONObject(out)))
        }
    }

    @Test fun validatorMatches() {
        val cases = ref.getJSONArray("validate")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val f = c.getString("field")
            val out = Extractor.validate(JSONObject().put(f, c.getString("value")), listOf(tax.fieldMeta(f)), c.getString("text"))
            assertEquals("$f=${c.getString("value")} in '${c.getString("text")}'", plain(c.getJSONObject("out")), plain(JSONObject(out)))
        }
    }

    @Test fun endPhrasesMatch() {
        val ends = ref.getJSONObject("ends")
        for (k in ends.keys()) assertEquals(k, ends.getBoolean(k), Taxonomy.isEnd(k))
    }

    /** Same scripted classifier probabilities, no LLM: every action, note, value and routing must match form.py. */
    @Test fun assistantConversationsMatch() {
        val clfTypes = strings(JSONObject(labels).getJSONArray("types"))
        val convs = ref.getJSONArray("convs")
        for (i in 0 until convs.length()) {
            val c = convs.getJSONObject(i)
            val script = c.getJSONArray("probs")
            var call = 0
            val a = Assistant(tax, { turns ->
                val p = clfTypes.associateWith { 0.0 }.toMutableMap()
                val row = script.getJSONObject(minOf(call++, script.length() - 1))
                row.keys().forEach { p[it] = row.getDouble(it) }
                Classification(p, turns.last().map { SentenceInfo(turns.size - 1, it, 0.9, emptyList()) }, 0.0, 0)
            }, null, 0.8, summarise = false, llmQuestions = false)
            val turns = c.getJSONArray("turns")
            for (t in 0 until turns.length()) {
                val e = turns.getJSONObject(t)
                val act = a.step(e.getString("user"))
                val where = "${c.getString("name")} turn ${t + 1}"
                assertEquals(where, e.getString("kind"), act.kind)
                assertEquals(where, e.getString("text"), act.text)
                assertEquals(where, strings(e.getJSONArray("fields")), act.fields)
                assertEquals(where, e.getString("note"), a.note)
                assertEquals(where, e.opt("type").takeUnless { it == JSONObject.NULL }, a.state.type)
                assertEquals(where, plain(e.getJSONObject("values")), plain(JSONObject(a.state.values.mapValues { it.value.value })))
                assertEquals(where, e.getBoolean("ready"), a.state.ready)
                assertEquals(where, e.getBoolean("done"), a.state.done)
                val r = e.opt("routing")
                if (r is JSONObject) {
                    val k = a.routing!!
                    assertEquals(where, r.getString("priority"), k.priority)
                    assertEquals(where, r.getString("team"), k.team)
                    assertEquals(where, strings(r.getJSONArray("notify")), k.notify)
                    assertEquals(where, strings(r.getJSONArray("secondary")), k.secondary)
                    assertEquals(where, r.getInt("sla_min"), k.slaMin)
                } else assertEquals(where, null, a.routing)
            }
        }
    }

    /** assistant.py --selftest rule/policy cases. */
    @Test fun selftestCases() {
        val p = FormPolicy(tax)
        var st = FormState().apply { type = "water_leak"; set("near_electrical", true, "x", 1) }
        val r = p.route(st, "")!!
        assertTrue(r.priority == "P1" && "Electrical Technician" in r.notify)
        st = FormState().apply { type = "pest_sighting"; set("pest_kind", "snake", "x", 1) }
        assertEquals("P1", p.route(st, "")!!.priority)
        st = FormState().apply { type = "elevator_fault"; set("persons_trapped", "unknown", "x", 1) }
        assertEquals("P1", p.route(st, "")!!.priority)
        st = FormState().apply { type = "water_leak" }
        assertEquals(listOf("building", "floor"), p.nextAction(st, mapOf("water_leak" to 0.95), 0.8, 1).fields)
        st.set("building", "N", "x", 1); st.set("floor", 4, "x", 1)
        val act = p.nextAction(st, mapOf("water_leak" to 0.95), 0.8, 2)
        assertTrue(st.ready && act.fields == listOf("water_spread", "near_electrical") && "done" in act.text)
        p.nextAction(st, mapOf("water_leak" to 0.95), 0.8, 3, ended = true)
        assertTrue(st.done && st.outcome == "submitted")
        val v = Extractor.validate(JSONObject().put("water_spread", "flooding").put("space_id", "Room 9 basement").put("floor", "4"),
            listOf(tax.fieldMeta("water_spread"), tax.fieldMeta("space_id"), tax.fieldMeta("floor")), "the pantry on floor 4")
        assertEquals(mapOf("floor" to 4), v)
    }

    @Test fun grammarsAreWellFormed() {
        val g = Extractor.schemaGrammar(listOf(tax.fieldMeta("leak_source"), tax.fieldMeta("near_electrical"), tax.fieldMeta("floor")))
        assertTrue(g, g.startsWith("root ::= \"{\" ws (kv (\",\" ws kv)*)? \"}\" ws"))
        assertTrue(g, g.contains("kv0 ::= \"\\\"leak_source\\\"\" ws \":\" ws (\"\\\"pipe\\\"\" | "))
        assertTrue(g, g.contains("kv1 ::= \"\\\"near_electrical\\\"\" ws \":\" ws (\"\\\"yes\\\"\" | \"\\\"no\\\"\" | \"\\\"unknown\\\"\")"))
        assertTrue(g, g.contains("kv2 ::= \"\\\"floor\\\"\" ws \":\" ws (string)"))
    }
}
