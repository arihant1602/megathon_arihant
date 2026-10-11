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
        val wav = intent.getStringExtra("wav")
        val levelsSeconds = intent.getIntExtra("levels", 0)
        val dir = File(filesDir, "probe")
        if (intent.getBooleanExtra("voice", false)) {   // voice-path latency benchmark (VoiceBench)
            Thread {
                val result = runCatching { VoiceBench(this).run(File(dir, name).readLines()) }
                    .getOrElse { JSONObject().put("error", it.toString()).also { _ -> Log.e("Probe", "voice bench failed", it) } }
                File(dir, "$name.out.json").writeText(result.toString(1))
                Log.i("Probe", "DONE voice $name")
                runOnUiThread { text.append("DONE\n") }
            }.start()
            return
        }
        Thread {
            val out = JSONObject()
            try {
                if (levelsSeconds > 0) {   // room measurement: same mic source + endpoint, no assistant
                    val rate = 16000
                    @Suppress("MissingPermission")
                    val rec = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
                        android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, rate)
                    val ep = org.seedhecode.core.inference.NoiseRobustEndpoint()
                    val frameDb = ArrayList<Double>(); val turns = JSONArray()
                    val buf = ShortArray(320); rec.startRecording()
                    val end = System.currentTimeMillis() + levelsSeconds * 1000L
                    var t = 0
                    while (System.currentTimeMillis() < end) {
                        val n = rec.read(buf, 0, buf.size); if (n <= 0) continue
                        val f = FloatArray(n) { buf[it] / 32768f }
                        frameDb += 20 * kotlin.math.log10(maxOf(1e-6, kotlin.math.sqrt(f.fold(0.0) { a, v -> a + v * v } / n)))
                        val ev = ep.accept(f); t += 20
                        if (ev.speechStarted) Log.i("Probe", "start at ${t} ms, floor ${"%.1f".format(ep.noiseDb)}")
                        if (ev.turnEnded) turns.put(JSONObject().put("end_ms", t).put("floor", ep.lastTurnFloorDb).put("peak", ep.lastTurnPeakDb)
                            .put("seconds", ev.segment!!.size / 16000.0))
                    }
                    rec.stop(); rec.release()
                    val sorted = frameDb.sorted()
                    out.put("levels", JSONObject().put("p10", sorted[sorted.size / 10]).put("p50", sorted[sorted.size / 2])
                        .put("p90", sorted[sorted.size * 9 / 10]).put("p99", sorted[sorted.size * 99 / 100]).put("max", sorted.last()).put("turns", turns))
                }
                if (wav != null) {   // Whisper timing on a 16 kHz mono 16-bit WAV (44-byte header), 3 runs
                    val bytes = File(dir, wav).readBytes()
                    val pcm = FloatArray((bytes.size - 44) / 2) { i ->
                        ((bytes[44 + 2 * i].toInt() and 0xff) or (bytes[45 + 2 * i].toInt() shl 8)).toShort() / 32768f }
                    org.seedhecode.incidentassistant.engine.configureDsp(applicationInfo.nativeLibraryDir)
                    val result = JSONObject().put("audio_s", pcm.size / 16000.0)
                    val encoder = File(filesDir, "models/whisper_base_encoder_fp16_htp.dlc").takeIf { it.isFile }
                    val short = File(filesDir, "models/whisper_base_encoder_10s_fp16_htp.dlc").takeIf { it.isFile }
                    for ((label, enc) in listOf("npu" to encoder, "cpu" to null)) {
                        if (label == "npu" && enc == null) continue
                        val whisper = org.seedhecode.incidentassistant.engine.WhisperSpeech(File(filesDir, "models/ggml-base.bin").absolutePath, enc, short)
                        val runs = JSONArray()
                        for (clip in listOf(pcm, pcm.copyOf(minOf(pcm.size, 16000 * 4)))) repeat(2) {   // full clip, first 4 s
                            val t = System.nanoTime(); val text = whisper.transcribe(clip, "en", true).text
                            runs.put(JSONObject().put("audio_s", clip.size / 16000.0).put("ms", (System.nanoTime() - t) / 1_000_000).put("text", text))
                        }
                        whisper.close()
                        result.put(label, runs)
                    }
                    out.put("whisper", result)
                }
                // TTS speed for files/models/voice and every model dir in files/tts_bench/*: synthesis time vs audio length.
                val ttsDirs = listOf(File(filesDir, "models/voice")) + (File(filesDir, "tts_bench").listFiles()?.sortedBy { it.name } ?: emptyList())
                val ttsOut = JSONObject()
                for (d in ttsDirs.filter { it.isDirectory }) for (threads in listOf(4, 8)) {
                    val onnx = d.listFiles()!!.first { it.name.endsWith(".onnx") }.path
                    val data = File(d, "espeak-ng-data").path; val tokens = File(d, "tokens.txt").path
                    val voices = File(d, "voices.bin")
                    val model = when {
                        voices.isFile && "kitten" in d.name -> com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(numThreads = threads,
                            kitten = com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig(model = onnx, voices = voices.path, tokens = tokens, dataDir = data))
                        voices.isFile -> com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(numThreads = threads,
                            kokoro = com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig(model = onnx, voices = voices.path, tokens = tokens, dataDir = data))
                        else -> com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(numThreads = threads,
                            vits = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(model = onnx, tokens = tokens, dataDir = data))
                    }
                    val t0 = System.nanoTime()
                    val tts = com.k2fsa.sherpa.onnx.OfflineTts(config = com.k2fsa.sherpa.onnx.OfflineTtsConfig(model = model))
                    val loadMs = (System.nanoTime() - t0) / 1_000_000
                    val runs = JSONArray()
                    for (text in listOf("Which building are you in? And which floor?",
                        "Got it, your report is ready and Facilities Engineering will handle it. Is the water spreading?")) repeat(2) {
                        val t = System.nanoTime(); var first = -1L
                        val audio = tts.generateWithCallback(text, if (voices.isFile) 3.coerceAtMost(tts.numSpeakers() - 1) else 0, 1.2f) {
                            if (first < 0) first = (System.nanoTime() - t) / 1_000_000; 1 }
                        if (it == 1) runs.put(JSONObject().put("first_audio_ms", first).put("total_ms", (System.nanoTime() - t) / 1_000_000)
                            .put("audio_s", audio.samples.size.toDouble() / audio.sampleRate))
                    }
                    tts.release()
                    ttsOut.put("${d.name}@$threads", JSONObject().put("load_ms", loadMs).put("runs", runs))
                }
                out.put("tts", ttsOut)
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
