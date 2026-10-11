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
import org.seedhecode.incidentassistant.data.FiledReport
import org.seedhecode.incidentassistant.data.ReportStatus
import org.seedhecode.incidentassistant.data.ReportStore
import org.seedhecode.incidentassistant.engine.AssistantView
import org.seedhecode.incidentassistant.engine.IncidentEngine
import org.seedhecode.incidentassistant.engine.NeuralSpeaker
import org.seedhecode.incidentassistant.engine.OfflineSpeaker
import org.seedhecode.incidentassistant.engine.Speech
import org.seedhecode.incidentassistant.engine.SystemSpeech
import org.seedhecode.incidentassistant.ui.app.display
import org.seedhecode.incidentassistant.ui.app.humanize
import org.seedhecode.incidentassistant.ui.app.respondBy
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
    val filedReportId: String? = null,     // set once the current conversation's report is saved
    val notice: String? = null,            // one-off message (e.g. a rejected form edit)
    val voice: String = "System voice",
    val readingReportId: String? = null,   // desk: report being read aloud
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
    private var conversing = false   // the assistant asked something and waits for a spoken answer: open the mic after speech
    private val store = ReportStore(application.filesDir)
    private val _reports = MutableStateFlow(store.all())
    val reports: StateFlow<List<FiledReport>> = _reports.asStateFlow()
    // System TTS until the neural voice (files/models/voice) has loaded.
    private var speaker: Speech = SystemSpeech(OfflineSpeaker(application, { msg -> ui { _state.update { it.copy(status = msg) } } },
        { _, _ -> }, { _ -> ui { afterSpeech() } }))

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
                    onListeningReady = { ui { if (capturing) _state.update { it.copy(status = "Listening… speak, then pause") } } },
                    onNoSpeech = { ui {   // background noise or music, not an answer: quietly listen again
                        _state.update { it.copy(phase = Phase.IDLE, caption = "", status = "Didn't catch that. Listening again…") }
                        if (conversing) main.postDelayed({ if (conversing && !capturing) startListening() }, 150)
                    } })
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
                val voiceDir = java.io.File(getApplication<Application>().filesDir, "models/voice")
                if (voiceDir.isDirectory) runCatching { NeuralSpeaker(voiceDir, { afterSpeech() }) }
                    .onSuccess { neural -> ui { val old = speaker; speaker = neural; old.close(); _state.update { it.copy(voice = neural.name) } } }
                    .onFailure { error -> ui { _state.update { it.copy(notice = "Natural voice unavailable: ${error.message}") } } }
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
        _state.update { it.copy(caption = "", filedReportId = null) }
        engine?.reset()
    }

    /** Form correction; the engine validates it against the field's type. */
    fun editField(field: String, value: String) { engine?.editField(field, value) }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    /** Desk: read the basic report aloud (tap again to stop). */
    fun toggleReadAloud(r: FiledReport) {
        if (_state.value.readingReportId == r.id) { speaker.stop(); _state.update { it.copy(readingReportId = null) }; return }
        conversing = false; capture?.stop()
        val details = r.fields.filter { it.first !in setOf("building", "floor", "space_id") }
            .joinToString(". ") { (f, v) -> "${humanize(f)}: ${display(v)}" }
        val text = listOf("${r.typeLabel}, ${r.priorityLabel.lowercase()} priority",
            r.where().ifEmpty { "location not given" }, "Sent to ${r.team}", "Respond ${respondBy(r.filedAt, r.slaMin).replace("min", "minutes")}",
            "Status: ${r.status.label}", details).filter { it.isNotBlank() }.joinToString(". ") + "."
        if (speaker.speak(text)) _state.update { it.copy(readingReportId = r.id) }
    }

    fun setStatus(id: String, status: ReportStatus) {
        store.setStatus(id, status); _reports.value = store.all()
    }

    private fun save(v: AssistantView): String {
        val r = v.routing!!
        val fields = (v.requiredFields + v.values.keys).distinct().mapNotNull { f ->
            v.values[f]?.value?.let { f to when (it) { true -> "yes"; false -> "no"; else -> it.toString() } }
        }
        val report = FiledReport("IR-" + java.lang.Long.toString(System.currentTimeMillis(), 36).uppercase(), System.currentTimeMillis(),
            v.type!!, v.typeLabel!!, r.team, r.priority, r.priorityLabel, r.slaMin, r.notify, r.secondary, fields, v.summary, v.chat,
            ReportStatus.NEW)
        store.save(report); _reports.value = store.all()
        return report.id
    }

    fun toggleSpeech() = _state.update { it.copy(speakQuestions = !it.speakQuestions) }

    private var answeredTurns = 0   // chat length already spoken; form edits refresh the view without a new question

    private fun onView(v: AssistantView) {
        val filed = _state.value.filedReportId ?: if (v.done && v.outcome == "submitted" && v.routing != null) save(v) else null
        val newTurn = v.chat.size > answeredTurns
        answeredTurns = v.chat.size
        _state.update { it.copy(view = v, error = null, filedReportId = filed,
            phase = if (newTurn || it.phase == Phase.THINKING) Phase.IDLE else it.phase,
            status = if (v.done) "Report closed" else if (newTurn) "Your turn" else it.status) }
        if (!newTurn) return
        conversing = !v.done   // every question expects an answer: the mic opens as soon as it has been spoken
        if (_state.value.speakQuestions && speaker.speak(v.question)) {
            _state.update { it.copy(phase = Phase.SPEAKING, status = "Speaking…") }
        } else afterSpeech()
    }

    private fun afterSpeech() {
        if (_state.value.readingReportId != null) _state.update { it.copy(readingReportId = null) }
        if (_state.value.phase == Phase.SPEAKING) _state.update { it.copy(phase = Phase.IDLE, status = "Your turn") }
        // 300 ms lets the speaker's tail die out so the mic doesn't hear the assistant's own last word.
        if (conversing && _state.value.view?.done != true) main.postDelayed({ if (conversing && !capturing) startListening() }, 300)
    }

    private fun onEngineError(message: String) {
        _state.update { it.copy(phase = Phase.IDLE, status = message, level = 0f, notice = message) }
    }

    /** Activity stopped: release the mic and silence speech. */
    fun pause() { conversing = false; capture?.stop(); speaker.stop() }

    override fun onCleared() {
        capture?.close(); engine?.close(); speaker.close(); loader.shutdown()
        super.onCleared()
    }
}
