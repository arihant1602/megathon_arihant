package org.seedhecode.incidentassistant.engine

import android.content.Context
import org.seedhecode.core.inference.*
import org.json.JSONObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Pause-ended turns feed one local LLM interview; tentative Whisper captions never commit facts. */
class VoiceInterviewEngine(context: Context, pack: ModelPack,
    private val onCaption: (Transcript) -> Unit,
    private val onProcessing: () -> Unit,
    private val onDraft: (JSONObject) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningReady: () -> Unit = {},
) : VoiceEngine {
    private val audio = worker("interview-audio", 64)
    private val inference = worker("interview-inference", 4)
    private val epoch = AtomicLong()
    private val speech: WhisperSpeech
    private val interview: VoiceInterview
    private var resampler = PcmResampler(16000)
    private val endpoint = PauseEndpoint(pauseMs = 1600, minSpeechMs = 120, adaptive = true, calibrationMs = 250)
    private var readySent = false
    private var samples = 0L
    private var previewAt = 0L
    private val english = StringBuilder()
    private val source = StringBuilder()
    @Volatile private var closed = false
    @Volatile private var accepting = false
    @Volatile var language = "auto"
    init {
        var loadedSpeech: WhisperSpeech? = null
        try {
            loadedSpeech = WhisperSpeech(pack)
            interview = VoiceInterview(context, pack)
            speech = loadedSpeech
        } catch (error: Exception) { loadedSpeech?.close(); audio.shutdownNow(); inference.shutdownNow(); throw error }
    }
    fun restore() = submit(inference) { onDraft(interview.snapshot()) }
    fun reset() { cancel(); submit(inference) { interview.reset(); onDraft(interview.snapshot()) } }
    fun reopen() { cancel(); submit(inference) { interview.reopen(); onDraft(interview.snapshot()) } }
    fun changeType(id: String) { cancel(); submit(inference) { interview.changeType(id); onDraft(interview.snapshot()) } }
    override fun beginSession(sessionId: String, sampleRateHz: Int) {
        cancel()
        val stamp = epoch.get()
        submit(inference) { if (stamp == epoch.get()) { english.clear(); source.clear() } }
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
                if (event.turnEnded) { accepting = false; onProcessing() }
                transcribe(stamp, segment, true, event.turnEnded)
            }
            if (event.segment == null && samples - previewAt >= 32000 && inference.activeCount == 0 && inference.queue.isEmpty()) {
                previewAt = samples
                endpoint.preview()?.let { transcribe(stamp, it, false, false) }
            }
        }
    }
    private fun transcribe(stamp: Long, pcm: FloatArray, committed: Boolean, ended: Boolean) {
        val requestedLanguage = language
        submit(inference) {
            if (stamp != epoch.get()) return@submit
            val partial = speech.transcribe(pcm, requestedLanguage, committed)
            if (stamp != epoch.get()) return@submit
            val en = (english.toString() + " " + partial.text).trim()
            val original = (source.toString() + " " + partial.sourceText).trim()
            if (committed) { english.clear().append(en); source.clear().append(original) }
            onCaption(Transcript(en, original, partial.language, ended))
            if (ended) {
                if (en.isBlank()) { onError("I couldn't hear an answer. Tap the circle and try again."); return@submit }
                val draft = interview.answer(en, original, partial.language) { !closed && stamp == epoch.get() }
                english.clear(); source.clear()
                if (stamp == epoch.get()) onDraft(draft)
            }
        }
    }
    fun processText(text: String) {
        cancel(); val stamp = epoch.get(); onProcessing()
        submit(inference) {
            if (stamp != epoch.get()) return@submit
            val draft = interview.answer(text.trim(), text.trim(), "en") { !closed && stamp == epoch.get() }
            if (stamp == epoch.get()) onDraft(draft)
        }
    }
    override fun endSession() {
        val stamp = epoch.get()
        submit(audio) {
            if (!accepting || stamp != epoch.get()) return@submit
            accepting = false
            val remainder = endpoint.flush()
            if (remainder != null) { onProcessing(); transcribe(stamp, remainder, true, true) }
            else onError("I couldn't hear an answer. Tap Start and try again.")
        }
    }
    @Synchronized fun cancel() {
        if (closed) return
        accepting = false; epoch.incrementAndGet(); interview.cancel(); audio.queue.clear(); inference.queue.clear()
    }
    private fun submit(worker: ThreadPoolExecutor, action: () -> Unit) {
        if (closed) return
        val stamp = epoch.get()
        try { worker.execute {
            if (!closed && stamp == epoch.get()) try { action() } catch (error: Exception) {
                if (!closed && stamp == epoch.get()) { accepting = false; onError(error.message ?: "Local voice processing failed.") }
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
            speech.close(); interview.close()
        }.apply { isDaemon = true; start() }
    }
    private fun worker(name: String, size: Int) = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(size), { action -> Thread(action, name) }, ThreadPoolExecutor.AbortPolicy())
}
