package org.seedhecode.incidentassistant.engine

import android.content.Context
import org.seedhecode.core.inference.*
import org.seedhecode.incidentassistant.assistant.*
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Sideloaded model files in internal storage (scripts/push_models_kit.sh; never bundled, never downloaded). */
class OnDeviceModels(context: Context) {
    val dir = File(context.filesDir, "models")
    val laya = File(dir, "laya_w8a16_htp.dlc")
    val whisper = File(dir, "ggml-base.bin")
    /** Optional: Whisper's encoder as an FP16 HTP DLC; present -> encoder on the NPU. */
    val whisperEncoder = File(dir, "whisper_base_encoder_fp16_htp.dlc")
    val whisperEncoderShort = File(dir, "whisper_base_encoder_10s_fp16_htp.dlc")
    val qwen = File(dir, "Qwen3-0.6B-Q8_0.gguf")
    fun missing() = listOf(laya, whisper, qwen).filter { !it.isFile || it.length() == 0L }.map { it.name }
}

/** The DSP loader finds the SNPE HTP skel in the app's native lib dir. Call before any SNPE model loads. */
fun configureDsp(nativeLibDir: String) = android.system.Os.setenv("ADSP_LIBRARY_PATH",
    "$nativeLibDir;/vendor/lib/rfsa/adsp;/vendor/dsp/cdsp;/system/lib/rfsa/adsp;/dsp", true)

/** Fine-tuned Laya W8A16 on the Hexagon v79 NPU via SNPE (int32 user buffers), loaded once and kept warm. */
class SnpeLayaRunner(dlc: File, nativeLibDir: String) : LayaRunner {
    private val handle: Long
    init {
        check(NativeSnpe.available) { "This APK has no SNPE runtime. Build with -PsnpeSdk=<QAIRT SDK>." }
        configureDsp(nativeLibDir)
        handle = NativeSnpe.load(dlc.absolutePath)
    }
    override fun run(ids: IntArray, mask: IntArray): FloatArray = NativeSnpe.run(handle, ids, mask)
    override fun close() = NativeSnpe.release(handle)
}

/** Qwen3-0.6B (stock GGUF for now) on llama.cpp CPU, thinking off, output constrained by a GBNF grammar. */
class LlamaLlm(gguf: File) : SmallLlm, AutoCloseable {
    private val handle: Long
    init {
        check(NativeLlama.available) { "This APK has no llama.cpp runtime. Build with -PnativeModels=true." }
        handle = NativeLlama.load(gguf.absolutePath)
        check(handle != 0L) { "Cannot load the Qwen GGUF model." }
    }
    override fun chat(messages: List<Msg>, grammar: String, maxTokens: Int): String {
        val prompt = messages.joinToString("") { "<|im_start|>${it.role}\n${it.content}<|im_end|>\n" } +
            "<|im_start|>assistant\n<think>\n\n</think>\n\n"
        return String(NativeLlama.generateGrammar(handle, prompt, grammar, maxTokens), Charsets.UTF_8).trim()
    }
    fun cancel() = NativeLlama.cancel(handle)
    override fun close() = NativeLlama.release(handle)
}

/** Everything the UI shows after a turn (immutable copy of the assistant state). */
data class AssistantView(
    val chat: List<Pair<String, String>>,
    val question: String,
    val questionBy: String,
    val type: String?, val typeLabel: String?, val typeSource: String?, val typeConf: Double,
    val team: String?,
    val values: Map<String, FieldValue>,
    val requiredFields: List<String>,
    val essential: List<String>,
    val missingEssentials: List<String>,
    val ready: Boolean, val done: Boolean, val outcome: String?,
    val routing: Routing?, val summary: String,
    val topTypes: List<Pair<String, Double>>,
    val sentences: List<SentenceInfo>,
    val note: String,
    val timings: Timings?,
    val actionKind: String?,                  // last assistant move: ask/detail/confirm/open/offer_end/submit/out_of_scope
    val actionFields: List<String>,           // fields the open question asks for
    val confirmOptions: List<Pair<String, String>>,   // "Is it (1) A, (2) B ...": type id -> label
    val fieldMeta: Map<String, FieldMeta>,    // every field shown on the form
    val whisperOnNpu: Boolean,
)

data class Timings(val whisperMs: Long?, val classifierMs: Double, val llm: List<LlmStat>, val turnMs: Long)

/**
 * Voice -> text with main's pause endpoint + Whisper; text -> our pipeline (Laya on the NPU, form policy,
 * Qwen extraction/questions). One inference lane so turns are processed in order; models stay loaded.
 */
