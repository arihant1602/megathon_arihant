package org.seedhecode.incidentassistant.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** What the assistant speaks with: the neural voice when its model is on the device, else the system TTS. */
interface Speech : AutoCloseable {
    val speaking: Boolean
    val name: String
    /** Returns false if nothing will be spoken (then no finish callback follows). */
    fun speak(text: String): Boolean
    fun stop()
}

class SystemSpeech(private val speaker: OfflineSpeaker) : Speech {
    override val speaking get() = speaker.speaking
    override val name = "System voice"
    override fun speak(text: String) = speaker.speak(text)
    override fun stop() { speaker.stop() }
    override fun close() = speaker.close()
}

/**
 * Neural voice through sherpa-onnx on the CPU: Piper (VITS) lessac-medium by default, or Kokoro if its files are in the
 * dir. Each sentence streams into an AudioTrack as soon as it is synthesized, so speech starts after the first sentence.
 * On the SM8750 kit (8 threads, 1.2x speed): Piper lessac-medium first audio 60-170 ms, 20x real time; Kokoro-82M
 * 0.9-1.6 s, under 2x real time, too slow for a conversation.
 * Model dir (scripts/fetch_sherpa_onnx.sh): <voice>.onnx, tokens.txt, espeak-ng-data/ (+ voices.bin for Kokoro).
 */
class NeuralSpeaker(dir: File, private val onFinished: () -> Unit, private val speed: Float = 1.2f) : Speech {
    private val kokoro = File(dir, "voices.bin").isFile
    private val voice = if (kokoro) VOICE_SARAH else 0
    private val tts = OfflineTts(config = OfflineTtsConfig(model = run {
        val onnx = dir.listFiles()!!.first { it.name.endsWith(".onnx") }.path
        val tokens = File(dir, "tokens.txt").path; val data = File(dir, "espeak-ng-data").path
        if (kokoro) OfflineTtsModelConfig(numThreads = 8,
            kokoro = OfflineTtsKokoroModelConfig(model = onnx, voices = File(dir, "voices.bin").path, tokens = tokens, dataDir = data))
        else OfflineTtsModelConfig(numThreads = 8, vits = OfflineTtsVitsModelConfig(model = onnx, tokens = tokens, dataDir = data))
    }))
    private val rate = tts.sampleRate()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "neural-tts") }
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()
    @Volatile private var track: AudioTrack? = null
    @Volatile override var speaking = false; private set
    override val name = if (kokoro) "Kokoro" else "Piper"
    /** Time from speak() to the first audio written, for the last utterance. */
    @Volatile var lastFirstAudioMs = 0L; private set

    override fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        stop()
        val id = generation.incrementAndGet()
        speaking = true
        val t0 = System.nanoTime()
        worker.execute {
            if (generation.get() != id) return@execute
            // 30 s of float audio: writes never block, so synthesis of sentence n+1 overlaps playback of sentence n.
            val out = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(rate * 4 * 30).setTransferMode(AudioTrack.MODE_STREAM).build()
            track = out
            var frames = 0
            try {
                out.play()
                tts.generateWithCallback(text, voice, speed) { samples ->
                    if (generation.get() != id) 0 else {
                        if (frames == 0) lastFirstAudioMs = (System.nanoTime() - t0) / 1_000_000
                        out.write(samples, 0, samples.size, AudioTrack.WRITE_NON_BLOCKING)
                        frames += samples.size
                        1
                    }
                }
                while (generation.get() == id && out.playbackHeadPosition < frames) Thread.sleep(15)
            } catch (_: Exception) {
                // Synthesis or playback failed: finish anyway so the conversation goes on (the question is on screen).
            }
            runCatching { out.stop() }; out.release()
            if (track === out) track = null
            if (generation.get() == id) { speaking = false; main.post { onFinished() } }
        }
        return true
    }

    override fun stop() {
        generation.incrementAndGet()
        track?.let { runCatching { it.pause(); it.flush() } }
        speaking = false
    }

    override fun close() { stop(); worker.execute { tts.release() }; worker.shutdown() }

    companion object {
        // kokoro-en-v0_19 speakers: 0 af, 1 af_bella, 2 af_nicole, 3 af_sarah, 4 af_sky, 5 am_adam, 6 am_michael,
        // 7 bf_emma, 8 bf_isabella, 9 bm_george, 10 bm_lewis.
        const val VOICE_SARAH = 3
    }
}
