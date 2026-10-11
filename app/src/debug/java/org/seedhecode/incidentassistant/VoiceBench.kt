package org.seedhecode.incidentassistant

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import org.json.JSONArray
import org.json.JSONObject
import org.seedhecode.core.inference.AudioFrame
import org.seedhecode.incidentassistant.engine.AssistantView
import org.seedhecode.incidentassistant.engine.IncidentEngine
import org.seedhecode.incidentassistant.engine.NeuralSpeaker
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Voice-path latency benchmark (debug builds). The user's lines are synthesized with the Piper voice and streamed into
 * the real IncidentEngine in 20 ms frames at real-time pace over low background noise, exactly like microphone
 * audio, so pause endpointing, Whisper (NPU encoder), Laya (NPU), Qwen and the reply voice all run as in a live
 * conversation. Script: one user line per line, conversations separated by "---".
 *
 * Per turn: end of user speech -> turn detected -> transcript + answer text -> first reply audio synthesized ->
 * reply audible (first frame played), plus every stage time, transcript accuracy, memory, CPU and temperature.
 */
class VoiceBench(private val activity: Activity) {
    private val rnd = Random(11)

    fun run(script: List<String>): JSONObject {
        val dir = activity.filesDir
        var view: AssistantView? = null
        var viewAt = 0L; var endpointAt = 0L; var noSpeech = false
        var viewLatch = CountDownLatch(1)
        val t0 = System.nanoTime()
        val engine = IncidentEngine(activity, onCaption = {}, onProcessing = { endpointAt = System.nanoTime() },
            onView = { v -> view = v; viewAt = System.nanoTime(); viewLatch.countDown() },
            onError = { viewLatch.countDown() }, onNoSpeech = { noSpeech = true; viewLatch.countDown() })
        val loadMs = (System.nanoTime() - t0) / 1_000_000
        var speakLatch = CountDownLatch(1)
        val speaker = NeuralSpeaker(File(dir, "models/voice"), { speakLatch.countDown() })
        val voiceDir = File(dir, "models/voice")
        val userVoice = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(numThreads = 4,
            vits = OfflineTtsVitsModelConfig(model = voiceDir.listFiles()!!.first { it.name.endsWith(".onnx") }.path,
                tokens = File(voiceDir, "tokens.txt").path, dataDir = File(voiceDir, "espeak-ng-data").path))))
        val conversations = script.joinToString("\n").split(Regex("\\n---+\\n")).map { block ->
            block.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") } }.filter { it.isNotEmpty() }

        val turns = JSONArray()
        for ((ci, conv) in conversations.withIndex()) {
            viewLatch = CountDownLatch(1); engine.reset(); viewLatch.await(10, TimeUnit.SECONDS)
            for ((ti, line) in conv.withIndex()) {
                if (view?.done == true) break
                val pcm = toPcm16k(userVoice.generate(line, 0, 1.0f).let { it.samples to it.sampleRate })
                endpointAt = 0L; noSpeech = false; viewLatch = CountDownLatch(1)
                val cpu0 = cpuMs(); val wall0 = System.nanoTime()
                engine.beginSession("bench-$ci-$ti", 16000)
                var sample = 0L
                fun feed(chunk: ShortArray) {
                    engine.accept(AudioFrame(chunk, 16000, sample)); sample += chunk.size
                    Thread.sleep(20)   // real-time pace, like the capture thread
                }
                repeat(35) { feed(noise(320)) }                                  // 700 ms of room before speaking
                for (o in pcm.indices step 320) feed(mix(pcm.copyOfRange(o, minOf(pcm.size, o + 320))))
                val speechEnd = System.nanoTime()
                var waited = 0
                while (endpointAt == 0L && waited < 250) { feed(noise(320)); waited++ }   // silence until the turn ends
                val answered = viewLatch.await(90, TimeUnit.SECONDS) && !noSpeech
                val v = view
                val row = JSONObject().put("conversation", ci + 1).put("turn", ti + 1).put("said", line)
                    .put("speech_s", pcm.size / 16000.0)
                if (!answered || v == null || endpointAt == 0L) { turns.put(row.put("error", if (noSpeech) "ignored as non-speech" else "no answer")); continue }
                // Speak the answer exactly as the app does and wait until it has been played.
                speakLatch = CountDownLatch(1)
                speaker.speak(v.question)
                speakLatch.await(60, TimeUnit.SECONDS)
                val heard = v.chat.lastOrNull { it.first == "user" }?.second.orEmpty()
                val t = v.timings
                val audible = speaker.lastPlaybackStartNanos
                row.put("heard", heard).put("wer", wer(line, heard)).put("answer", v.question).put("action", v.actionKind)
                    .put("endpoint_ms", ms(endpointAt - speechEnd))
                    .put("asr_ms", t?.whisperMs ?: JSONObject.NULL)
                    .put("classifier_ms", t?.classifierMs ?: JSONObject.NULL)
                    .put("assistant_step_ms", t?.turnMs ?: JSONObject.NULL)
                    .put("llm", JSONArray(t?.llm?.map { JSONObject().put("kind", it.kind).put("ms", it.ms) } ?: emptyList<JSONObject>()))
                    .put("text_ready_ms", ms(viewAt - endpointAt))
                    .put("tts_first_audio_ms", speaker.lastFirstAudioMs)
                    .put("audible_after_text_ms", if (audible > 0) ms(audible - speaker.lastSpeakNanos) else JSONObject.NULL)
                    .put("speech_end_to_reply_audible_ms", if (audible > 0) ms(audible - speechEnd) else JSONObject.NULL)
                    .put("turn_detected_to_reply_audible_ms", if (audible > 0) ms(audible - endpointAt) else JSONObject.NULL)
                    .put("cpu_cores_avg", (cpuMs() - cpu0) / ms(System.nanoTime() - wall0).coerceAtLeast(1.0))
                    .put("pss_mb", Debug.getPss() / 1024.0)
                    .put("battery_temp_c", batteryTemp()).put("thermal_headroom", headroom())
                    .put("ready", v.ready).put("done", v.done).put("type", v.type ?: JSONObject.NULL)
                turns.put(row)
                android.util.Log.i("VoiceBench", row.toString())
            }
        }
        userVoice.release(); speaker.close(); engine.close()
        return JSONObject().put("load_ms", loadMs).put("engine_load_ms", JSONObject(engine.loadMs as Map<*, *>)).put("turns", turns)
            .put("device", android.os.Build.MODEL + " / " + android.os.Build.SOC_MODEL)
    }

    private fun ms(nanos: Long) = nanos / 1_000_000.0
    private fun noise(n: Int) = ShortArray(n) { ((rnd.nextFloat() * 2 - 1) * 16).toInt().toShort() }          // ~-66 dBFS
    private fun mix(s: ShortArray) = ShortArray(s.size) { (s[it] + (rnd.nextFloat() * 2 - 1) * 16).toInt().coerceIn(-32768, 32767).toShort() }

    /** Linear resample of the synthesized voice to 16 kHz PCM16 (what the engine gets from the mic). */
    private fun toPcm16k(audio: Pair<FloatArray, Int>): ShortArray {
        val (x, rate) = audio
        val n = (x.size.toLong() * 16000 / rate).toInt()
        return ShortArray(n) { i ->
            val p = i * rate / 16000.0; val k = p.toInt(); val f = (p - k).toFloat()
            val a = x[minOf(k, x.size - 1)]; val b = x[minOf(k + 1, x.size - 1)]
            ((a + (b - a) * f).coerceIn(-1f, 1f) * 32767 * 0.7f).toInt().toShort()
        }
    }

    /** Word error rate (normalized words, Levenshtein). */
    private fun wer(ref: String, hyp: String): Double {
        fun words(s: String) = s.lowercase().replace(Regex("[^a-z0-9' ]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val r = words(ref); val h = words(hyp)
        if (r.isEmpty()) return if (h.isEmpty()) 0.0 else 1.0
        val d = Array(r.size + 1) { IntArray(h.size + 1) }
        for (i in 0..r.size) d[i][0] = i
        for (j in 0..h.size) d[0][j] = j
        for (i in 1..r.size) for (j in 1..h.size)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (r[i - 1] == h[j - 1]) 0 else 1)
        return d[r.size][h.size].toDouble() / r.size
    }

    /** CPU time of this process (user + system) in ms, from /proc/self/stat (clock ticks of 10 ms). */
    private fun cpuMs(): Double = runCatching {
        val f = File("/proc/self/stat").readText().substringAfterLast(") ").split(" ")
        (f[11].toLong() + f[12].toLong()) * 10.0
    }.getOrDefault(0.0)

    private fun batteryTemp(): Double {
        val i: Intent? = activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
    }

    private fun headroom(): Double = runCatching {
        (activity.getSystemService(PowerManager::class.java)).getThermalHeadroom(0).toDouble()
    }.getOrDefault(Double.NaN).let { if (it.isNaN()) -1.0 else it }
}
