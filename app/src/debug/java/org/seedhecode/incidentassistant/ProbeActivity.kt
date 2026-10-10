package org.seedhecode.incidentassistant

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.seedhecode.incidentassistant.engine.AssistantView
import org.seedhecode.incidentassistant.engine.IncidentEngine
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * adb shell am start -n org.seedhecode.incidentassistant/.ProbeActivity --es script <file in files/probe/>
 * Script lives in files/probe/ (scripts/probe_kit.sh copies it there with run-as). One typed user turn per line.
 * Writes <script>.out.json next to it and logs "Probe".
 */
class ProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { textSize = 12f; setPadding(24, 24, 24, 24) }
        setContentView(text)
        val name = intent.getStringExtra("script") ?: "script.txt"
        val dir = File(filesDir, "probe")
        Thread {
            val out = JSONObject()
            try {
                val lines = File(dir, name).readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                var latest: AssistantView? = null
                var latch = CountDownLatch(1)
                val t0 = System.nanoTime()
                val engine = IncidentEngine(this, {}, {}, { v -> latest = v; latch.countDown() },
                    { e -> Log.e("Probe", e); latch.countDown() })
                out.put("load_ms", JSONObject(engine.loadMs as Map<*, *>)).put("load_total_ms", (System.nanoTime() - t0) / 1_000_000)
                val turns = JSONArray()
                for (line in lines) {
                    if (latest?.done == true) break
                    latch = CountDownLatch(1)
                    engine.processText(line)
                    check(latch.await(120, TimeUnit.SECONDS)) { "turn timed out: $line" }
                    val v = latest!!
                    val t = JSONObject().put("user", line).put("assistant", v.question).put("question_by", v.questionBy)
                        .put("note", v.note).put("type", v.type).put("ready", v.ready).put("done", v.done).put("outcome", v.outcome)
                        .put("values", JSONObject(v.values.mapValues { it.value.value }))
                        .put("top", JSONArray(v.topTypes.map { JSONArray().put(it.first).put(it.second) }))
                        .put("sentences", JSONArray(v.sentences.map { JSONObject().put("text", it.text).put("relevant", it.relevant).put("fields", JSONArray(it.fields)) }))
                        .put("classifier_ms", v.timings?.classifierMs).put("turn_ms", v.timings?.turnMs)
                        .put("llm", JSONArray(v.timings?.llm?.map { JSONObject().put("kind", it.kind).put("ms", it.ms) } ?: emptyList<JSONObject>()))
                        .put("routing", v.routing?.let { JSONObject().put("priority", it.priority).put("team", it.team).put("notify", JSONArray(it.notify)) })
                    turns.put(t)
                    Log.i("Probe", t.toString())
                    runOnUiThread { text.append("you: $line\nbot: ${v.question}\n\n") }
                }
                out.put("turns", turns)
                engine.close()
            } catch (error: Throwable) {
                Log.e("Probe", "failed", error); out.put("error", error.toString())
            }
            File(dir, "$name.out.json").writeText(out.toString(1))
            Log.i("Probe", "DONE $name")
            runOnUiThread { text.append("DONE\n") }
        }.start()
    }
}
