package org.seedhecode.incidentassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import org.seedhecode.core.audio.ConversationCapture
import org.seedhecode.core.inference.TurnKey
import org.seedhecode.incidentassistant.engine.*
import java.util.concurrent.Executors

/** Phase 1-2 app entry point; receives model proposals rather than submitting reports. */
class ConversationActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var captions: TextView
    private lateinit var output: TextView
    private lateinit var modelStatus: TextView
    private lateinit var level: ProgressBar
    private lateinit var listen: Button
    private lateinit var process: Button
    private lateinit var importModels: Button
    private lateinit var loadModels: Button
    private lateinit var text: EditText
    private lateinit var question: EditText
    private lateinit var languages: Spinner
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var models: ModelStore
    private lateinit var repository: LocalRepository
    private var engine: AndroidConversationEngine? = null
    private var capture: ConversationCapture? = null
    private lateinit var speaker: OfflineSpeaker
    private var revision = 0L
    private val draftSessionId = java.util.UUID.randomUUID().toString()
    private var listening = false
    private var foreground = false
    private var busy = false
    private var echoCancellation = false
    private var exportPending: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        models = ModelStore(this)
        repository = LocalRepository(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(getColor(R.color.background))
        }
        setContentView(ScrollView(this).apply { addView(column) })
        fun label(value: String, size: Float = 16f): TextView = TextView(this).apply {
            text = value; textSize = size; setTextColor(getColor(R.color.text_primary))
            setPadding(0, dp(8), 0, dp(8)); column.addView(this)
        }
        fun button(value: String, action: () -> Unit): Button = Button(this).apply {
            text = value; isAllCaps = false; setOnClickListener { action() }; column.addView(this)
        }
        label("Incident conversation", 28f)
        label("Speak naturally and pause to finish a turn. Review extracted proposals here before the reporting workflow is merged.")
        status = label("Checking local model setup…")
        modelStatus = label("Models are imported from a local bundle. No automatic downloads.", 13f)
        importModels = button("Import local model bundle") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
            }, IMPORT_MODELS)
        }
        loadModels = button("Load local models") { load() }
        languages = Spinner(this).apply {
            adapter = ArrayAdapter(this@ConversationActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Auto: English / Hindi / Hinglish", "English", "Hindi / Hinglish"))
            column.addView(this)
        }
        level = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; column.addView(this) }
        listen = button("Start conversation") { if (listening) stopListening() else requestListening() }
        captions = label("Your transcript appears here.", 18f)
        label("English text input", 18f)
        text = EditText(this).apply {
            hint = "Describe the incident in English"; minLines = 2; maxLines = 5
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            column.addView(this)
        }
        process = button("Process text locally") {
            if (text.text.isBlank()) { status.text = "Enter an incident description first." }
            else {
                stopListening()
                engine?.updateDraft(revision, question.text.toString().takeIf { it.isNotBlank() })
                engine?.processText(text.text.toString())
                status.text = "Processing with local models…"
            }
        }
        label("Follow-up supplied by the reporting workflow", 18f)
        question = EditText(this).apply { hint = "Approved English question (optional)"; column.addView(this) }
        button("Read question aloud") {
            if (question.text.isBlank()) status.text = "Enter the approved question first."
            else if (listening && !echoCancellation) status.text = "Stop listening before playback; echo cancellation is unavailable."
            else {
                engine?.updateDraft(revision, question.text.toString())
                speaker.speak(question.text.toString())
            }
        }
        button("Apply correction / advance revision") {
            revision++
            engine?.updateDraft(revision, question.text.toString().takeIf { it.isNotBlank() })
            speaker.stop(); captions.text = "Revision $revision: earlier model results discarded."
            output.text = "Process the corrected answer to produce new proposals."
        }
        label("Latest model proposals", 20f)
        output = label("No model result yet.", 13f).apply { setTextIsSelectable(true) }
        button("Export local evidence and diagnostics") {
            background {
                exportPending = repository.export()
                ui { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "incident-engine-diagnostics.json")
                }, EXPORT_DIAGNOSTICS) }
            }
        }
        speaker = OfflineSpeaker(this, { message -> ui { status.text = message } }, { _, millis ->
            // This is TTS enqueue-to-onStart, not the full M1 speech-end response budget.
            if (!isDestroyed) repository.metric("tts_enqueue_to_start", millis)
        })
        controls()
        background {
            val pack = models.load()
            ui {
                modelStatus.text = if (pack == null) "Import the Whisper + Qwen bundle to process speech and text."
                    else "Bundle verified (${pack.mode.manifestName}): ${pack.versions().entries.joinToString { "${it.key}: ${it.value}" }}" +
                        if (pack.mode.manifestName == "asr_llm") "\nCfC filtering and Laya classification disabled; incident type remains unclassified." else ""
                status.text = "Local setup checked."
            }
        }
    }

    private fun load() {
        stopListening()
        engine?.close(); engine = null; capture?.close(); capture = null
        busy = true; controls(); status.text = "Verifying and loading local models…"
        background {
            val pack = models.load() ?: error("Import the trained model bundle first.")
            val loaded = AndroidConversationEngine(pack, repository,
                { key, transcript -> ui {
                    if (engine?.isCurrent(key) == true) captions.text = buildString {
                        append(if (transcript.isFinal) "Final: " else "Tentative: ")
                        append(transcript.text)
                        if (transcript.sourceText != transcript.text) append("\nOriginal (${transcript.language}): ${transcript.sourceText}")
                    }
                } },
                { result -> ui {
                    if (engine?.isCurrent(result.key) == true) {
                        status.text = "Turn ${result.key.turnId} processed locally. No report submitted."
                        output.text = result.toJson().toString(2)
                    }
                } },
                { message -> ui { status.text = message; stopListening() } },
                { ui {
                    if (!foreground) return@ui
                    if (speaker.speaking) {
                        val requestLatency = speaker.stop()
                        repository.metric("barge_in_stop_request", requestLatency)
                    }
                    status.text = "Listening…"
                } })
            runOnUiThread {
                if (isDestroyed || isFinishing) { loaded.close(); return@runOnUiThread }
                engine = loaded
                loaded.beginDraft(draftSessionId, revision, question.text.toString().takeIf { it.isNotBlank() })
                capture = ConversationCapture(loaded, object : ConversationCapture.Listener {
                    override fun onReady(sampleRate: Int, echoCancellation: Boolean) = ui {
                        this@ConversationActivity.echoCancellation = echoCancellation
                        status.text = "Listening at ${sampleRate / 1000} kHz. " +
                            if (echoCancellation) "Echo cancellation enabled." else "Echo cancellation unavailable; speech playback disabled while listening."
                    }
                    override fun onLevel(level: Float, capturedAtNanos: Long) = ui {
                        this@ConversationActivity.level.progress = (level * 100).toInt()
                        repository.metric("listening_feedback", (System.nanoTime() - capturedAtNanos) / 1_000_000.0)
                    }
                    override fun onStopped() = ui { listening = false; level.progress = 0; controls() }
                    override fun onError(message: String) = ui { status.text = message }
                }, draftSessionId)
                busy = false; controls()
                modelStatus.text = "Models ready (${pack.mode.manifestName}): CPU reference backend." +
                    if (pack.mode.manifestName == "asr_llm") " CfC and Laya disabled; incident type unclassified." else ""
                status.text = "Ready for a local conversation."
            }
        }
    }
    private fun requestListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE)
        } else startListening()
    }
    private fun startListening() {
        if (!foreground || engine == null || busy) return
        speaker.stop()
        engine?.language = listOf("auto", "en", "hi")[languages.selectedItemPosition]
        engine?.updateDraft(revision, question.text.toString().takeIf { it.isNotBlank() })
        listening = true; echoCancellation = false; controls()
        capture?.start()
    }
    private fun stopListening() { capture?.stop(); level.progress = 0; controls() }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == MICROPHONE) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
            else status.text = "Microphone permission denied. Text processing is still available."
        }
    }
    @Deprecated("Activity result API retained to keep the native Activity scaffold dependency-free")
    override fun onActivityResult(code: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(code, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        if (code == IMPORT_MODELS) {
            stopListening(); engine?.close(); engine = null; capture?.close(); capture = null
            busy = true; controls(); status.text = "Importing and checking model checksums…"
            background {
                val pack = models.import(uri)
                ui { busy = false; controls(); modelStatus.text = "Verified ${pack.versions().keys.joinToString()} bundle."
                    status.text = "Bundle imported. Tap Load local models." }
            }
        } else if (code == EXPORT_DIAGNOSTICS) {
            val content = exportPending ?: return
            background {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                    ?: error("Cannot write diagnostics file.")
                ui { status.text = "Diagnostics exported. Contains local transcript evidence." }
            }
        }
    }
    private fun controls() {
        if (!::listen.isInitialized) return
        listen.isEnabled = engine != null && !busy
        listen.text = if (listening) "Stop conversation" else "Start conversation"
        process.isEnabled = engine != null && !busy && !listening
        importModels.isEnabled = !busy && !listening
        loadModels.isEnabled = !busy && !listening
    }
    private fun background(action: () -> Unit) {
        io.execute { try { action() } catch (error: Exception) {
            ui { busy = false; controls(); status.text = error.message ?: "Local setup failed." }
        } }
    }
    private fun ui(action: () -> Unit) { runOnUiThread { if (!isDestroyed && !isFinishing) action() } }
    override fun onStart() { super.onStart(); foreground = true }
    override fun onStop() {
        foreground = false; stopListening(); engine?.cancel(); speaker.stop()
        super.onStop()
    }
    override fun onDestroy() {
        capture?.close(); engine?.close(); speaker.close()
        io.execute { repository.close() }; io.shutdown()
        super.onDestroy()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val IMPORT_MODELS = 201; private const val EXPORT_DIAGNOSTICS = 202; private const val MICROPHONE = 203 }
}
