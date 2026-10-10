package org.seedhecode.incidentassistant.engine

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.seedhecode.core.inference.PipelineMode

class QwenResponseTest {
    private val empty = """{"fields":[],"dialogue_act":null,"tool_calls":[],"incident_groups":[]}"""
    private fun parse(raw: String) = QwenResponse.parse(raw, "Leak in Room 2", setOf("location"), PipelineMode.ASR_LLM)
    private fun rejects(raw: String) {
        try { parse(raw); fail("Invalid output accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun `empty extraction is valid only with complete contract`() {
        assertTrue(parse(empty).fields.isEmpty())
        rejects("{}")
    }
    @Test fun `valid evidence and confidence produce grounded field`() {
        val raw = JSONObject(empty).put("fields", org.json.JSONArray("""[{"field_name":"location","value":"Room 2","confidence":0.8,"evidence":"Room 2","status":"known"}]"""))
        assertEquals(8, parse(raw.toString()).fields.single().evidence.start)
    }
    @Test fun `hallucinated evidence is rejected`() {
        rejects("""{"fields":[{"field_name":"location","value":"Room 99","confidence":0.8,"evidence":"Room 99","status":"known"}],"dialogue_act":null,"tool_calls":[],"incident_groups":[]}""")
    }
    @Test fun `unsupported tool and unclassified form lookup rejected`() {
        for (name in listOf("submit_report", "get_form_fields")) {
            rejects(JSONObject(empty).put("tool_calls", org.json.JSONArray("""[{"name":"$name","arguments":{}}]""")).toString())
        }
    }
    @Test fun `bad confidence and status rejected`() {
        val raw = """{"field_name":"location","value":"Room 2","confidence":0.8,"evidence":"Room 2","status":"known"}"""
        for (field in listOf(JSONObject(raw).put("confidence", 2), JSONObject(raw).put("status", "confirmed"))) {
            rejects(JSONObject(empty).put("fields", org.json.JSONArray().put(field)).toString())
        }
    }
    @Test fun `model bundle mode selects only installed roles`() {
        val models = JSONObject().put("asr", JSONObject().put("version", "base").put("backend", "whisper.cpp-cpu"))
            .put("llm", JSONObject().put("version", "qwen").put("backend", "llama.cpp-cpu"))
        val pack = ModelPack(java.io.File("."), JSONObject().put("pipeline", "asr_llm").put("models", models))
        assertEquals(PipelineMode.ASR_LLM, pack.mode)
        assertEquals(setOf("asr", "llm"), pack.versions().keys)
    }
    @Test fun `known null and unknown guessed values rejected`() {
        for ((status, value) in listOf("known" to "null", "unknown" to "\"Room 2\"")) {
            rejects("""{"fields":[{"field_name":"location","value":$value,"confidence":0.8,"evidence":"Room 2","status":"$status"}],"dialogue_act":null,"tool_calls":[],"incident_groups":[]}""")
        }
    }
    @Test fun `export explicitly declares deferred stages and no classification`() {
        val result = org.seedhecode.core.inference.TurnResult(
            org.seedhecode.core.inference.TurnKey("reference", 1, 0),
            org.seedhecode.core.inference.Transcript("Leak", "Leak", "en", true),
            org.seedhecode.core.inference.Classification("not_run", emptyList(), emptyMap(), false),
            org.seedhecode.core.inference.Extraction(emptyList(), null, emptyList()),
            emptyList(), emptyList(), 1, mapOf("llm" to "qwen"),
            stageStatus = mapOf("cfc_filter" to "disabled", "laya_classifier" to "disabled", "llm" to "enabled"))
        val json = result.toJson()
        assertEquals(0, json.getJSONArray("candidate_incident_types").length())
        val metadata = json.getJSONObject("metadata")
        assertEquals("not_run", metadata.getString("classification_outcome"))
        assertEquals("no_laya_prediction", metadata.getString("classification_source"))
        assertEquals("disabled", metadata.getJSONObject("stage_status").getString("cfc_filter"))
        assertEquals(0, metadata.getJSONArray("relevance").length())
    }
}
