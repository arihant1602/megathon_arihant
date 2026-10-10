package org.seedhecode.incidentassistant.feature.report

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.seedhecode.core.audio.MicrophoneRecorder
import org.seedhecode.core.audio.RecordingStore
import org.seedhecode.core.audio.VoiceRecording
import org.seedhecode.core.inference.CaptureOnlyVoiceEngine
import java.io.File

data class ReportUiState(
    val captureState: CaptureState = CaptureState.IDLE,
    val durationMs: Long = 0L,
    val audioLevel: Float = 0f,
    val statusText: String = "Ready to record incident",
    val recordings: List<VoiceRecording> = emptyList(),
    val playingFile: File? = null,
    val errorMessage: String? = null
)

class ReportViewModel(application: Application) : AndroidViewModel(application), MicrophoneRecorder.Listener {
    private val store: RecordingStore = RecordingStore(File(application.filesDir, "recordings"))
    private val recorder: MicrophoneRecorder = MicrophoneRecorder(store, CaptureOnlyVoiceEngine()).also { it.listener = this }

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private var player: MediaPlayer? = null

    init {
        refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val list = runCatching { store.list() }.getOrDefault(emptyList())
            _uiState.update { it.copy(recordings = list) }
        }
    }

    fun startCapture() {
        stopPlayback()
        _uiState.update {
            it.copy(
                captureState = CaptureState.STARTING,
                statusText = "Starting microphone...",
                durationMs = 0L,
                audioLevel = 0f,
                errorMessage = null
            )
        }
        runCatching {
            recorder.start()
        }.onFailure { ex ->
            _uiState.update {
                it.copy(
                    captureState = CaptureState.IDLE,
                    statusText = ex.message ?: "Failed to start recording",
                    errorMessage = ex.message
                )
            }
        }
    }

    fun stopCapture() {
        _uiState.update {
            it.copy(
                captureState = CaptureState.SAVING,
                statusText = "Saving recording...",
                audioLevel = 0f
            )
        }
        recorder.stop()
    }

    fun play(recording: VoiceRecording) {
        stopPlayback()
        val next = MediaPlayer()
        player = next
        _uiState.update { it.copy(playingFile = recording.file) }
        try {
            next.setDataSource(recording.file.absolutePath)
            next.setOnPreparedListener { it.start() }
            next.setOnCompletionListener {
                stopPlayback()
                refreshLibrary()
            }
            next.setOnErrorListener { _, _, _ ->
                stopPlayback()
                _uiState.update { it.copy(errorMessage = "Playback failed") }
                true
            }
            next.prepareAsync()
        } catch (_: Exception) {
            stopPlayback()
            _uiState.update { it.copy(errorMessage = "Playback failed") }
        }
    }

    fun stopPlayback() {
        player?.release()
        player = null
        _uiState.update { it.copy(playingFile = null) }
    }

    fun delete(recording: VoiceRecording) {
        if (_uiState.value.playingFile == recording.file) {
            stopPlayback()
        }
        val success = store.delete(recording)
        if (!success) {
            _uiState.update { it.copy(errorMessage = "Failed to delete recording") }
        }
        refreshLibrary()
    }

    override fun onStarted(sampleRateHz: Int) {
        if (_uiState.value.captureState == CaptureState.SAVING) {
            recorder.stop()
            return
        }
        _uiState.update {
            it.copy(
                captureState = CaptureState.RECORDING,
                statusText = "Listening..."
            )
        }
    }

    override fun onProgress(durationMs: Long, level: Float) {
        if (_uiState.value.captureState == CaptureState.RECORDING) {
            _uiState.update {
                it.copy(
                    durationMs = durationMs,
                    audioLevel = level
                )
            }
        }
    }

    override fun onSaved(recording: VoiceRecording) {
        _uiState.update {
            it.copy(
                captureState = CaptureState.IDLE,
                statusText = "Recording saved",
                durationMs = recording.durationMs,
                audioLevel = 0f
            )
        }
        refreshLibrary()
    }

    override fun onError(message: String) {
        _uiState.update {
            it.copy(
                captureState = CaptureState.IDLE,
                statusText = message,
                audioLevel = 0f,
                errorMessage = message
            )
        }
        refreshLibrary()
    }

    fun forceStopCapture() {
        if (_uiState.value.captureState != CaptureState.IDLE) {
            stopCapture()
        }
    }

    override fun onCleared() {
        stopPlayback()
        runCatching { recorder.close() }
        super.onCleared()
    }
}
