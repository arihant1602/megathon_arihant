package org.seedhecode.core.inference

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReferencePipelineTest {
    private val key = TurnKey("reference", 1, 0)
    private val noTools = object : LocalTools {
        override fun execute(key: TurnKey, request: ToolRequest): ToolResult = error("Unexpected tool")
    }
    private fun run(text: String, extractor: ExtractionModel, tools: LocalTools = noTools): Pair<TurnResult?, String?> {
        val done = CountDownLatch(1)
        var result: TurnResult? = null
        var failure: String? = null
        val pipeline = ConversationPipeline(null, null, extractor, tools,
            RevisionGate().apply { activate(key) }, mapOf("llm" to "test-double"),
            { result = it; done.countDown() }, { _, error -> failure = error; done.countDown() })
        try {
            pipeline.process(key, Transcript(text, text, "en", true), "Where is the leak?")
            assertTrue("Pipeline timed out", done.await(3, TimeUnit.SECONDS))
            return result to failure
        } finally { pipeline.close() }
    }
    private fun extractor(block: (String, List<ToolResult>, Classification?) -> Extraction) = object : ExtractionModel {
        override fun extract(text: String, question: String?, toolResults: List<ToolResult>, classification: Classification?) =
            block(text, toolResults, classification)
        override fun close() = Unit
    }

    @Test fun `disabled stages preserve full transcript and evidence offsets without predictions`() {
        val text = "Nice weather.\n  A leak in Room 2."
        val (result, error) = run(text, extractor { actual, _, classification ->
            assertEquals(text, actual)
            assertEquals("not_run", classification!!.outcome)
            val start = actual.indexOf("Room 2")
            Extraction(listOf(CandidateField("location", "Room 2", 0.8,
                Evidence("Room 2", start, start + 6))), "answer", emptyList())
        })
        assertNull(error)
        assertEquals(text.indexOf("Room 2"), result!!.extraction.fields.single().evidence.start)
        assertTrue(result.classification.candidates.isEmpty())
        assertTrue(result.classification.probabilities.isEmpty())
        assertTrue(result.relevance.isEmpty())
        assertEquals("disabled", result.stageStatus["cfc_filter"])
        assertEquals("disabled", result.stageStatus["laya_classifier"])
        assertEquals(setOf("llm_and_tools"), result.stageTimingsMs.keys)
    }

    @Test fun `zone tool loop runs with disabled classifiers`() {
        val tools = object : LocalTools {
            override fun execute(key: TurnKey, request: ToolRequest) =
                ToolResult(request.name, request.arguments, "{\"requires_confirmation\":true}")
        }
        val (result, error) = run("Room 2", extractor { _, results, _ ->
            if (results.isEmpty()) Extraction(emptyList(), "answer",
                listOf(ToolRequest("get_zone_details", mapOf("zone_name" to "Room 2"))))
            else Extraction(emptyList(), "answer", emptyList())
        }, tools)
        assertNull(error)
        assertEquals("get_zone_details", result!!.functionCalls.single().name)
    }

    @Test fun `form lookup cannot guess incident type without classifier`() {
        val (result, error) = run("Leak", extractor { _, _, _ ->
            Extraction(emptyList(), "answer", listOf(ToolRequest("get_form_fields",
                mapOf("incident_type_id" to "water_leak"))))
        })
        assertNull(result)
        assertTrue(error!!.contains("requires an incident classification"))
    }

    @Test fun `explicit unknown retains null and evidence`() {
        val (result, error) = run("I don't know", extractor { text, _, _ ->
            Extraction(listOf(CandidateField("location", null, 0.8, Evidence(text, 0, text.length), "unknown")),
                "uncertainty", emptyList())
        })
        assertNull(error)
        assertEquals("unknown", result!!.extraction.fields.single().status)
        assertNull(result.extraction.fields.single().value)
    }

    @Test fun `disabled stages still reject fabricated evidence`() {
        val (result, error) = run("Room 2", extractor { _, _, _ ->
            Extraction(listOf(CandidateField("location", "Room 99", 0.9,
                Evidence("Room 99", 0, 7))), "answer", emptyList())
        })
        assertNull(result)
        assertTrue(error!!.contains("evidence"))
    }

    @Test fun `pipeline modes reject unsupported manifests`() {
        assertEquals(setOf("asr", "llm"), PipelineMode.parse("asr_llm").roles)
        assertEquals(4, PipelineMode.parse("full").roles.size)
        try { PipelineMode.parse("auto"); fail("Unknown mode accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun `blank input does not run extraction or fabricate a classification`() {
        val (result, error) = run(" \n ", extractor { _, _, _ -> error("Blank input reached LLM") })
        assertNull(error)
        assertEquals("not_run", result!!.classification.outcome)
        assertEquals("not_run", result.stageStatus["llm"])
        assertTrue(result.extraction.fields.isEmpty())
        assertTrue(result.stageTimingsMs.isEmpty())
    }
}
