package org.seedhecode.incidentassistant.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
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
 * dir. Synthesis runs sentence by sentence on one thread while a player thread writes the audio to an AudioTrack, so
 * speech starts after the first sentence. On the SM8750 kit (8 threads, 1.2x speed): Piper lessac-medium first audio
 * 60-170 ms, 20x real time; Kokoro-82M 0.9-1.6 s, too slow for a conversation.
 * Model dir (scripts/fetch_sherpa_onnx.sh): <voice>.onnx, tokens.txt, espeak-ng-data/ (+ voices.bin for Kokoro).
 */
class NeuralSpeaker(dir: File, private val onFinished: () -> Unit, private val speed: Float = 1.2f) : Speech {
    private val kokoro = File(dir, "voices.bin").isFile
    private val voice = if (kokoro) VOICE_SARAH else 0
    private val tts = OfflineTts(config = OfflineTtsConfig(model = run {
        val onnx = dir.listFiles()!!.first { it.name.endsWith(".onnx") }.path
        val tokens = File(dir, "tokens.txt").path
        val data = File(dir, "espeak-ng-data").path
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
    /** Time from speak() to the first synthesized audio, for the last utterance. */
    @Volatile var lastFirstAudioMs = 0L; private set

    override fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        stop()
        val id = generation.incrementAndGet()
        speaking = true
        val t0 = System.nanoTime()
        worker.execute {
            if (generation.get() != id) return@execute
            val chunks = LinkedBlockingQueue<ShortArray>()
            val player = Thread({ play(id, chunks) }, "neural-tts-play").apply { start() }
            var first = true
            try {
                tts.generateWithCallback(text, voice, speed) { samples ->
                    if (generation.get() != id) 0 else {
                        if (first) { lastFirstAudioMs = (System.nanoTime() - t0) / 1_000_000; first = false }
                        chunks.put(ShortArray(samples.size) { i -> (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort() })
                        1
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "synthesis failed", error)
            }
            chunks.put(END)
            player.join()
        }
        return true
    }

    /** Plays queued 16-bit chunks until END; calls back only if this is still the current utterance. */
    private fun play(id: Long, chunks: LinkedBlockingQueue<ShortArray>) {
        var written = 0
        var out: AudioTrack? = null
        try {
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            out = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min * 4, rate)).setTransferMode(AudioTrack.MODE_STREAM).build()
            track = out
            out.play()
            while (generation.get() == id) {
                val chunk = chunks.take()
                if (chunk === END) break
                var offset = 0
                while (offset < chunk.size && generation.get() == id) {
                    val n = out.write(chunk, offset, chunk.size - offset)   // blocking write
                    if (n <= 0) { Log.e(TAG, "AudioTrack.write returned $n"); break }
                    offset += n
                }
                written += offset
            }
            // Drain: wait until the speaker has played what was written; give up if playback stops advancing.
            var last = -1
            var stalledSince = System.nanoTime()
            while (generation.get() == id) {
                val head = out.playbackHeadPosition
                if (head >= written) break
                if (head != last) { last = head; stalledSince = System.nanoTime() }
                else if (System.nanoTime() - stalledSince > 1_000_000_000L) { Log.w(TAG, "playback stalled at $head/$written"); break }
                Thread.sleep(15)
            }
        } catch (error: Exception) {
            Log.e(TAG, "playback failed", error)
        } finally {
            out?.let { runCatching { it.stop() }; it.release() }
            if (track === out) track = null
            if (generation.get() == id) { speaking = false; main.post { onFinished() } }
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        track?.let { runCatching { it.pause(); it.flush() } }
        speaking = false
    }

    override fun close() { stop(); worker.execute { tts.release() }; worker.shutdown() }

    companion object {
        private const val TAG = "NeuralSpeaker"
        // kokoro-en-v0_19 speakers: 0 af, 1 af_bella, 2 af_nicole, 3 af_sarah, 4 af_sky, 5 am_adam, 6 am_michael,
        // 7 bf_emma, 8 bf_isabella, 9 bm_george, 10 bm_lewis.
        const val VOICE_SARAH = 3
        private val END = ShortArray(0)
    }
}
