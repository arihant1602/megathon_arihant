package org.seedhecode.incidentassistant.feature.assistant

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.seedhecode.core.audio.ConversationCapture
import org.seedhecode.incidentassistant.engine.AssistantView
import org.seedhecode.incidentassistant.engine.IncidentEngine
import org.seedhecode.incidentassistant.engine.OfflineSpeaker
import java.util.concurrent.Executors

enum class Phase { LOADING, IDLE, LISTENING, THINKING, SPEAKING, ERROR }

data class AssistantUiState(
    val phase: Phase = Phase.LOADING,
    val status: String = "Loading on-device models…",
    val caption: String = "",
    val level: Float = 0f,
    val view: AssistantView? = null,
    val loadMs: Map<String, Long> = emptyMap(),
    val error: String? = null,
    val speakQuestions: Boolean = true,
)

/** Drives one incident conversation: mic -> IncidentEngine -> spoken question -> listen again. */
class IncidentAssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private var engine: IncidentEngine? = null
    private var capture: ConversationCapture? = null
    private var capturing = false
    private var conversing = false   // started by voice: listen again after each spoken question
    private val speaker = OfflineSpeaker(application, { msg -> ui { _state.update { it.copy(status = msg) } } },
        { _, _ -> }, { _ -> ui { afterSpeech() } })

    init { load() }

    private fun ui(block: () -> Unit) { main.post(block) }

    fun load() {
        _state.update { it.copy(phase = Phase.LOADING, status = "Loading on-device models…", error = null) }
        loader.execute {
            try {
                val e = IncidentEngine(getApplication(),
                    onCaption = { text -> ui { _state.update { it.copy(caption = text) } } },
                    onProcessing = { ui { capture?.stop(); _state.update { it.copy(phase = Phase.THINKING, status = "Thinking…", level = 0f) } } },
                    onView = { v -> ui { onView(v) } },
                    onError = { msg -> ui { onEngineError(msg) } },
                    onListeningReady = { ui { if (capturing) _state.update { it.copy(status = "Listening… speak, then pause") } } })
                ui {
                    engine = e
                    capture = ConversationCapture(e, object : ConversationCapture.Listener {
                        override fun onReady(sampleRate: Int, echoCancellation: Boolean) =
                            ui { _state.update { it.copy(phase = Phase.LISTENING, status = "Listening… speak, then pause") } }
                        override fun onLevel(level: Float, capturedAtNanos: Long) = ui { _state.update { it.copy(level = level) } }
                        override fun onStopped() = ui {
                            capturing = false
                            _state.update { if (it.phase == Phase.LISTENING) it.copy(phase = Phase.IDLE, status = "Paused", level = 0f) else it.copy(level = 0f) }
                        }
                        override fun onError(message: String) = ui { capturing = false; onEngineError(message) }
                    })
                    _state.update { it.copy(phase = Phase.IDLE, status = "Ready. Tap the mic or type.", view = e.view(), loadMs = e.loadMs) }
                }
            } catch (error: Throwable) {
                ui { _state.update { it.copy(phase = Phase.ERROR, status = "Models not ready", error = error.message ?: error.toString()) } }
            }
        }
    }

    /** Call only once RECORD_AUDIO is granted. */
    fun startListening() {
        val c = capture ?: return
        if (capturing || _state.value.view?.done == true) return
        speaker.stop()
        conversing = true; capturing = true
        _state.update { it.copy(phase = Phase.LISTENING, status = "Starting microphone…", caption = "") }
        c.start()
    }

    fun stopListening() {
        conversing = false
        capture?.stop()
    }

    fun send(text: String) {
        val e = engine ?: return
        if (text.isBlank() || _state.value.view?.done == true) return
        conversing = false; speaker.stop(); capture?.stop()
        _state.update { it.copy(caption = text.trim()) }
        e.processText(text)
    }

    fun done() = send("done")

    fun newReport() {
        conversing = false; speaker.stop(); capture?.stop()
        _state.update { it.copy(caption = "") }
        engine?.reset()
    }

    fun toggleSpeech() = _state.update { it.copy(speakQuestions = !it.speakQuestions) }

    private fun onView(v: AssistantView) {
        _state.update { it.copy(view = v, phase = Phase.IDLE, status = if (v.done) "Report closed" else "Your turn", error = null) }
        if (v.chat.isEmpty()) return
        if (_state.value.speakQuestions && speaker.speak(v.question)) {
            _state.update { it.copy(phase = Phase.SPEAKING, status = "Speaking…") }
        } else afterSpeech()
    }

    private fun afterSpeech() {
        if (_state.value.phase == Phase.SPEAKING) _state.update { it.copy(phase = Phase.IDLE, status = "Your turn") }
        if (conversing && _state.value.view?.done != true) main.postDelayed({ if (conversing && !capturing) startListening() }, 250)
    }

    private fun onEngineError(message: String) {
        _state.update { it.copy(phase = Phase.IDLE, status = message, level = 0f) }
    }

    /** Activity stopped: release the mic and silence speech. */
    fun pause() { conversing = false; capture?.stop(); speaker.stop() }

    override fun onCleared() {
        capture?.close(); engine?.close(); speaker.close(); loader.shutdown()
        super.onCleared()
    }
}
