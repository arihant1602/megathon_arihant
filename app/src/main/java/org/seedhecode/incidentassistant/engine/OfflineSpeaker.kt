package org.seedhecode.incidentassistant.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.UUID

/** Local device TTS reference adapter. It never selects a network-required voice. */
class OfflineSpeaker(context: Context, private val onStatus: (String) -> Unit,
    private val onAudioStart: (String, Double) -> Unit,
    private val onFinished: (Boolean) -> Unit = {}) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var initialized = false
    private var pending: Pair<String, Long>? = null
    private var watchdog: Runnable? = null
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change -> handleFocus(change) }
    @Volatile private var closed = false
    @Volatile var speaking = false; private set
    private val starts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var currentId: String? = null
    private val engine: TextToSpeech = TextToSpeech(context) { code ->
        handler.post {
            if (!closed) initialize(code)
        }
    }
    private fun initialize(code: Int) {
        initialized = true
        if (code != TextToSpeech.SUCCESS) { unavailable("Speech output unavailable. Install an offline English voice in speech settings."); return }
        val voice = engine.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language == "en" &&
            it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
            ?.maxByOrNull { it.quality }
        if (voice == null || engine.setVoice(voice) != TextToSpeech.SUCCESS) {
            unavailable("Install an offline English voice in Android speech settings."); return
        }
        ready = true
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {
                if (closed || currentId != id) return
                speaking = true
                starts.remove(id)?.let { start -> onAudioStart(id, (System.nanoTime() - start) / 1_000_000.0) }
            }
            override fun onDone(id: String) { finish(id, true) }
            @Deprecated("Legacy callback") override fun onError(id: String) {
                finish(id, false); handler.post { if (!closed) onStatus("Local speech output failed.") }
            }
            override fun onStop(id: String, interrupted: Boolean) { finish(id, false) }
        })
        onStatus("Voice ready. Questions will be read aloud.")
        pending?.let { request -> pending = null; speak(request.first, request.second) }
    }
    fun speak(text: String, responseStartNanos: Long = System.nanoTime()): Boolean {
        if (closed) return false
        if (!initialized) {
            stop(); pending = text to responseStartNanos
            onStatus("Preparing speech output...")
            watchdog = Runnable { if (pending != null && !closed) {
                pending = null; onStatus("Speech output is taking too long. Read the question on screen."); onFinished(false)
            } }.also { handler.postDelayed(it, 8000) }
            return true
        }
        if (!ready) { onStatus("No offline voice available. Read the question above or install a voice in Setup."); return false }
        stop()
        @Suppress("DEPRECATION")
        val focus = audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { onStatus("Audio is busy. Read the question above."); return false }
        val id = UUID.randomUUID().toString()
        currentId = id
        starts[id] = responseStartNanos
        speaking = true
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            starts.remove(id); currentId = null; speaking = false; releaseFocus()
            onStatus("Could not play the question. Read it above."); return false
        }
        watchdog = Runnable { if (!closed && currentId == id) {
            onStatus("Speech playback stalled. Read the question above; you can answer now.")
            engine.stop(); finish(id, false)
        } }.also { handler.postDelayed(it, maxOf(12000L, text.length * 100L + 8000L).coerceAtMost(45000L)) }
        return true
    }
    fun stop(): Double {
        val start = System.nanoTime()
        watchdog?.let { handler.removeCallbacks(it) }; watchdog = null; pending = null
        currentId = null
        engine.stop(); speaking = false; starts.clear(); releaseFocus()
        // Stop request latency only; physical acoustic stop must be measured on the kit.
        return (System.nanoTime() - start) / 1_000_000.0
    }
    private fun finish(id: String, success: Boolean) {
        starts.remove(id)
        handler.post {
            if (!closed && currentId == id) {
                watchdog?.let { handler.removeCallbacks(it) }; watchdog = null
                currentId = null; speaking = false; releaseFocus(); onFinished(success)
            }
        }
    }
    private fun unavailable(message: String) {
        val hadPending = pending != null
        pending = null; watchdog?.let { handler.removeCallbacks(it) }; watchdog = null
        onStatus(message)
        if (hadPending) onFinished(false)
    }
    private fun handleFocus(change: Int) {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            handler.post { currentId?.let { id -> engine.stop(); finish(id, false) } }
    }
    @Suppress("DEPRECATION") private fun releaseFocus() { audio.abandonAudioFocus(focusListener) }
    override fun close() { closed = true; stop(); engine.shutdown() }
}