class IncidentEngine(context: Context,
    private val onCaption: (String) -> Unit,
    private val onProcessing: () -> Unit,
    private val onView: (AssistantView) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningReady: () -> Unit = {},
    private val onNoSpeech: () -> Unit = {},
) : VoiceEngine {
    private val audio = worker("incident-audio", 64)
    private val inference = worker("incident-inference", 4)
    private val epoch = AtomicLong()
    private val models = OnDeviceModels(context)
    private val speech: WhisperSpeech
    private val llm: LlamaLlm
    private val classifier: LayaClassifier
    private val assistant: Assistant
    private var resampler = PcmResampler(16000)
    // Ends a turn on a ~0.9 s pause even with fans/AC/chatter in the room (see NoiseRobustEndpoint).
    private val endpoint = NoiseRobustEndpoint()
    private var readySent = false
    private var samples = 0L
    private var previewAt = 0L
    private val english = StringBuilder()
    private var whisperMs = 0L
    @Volatile private var closed = false
    @Volatile private var accepting = false
    val loadMs: Map<String, Long>

    init {
        val missing = models.missing()
        check(missing.isEmpty()) { "Missing model files in ${models.dir}: ${missing.joinToString()}" }
        val assets = context.assets
        fun asset(name: String) = assets.open(name).bufferedReader().use { it.readText() }
        val times = linkedMapOf<String, Long>()
        fun <T> timed(name: String, block: () -> T): T { val t0 = System.nanoTime(); return block().also { times[name] = (System.nanoTime() - t0) / 1_000_000 } }
        var s: WhisperSpeech? = null; var l: LlamaLlm? = null; var r: SnpeLayaRunner? = null
        try {
            val tax = Taxonomy(asset("incident_taxonomy.json"))
            val tokenizer = timed("tokenizer") { LayaTokenizer(asset("laya_tokenizer.json")) }
            r = timed("laya_npu") { SnpeLayaRunner(models.laya, context.applicationInfo.nativeLibraryDir) }
            classifier = LayaClassifier(tokenizer, asset("laya_labels.json"), r)
            s = timed("whisper") { WhisperSpeech(models.whisper.absolutePath,
                models.whisperEncoder.takeIf { it.isFile }, models.whisperEncoderShort.takeIf { it.isFile }) }
            l = timed("qwen") { LlamaLlm(models.qwen) }
            speech = s; llm = l
            assistant = Assistant(tax, classifier::classify, llm, summarise = false)
            classifier.classify(listOf(listOf("warm up")))   // first HTP execution pays one-off setup
        } catch (error: Throwable) {
            s?.close(); l?.close(); r?.close(); audio.shutdownNow(); inference.shutdownNow(); throw error
        }
        loadMs = times
    }

    fun view(): AssistantView {
        val a = assistant; val st = a.state; val tax = a.tax
        val last = a.log.lastOrNull()
        return AssistantView(a.chat.toList(), a.lastAction?.text ?: OPENING, a.lastAction?.questionBy ?: "template",
            st.type, st.type?.let { tax.label(it) }, st.typeSource, st.typeConf, st.type?.let { tax.team(it) },
            LinkedHashMap(st.values), tax.requiredFields(st.type), tax.essential, st.missingEssentials(tax),
            st.ready, st.done, st.outcome, a.routing, a.summary,
            a.cls?.probs?.entries?.sortedByDescending { it.value }?.take(5)?.map { it.key to it.value } ?: emptyList(),
            a.cls?.sentences ?: emptyList(), a.note,
            last?.let { Timings(whisperMs.takeIf { it > 0 }, it.classifierMs, it.llm, it.turnMs) },
            a.lastAction?.kind, a.lastAction?.fields ?: emptyList(),
            (if (a.lastAction?.kind == "confirm") st.pendingConfirm else null)?.map { it to tax.label(it) } ?: emptyList(),
            (tax.requiredFields(st.type) + st.values.keys).distinct().associateWith { tax.fieldMeta(it) },
            speech.encoderOnNpu)
    }

    /** Form edit: parse [raw] by the field's kind; blank clears the field. */
    fun editField(field: String, raw: String) {
        submit(inference) {
            val meta = assistant.tax.fieldMeta(field)
            val text = raw.trim()
            val value: Any? = when {
                text.isEmpty() -> null
                meta.kind == "int" -> text.toIntOrNull() ?: throw IllegalArgumentException("${field.replace('_', ' ')} needs a number.")
                meta.kind == "bool" -> when (text.lowercase()) { "yes", "true" -> true; "no", "false" -> false; "unknown" -> "unknown"
                    else -> throw IllegalArgumentException("Answer yes or no.") }
                meta.kind == "enum" -> text.takeIf { it in meta.values || it == "unknown" } ?: throw IllegalArgumentException("Pick one of the listed options.")
                field == "building" -> text.uppercase().takeIf { it in assistant.tax.buildingCodes } ?: text
                else -> text
            }
            assistant.edit(field, value)
            onView(view())
        }
    }

    fun reset() { cancel(); submit(inference) { assistant.reset(); whisperMs = 0; onView(view()) } }

    override fun beginSession(sessionId: String, sampleRateHz: Int) {
        cancel()
        val stamp = epoch.get()
        submit(inference) { if (stamp == epoch.get()) english.clear() }
        submit(audio) {
            if (stamp == epoch.get()) {
                resampler = PcmResampler(sampleRateHz); endpoint.reset(); samples = 0; previewAt = 0; readySent = false; accepting = true
            }
        }
    }

    override fun accept(frame: AudioFrame) {
        val stamp = epoch.get()
        submit(audio) {
            if (!accepting || stamp != epoch.get()) return@submit
            val pcm = resampler.accept(frame.samples)
            samples += pcm.size
            val event = endpoint.accept(pcm)
            if (endpoint.calibrated && !readySent) { readySent = true; onListeningReady() }
            event.segment?.let { segment ->
                if (event.turnEnded) {
                    android.util.Log.i("IncidentEngine", "turn: floor %.1f dB, peak %.1f dB, %.1f s".format(
                        endpoint.lastTurnFloorDb, endpoint.lastTurnPeakDb, segment.size / 16000.0))
                    accepting = false; onProcessing()
                }
                transcribe(stamp, segment, true, event.turnEnded)
            }
            if (event.segment == null && samples - previewAt >= 32000 && inference.activeCount == 0 && inference.queue.isEmpty()) {
                previewAt = samples
                endpoint.preview()?.let { transcribe(stamp, it, false, false) }
            }
        }
    }

    private fun transcribe(stamp: Long, pcm: FloatArray, committed: Boolean, ended: Boolean) {
        submit(inference) {
            if (stamp != epoch.get()) return@submit
            val t0 = System.nanoTime()
            val partial = speech.transcribe(pcm, "en", committed)
            if (committed) whisperMs += (System.nanoTime() - t0) / 1_000_000
            if (stamp != epoch.get()) return@submit
            val en = (english.toString() + " " + partial.text).trim()
            if (committed) english.clear().append(en)
            onCaption(NON_SPEECH.replace(en, " ").trim())
            if (ended) {
                english.clear()
                val said = spoken(en)
                android.util.Log.i("IncidentEngine", "heard: \"$en\" -> ${if (said == null) "ignored" else "turn"}")
                // Music, noise or a fragment: not a turn. Keep listening instead of answering it.
                if (said == null) { onNoSpeech(); return@submit }
                step(said)
            }
        }
    }

    private fun step(text: String) {
        assistant.step(text)
        onView(view())
        whisperMs = 0
    }

    fun processText(text: String) {
        if (text.isBlank()) return
        cancel(); onProcessing()
        val stamp = epoch.get()
        submit(inference) { if (stamp == epoch.get()) { whisperMs = 0; step(text.trim()) } }
    }

    override fun endSession() {
        val stamp = epoch.get()
        submit(audio) {
            if (!accepting || stamp != epoch.get()) return@submit
            accepting = false
            val remainder = endpoint.flush()
            if (remainder != null) { onProcessing(); transcribe(stamp, remainder, true, true) }
            else onError("I couldn't hear that. Tap the mic and try again.")
        }
    }

    @Synchronized fun cancel() {
        if (closed) return
        accepting = false; epoch.incrementAndGet(); llm.cancel(); audio.queue.clear(); inference.queue.clear()
    }

    private fun submit(worker: ThreadPoolExecutor, action: () -> Unit) {
        if (closed) return
        val stamp = epoch.get()
        try { worker.execute {
            if (!closed && stamp == epoch.get()) try { action() } catch (error: Exception) {
                if (!closed && stamp == epoch.get()) { accepting = false; onError(error.message ?: "Local processing failed.") }
            }
        } } catch (_: java.util.concurrent.RejectedExecutionException) {
            if (!closed) { cancel(); onError("Local processing is busy. Please pause and try again.") }
        }
    }

    @Synchronized override fun close() {
        if (closed) return
        cancel(); closed = true; audio.shutdown(); inference.shutdown()
        Thread {
            audio.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
            inference.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
            speech.close(); llm.close(); classifier.close()
        }.apply { isDaemon = true; start() }
    }

    private fun worker(name: String, size: Int) = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(size), { action -> Thread(action, name) }, ThreadPoolExecutor.AbortPolicy())

    companion object {
        const val OPENING = "Tell me what happened and where you are."
        private val NON_SPEECH = Regex("""\[[^\]]*]|\([^)]*\)|\*[^*]*\*|[♪♫]""")

        // What Whisper typically "hears" in silence or noise.
        private val HALLUCINATIONS = setOf("you", "thank you", "thanks", "thanks for watching", "thank you for watching",
            "please subscribe", "subtitles by the amara org community", "bye bye")

        /**
         * Whisper text without non-speech tags ([Music], (applause), ♪). Null if nothing spoken is left or it is one of
         * Whisper's usual noise hallucinations. One-word answers ("yes", "QRC", "done") are kept.
         */
        fun spoken(text: String): String? {
            val clean = NON_SPEECH.replace(text, " ").replace(Regex("\\s+"), " ").trim()
            val words = Regex("[\\p{L}\\p{N}]+").findAll(clean.lowercase()).map { it.value }.toList()
            if (words.isEmpty() || words.joinToString(" ") in HALLUCINATIONS) return null
            return clean
        }
    }
}
