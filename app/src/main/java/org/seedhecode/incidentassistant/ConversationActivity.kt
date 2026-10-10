package org.seedhecode.incidentassistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import org.seedhecode.core.audio.ConversationCapture
import org.seedhecode.incidentassistant.engine.*
import java.util.concurrent.Executors
import kotlin.math.sin

/** One-screen, pause-driven interview. Nothing is dispatched or submitted automatically. */
class ConversationActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var question: TextView
    private lateinit var captions: TextView
    private lateinit var progress: TextView
    private lateinit var circle: VoiceCircle
    private lateinit var toggle: Button
    private lateinit var form: LinearLayout
    private lateinit var text: EditText
    private lateinit var send: Button
    private lateinit var turnAction: Button
    private lateinit var voiceStatus: TextView
    private lateinit var models: ModelStore
    private lateinit var speaker: OfflineSpeaker
    private val io = Executors.newSingleThreadExecutor()
    private val handoff = Handler(Looper.getMainLooper())
    private var engine: VoiceInterviewEngine? = null
    private var capture: ConversationCapture? = null
    private var draft: JSONObject? = null
    private var active = false
    private var foreground = false
    private var setupBusy = false
    private var processing = false
    private var captureRunning = false
    private var pendingListen = false
    private var pendingQuestion = false
    private var loadGeneration = 0L
    private var language = "auto"
    private var exportContent: String? = null
    private var awaitingMicrophonePermission = false
    private var listenWhenForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        models = ModelStore(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(20), dp(24), dp(28)); setBackgroundColor(Color.rgb(12, 18, 29))
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(column) })
        fun label(value: String, size: Float = 16f) = TextView(this).apply {
            text = value; textSize = size; gravity = Gravity.CENTER; setTextColor(Color.rgb(238, 244, 255))
            setPadding(0, dp(10), 0, dp(10)); column.addView(this)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; column.addView(this) }
        header.addView(TextView(this).apply {
            text = "Incident assistant"; textSize = 24f; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply { text = "Setup"; isAllCaps = false; setOnClickListener { setup() } })
        status = label("Checking local models", 14f)
        circle = VoiceCircle(this).apply { contentDescription = "Start or pause the voice interview"; setOnClickListener { toggleConversation() } }
        column.addView(circle, LinearLayout.LayoutParams(dp(230), dp(230)))
        question = label("Tell me what happened and where you are.", 23f)
        progress = label("Your answers build a department report. Speak, then pause.", 13f)
        captions = label("Your live transcript will appear here.", 16f)
        voiceStatus = label("Questions are read aloud when a local voice is available.", 13f)
        toggle = Button(this).apply { isAllCaps = false; setOnClickListener { toggleConversation() }; column.addView(this) }
        turnAction = Button(this).apply {
            isAllCaps = false; column.addView(this)
            setOnClickListener {
                if (!active || processing) return@setOnClickListener
                if (circle.state == "Listening") {
                    processing = true; visual("Thinking", "Finishing your answer..."); capture?.stop()
                } else {
                    pendingQuestion = false; speaker.stop(); scheduleListening()
                }
            }
        }
        text = EditText(this).apply {
            hint = "Or type an answer / correction"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            minLines = 1; maxLines = 4; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            column.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        send = Button(this).apply {
            text = "Send answer"; isAllCaps = false; column.addView(this)
            setOnClickListener {
                val answer = this@ConversationActivity.text.text.toString().trim()
                if (answer.isNotBlank()) {
                    pause(); processing = true; visual("Thinking", "Building your report")
                    engine?.processText(answer); this@ConversationActivity.text.setText(""); controls()
                }
            }
        }
        form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; column.addView(this, LinearLayout.LayoutParams(-1, -2)) }
        speaker = OfflineSpeaker(this, { message -> ui { voiceStatus.text = message } }, { _, _ -> }, { success -> ui {
            if (active && foreground && draft?.optString("stage") != "review") {
                if (!success) status.text = "Read the question above and answer when ready."
                scheduleListening()
            }
        } })
        controls(); load()
    }
    private fun setup() {
        if (setupBusy) return
        pause()
        AlertDialog.Builder(this).setTitle("Local voice setup")
            .setItems(arrayOf("Import model bundle", "Reload local models", "Speech language", "Start a new report", "Change incident type / department", "Spoken question settings")) { _, which ->
                when (which) {
                    0 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip" }, IMPORT)
                    1 -> load()
                    2 -> AlertDialog.Builder(this).setTitle("Speech language").setSingleChoiceItems(
                        arrayOf("Auto: English / Hindi / Hinglish", "English", "Hindi / Hinglish"), listOf("auto", "en", "hi").indexOf(language)) { dialog, index ->
                        language = listOf("auto", "en", "hi")[index]; dialog.dismiss()
                    }.show()
                    3 -> AlertDialog.Builder(this).setTitle("Start a new report?").setMessage("This replaces the saved draft. Export your current report first if you need it.")
                        .setPositiveButton("New report") { _, _ -> engine?.reset() }.setNegativeButton("Keep draft", null).show()
                    4 -> {
                        val types = JSONObject(assets.open("incident_taxonomy.json").bufferedReader().use { it.readText() }).getJSONArray("incident_types")
                        AlertDialog.Builder(this).setTitle("Choose incident type").setItems(
                            (0 until types.length()).map { types.getJSONObject(it).getString("label") }.toTypedArray()) { _, index ->
                            engine?.changeType(types.getJSONObject(index).getString("id"))
                        }.show()
                    }
                    5 -> {
                        try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                        catch (_: Exception) { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                    }
                }
            }.show()
    }
    private fun load() {
        pause(); val generation = ++loadGeneration
        capture?.close(); capture = null; engine?.close(); engine = null; captureRunning = false
        setupBusy = true; controls(); status.text = "Loading local Whisper and Qwen"
        io.execute {
            try {
                val pack = models.loadOrInstallBundled {
                    ui { if (generation == loadGeneration) status.text = "Preparing built-in models for first use..." }
                } ?: error("Open Setup and import a Whisper + Qwen model bundle first.")
                val loaded = VoiceInterviewEngine(this, pack,
                    { transcript -> ui { if (generation == loadGeneration) captions.text = transcript.text +
                        if (transcript.sourceText != transcript.text) "\n${transcript.sourceText}" else "" } },
                    { ui { if (generation == loadGeneration && (active || processing)) {
                        processing = true; pendingListen = false; pendingQuestion = false; handoff.removeCallbacksAndMessages(null)
                        capture?.stop(); visual("Thinking", "Understanding your answer"); controls()
                    } } },
                    { result -> ui { if (generation == loadGeneration) showDraft(result) } },
                    { message -> ui { if (generation == loadGeneration) { pause(); status.text = message; controls() } } },
                    { ui { if (generation == loadGeneration && active && foreground && !processing && circle.state == "Starting mic")
                        visual("Listening", "Your turn - speak now, then pause or tap I'm done") } })
                runOnUiThread {
                    if (isDestroyed || isFinishing || generation != loadGeneration) { loaded.close(); return@runOnUiThread }
                    engine = loaded
                    capture = ConversationCapture(loaded, object : ConversationCapture.Listener {
                        override fun onReady(sampleRate: Int, echoCancellation: Boolean) = ui {
                            if (generation == loadGeneration && active && !processing && circle.state == "Starting mic")
                                status.text = "Microphone connected - checking background sound..."
                        }
                        override fun onLevel(level: Float, capturedAtNanos: Long) = ui { if (generation == loadGeneration) circle.level = level }
                        override fun onStopped() = ui {
                            if (generation != loadGeneration) return@ui
                            captureRunning = false; circle.level = 0f
                            if (pendingQuestion && active && foreground && !processing) playQuestion()
                            else if (pendingListen && active && foreground && !processing) { pendingListen = false; startListening() }
                        }
                        override fun onError(message: String) = ui { if (generation == loadGeneration) { pause(); status.text = message } }
                    })
                    setupBusy = false; controls(); loaded.restore()
                }
            } catch (error: Exception) { ui { if (generation == loadGeneration) { setupBusy = false; status.text = error.message; controls() } } }
        }
    }
    private fun showDraft(result: JSONObject) {
        draft = result; processing = false; question.text = result.getString("question")
        val required = result.optJSONArray("required_fields"); val missing = result.optJSONArray("missing_fields")
        progress.text = if (required == null) "Tell me about the incident to choose its department."
            else "${result.optString("department_name")} · ${required.length() - (missing?.length() ?: required.length())}/${required.length()} answered" +
                if ((result.optJSONArray("unknown_fields")?.length() ?: 0) > 0) " · includes unknowns" else ""
        if (result.getString("stage") == "review") {
            active = false; pendingListen = false; capture?.stop(); speaker.stop()
            visual("Ready", "Report ready for review - not submitted"); renderForm(result)
        } else {
            form.visibility = View.GONE
            if (active && foreground) askQuestion() else visual("Paused", "Tap the circle to continue")
        }
        controls()
    }
    private fun askQuestion() {
        if (!active || !foreground || processing) return
        pendingListen = false; pendingQuestion = true; handoff.removeCallbacksAndMessages(null)
        if (captureRunning) { visual("Preparing", "Getting your next question ready"); capture?.stop() }
        else playQuestion()
    }
    private fun playQuestion() {
        if (!active || !foreground || processing) return
        pendingQuestion = false; visual("Speaking", "Listen to the question, then answer")
        if (!speaker.speak(question.text.toString())) scheduleListening()
    }
    private fun scheduleListening() {
        handoff.removeCallbacksAndMessages(null)
        if (!active || !foreground || processing) return
        visual("Your turn", "Your turn - getting the microphone ready")
        val generation = loadGeneration
        handoff.postDelayed({ if (generation == loadGeneration && active && foreground && !processing) requestListening() }, 250)
    }
    private fun toggleConversation() {
        if (setupBusy) return
        if (engine == null) { setup(); return }
        if (active || processing) { pause(); status.text = "Paused - tap to continue"; return }
        active = true
        if (draft?.optString("stage") == "review") { engine?.reopen(); return }
        askQuestion()
    }
    private fun requestListening() {
        if (!active || !foreground) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (awaitingMicrophonePermission) return
            awaitingMicrophonePermission = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE)
        } else startListening()
    }
    private fun startListening() {
        if (!active || !foreground || processing || speaker.speaking || engine == null) return
        if (captureRunning) { pendingListen = true; capture?.stop(); return }
        engine?.language = language; captureRunning = true
        visual("Starting mic", "Getting the microphone ready..."); capture?.start(); controls()
    }
    private fun pause() {
        active = false; pendingListen = false; pendingQuestion = false; processing = false
        handoff.removeCallbacksAndMessages(null)
        engine?.cancel(); capture?.stop(); if (::speaker.isInitialized) speaker.stop()
        if (::circle.isInitialized) visual("Paused", "Paused")
        controls()
    }
    private fun renderForm(result: JSONObject) {
        form.removeAllViews(); form.visibility = View.VISIBLE
        fun row(value: String, size: Float = 16f) { form.addView(TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE); setTextIsSelectable(true); setPadding(0, dp(8), 0, dp(8))
        }) }
        row("${result.optString("department_name")} report", 22f); row(result.optString("incident_label"))
        val fields = result.getJSONObject("fields"); val required = result.getJSONArray("required_fields")
        val ordered = (0 until required.length()).map { required.getString(it) } + fields.keys().asSequence().filter { name ->
            (0 until required.length()).none { required.getString(it) == name }
        }.toList()
        ordered.forEach { name ->
            val field = fields.optJSONObject(name)
            val value = if (field != null && field.optString("status") == "known") when (val raw = field.opt("value")) {
                true -> "Yes"; false -> "No"; else -> raw.toString()
            } else "Unknown / not provided"
            row("${name.replace('_', ' ').replaceFirstChar { it.uppercase() }}\n$value")
        }
        val extra = result.getJSONArray("extra_details")
        if (extra.length() > 0) row("Additional-details answers\n${(0 until extra.length()).joinToString("\n") { extra.getJSONObject(it).getString("text") }}")
        if (result.optBoolean("safety_unknown")) row("Safety information is unknown. This report does not establish that the situation is safe.")
        row("Saved on this device. Review all details; nothing has been submitted.", 13f)
        form.addView(Button(this).apply { text = "Correct details"; isAllCaps = false; setOnClickListener { active = true; engine?.reopen() } })
        form.addView(Button(this).apply { text = "Export report JSON"; isAllCaps = false; setOnClickListener {
            exportContent = result.toString(2)
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "incident-report.json")
            }, EXPORT)
        } })
    }
    private fun visual(state: String, message: String) { circle.state = state; status.text = message; controls() }
    private fun controls() {
        if (!::toggle.isInitialized) return
        toggle.isEnabled = !setupBusy; circle.isEnabled = toggle.isEnabled
        toggle.text = if (setupBusy) "Loading models..." else if (engine == null) "Set up voice models"
            else if (active || processing) "Pause conversation" else if (draft?.optString("stage") == "review") "Correct report" else "Start / resume conversation"
        send.isEnabled = engine != null && !setupBusy && !processing
        if (::turnAction.isInitialized) {
            turnAction.isEnabled = active && !processing && !setupBusy && engine != null && circle.state != "Starting mic"
            turnAction.text = if (circle.state == "Listening") "I'm done answering" else "Answer now / skip playback"
        }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != MICROPHONE) return
        val requested = awaitingMicrophonePermission
        awaitingMicrophonePermission = false
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED && requested && engine != null) {
            active = true
            if (foreground) requestListening() else listenWhenForeground = true
        } else {
            pause()
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                AlertDialog.Builder(this).setTitle("Microphone access needed")
                    .setMessage("Enable microphone permission in this app's settings to use the voice conversation. You can also type answers.")
                    .setPositiveButton("Open app settings") { _, _ -> startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:$packageName"))) }.setNegativeButton("Use text", null).show()
            }
            status.text = "Microphone permission denied. You can type answers instead."
        }
    }
    @Deprecated("Native Activity result API")
    override fun onActivityResult(code: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(code, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (code == IMPORT) {
            pause(); ++loadGeneration; capture?.close(); capture = null; engine?.close(); engine = null; captureRunning = false
            setupBusy = true; controls(); status.text = "Verifying model bundle"
            io.execute { try { models.import(uri); ui { setupBusy = false; load() } }
                catch (error: Exception) { ui { setupBusy = false; status.text = error.message; controls() } } }
        } else if (code == EXPORT) {
            val content = exportContent ?: return
            io.execute { try {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) } ?: error("Cannot export report.")
                ui { status.text = "Report exported. Nothing submitted." }
            } catch (error: Exception) { ui { status.text = error.message } } }
        }
    }
    private fun ui(action: () -> Unit) { runOnUiThread { if (!isDestroyed && !isFinishing) action() } }
    override fun onStart() {
        super.onStart(); foreground = true
        if (listenWhenForeground) { listenWhenForeground = false; active = true; requestListening() }
    }
    override fun onStop() { foreground = false; pause(); super.onStop() }
    override fun onDestroy() { ++loadGeneration; handoff.removeCallbacksAndMessages(null); capture?.close(); engine?.close(); speaker.close(); io.shutdown(); super.onDestroy() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private const val IMPORT = 201; private const val EXPORT = 202; private const val MICROPHONE = 203 }
}

private class VoiceCircle(context: android.content.Context) : View(context) {
    var state = "Paused"; set(value) { field = value; invalidate() }
    var level = 0f; set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = true; isFocusable = true }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val moving = state in setOf("Listening", "Thinking", "Speaking")
        val wave = if (moving) ((sin(SystemClock.uptimeMillis() / 420.0) + 1) / 2).toFloat() else 0.25f
        val radius = width.coerceAtMost(height) * (0.31f + level * 0.045f + wave * 0.015f)
        paint.style = Paint.Style.FILL; paint.color = Color.argb(35, 122, 181, 255)
        canvas.drawCircle(width / 2f, height / 2f, radius + 23f + wave * 8f, paint)
        paint.color = if (state == "Ready") Color.rgb(91, 207, 176) else Color.rgb(133, 185, 255)
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        paint.color = Color.rgb(17, 38, 67); paint.textSize = 16 * resources.displayMetrics.scaledDensity; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(state, width / 2f, height / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        if (moving && isAttachedToWindow && visibility == VISIBLE) postInvalidateDelayed(33)
    }
}
