package org.seedhecode.core.inference

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pause endpointing that holds up in background noise (fans, AC, chatter), for 16 kHz mono float PCM.
 *
 * - The noise floor is a low percentile of the last ~3 s of 20 ms frame levels, updated all the time (also while the
 *   user talks), so it follows a fan switching on instead of being fixed at start-up.
 * - Speech starts only when the level is [startDb] above the floor (and above an absolute minimum) for [minStartMs];
 *   once talking, a frame counts as speech while it stays [continueDb] above the floor. Anything quieter is pause.
 * - The turn ends after [pauseMs] of pause, or after [maxTurnMs] in any case, so noise can never hold a turn open.
 * - Speech rises and falls with syllables; a fan or AC switching on is flat. A "turn" whose level stays within a few dB
 *   for a whole second is dropped as noise, and that level becomes the new floor.
 *
 * - A turn only counts if its loudest frame reaches [minPeakDb]: the user close to the device is louder than people
 *   talking a few metres away (SM8750 kit in a busy hall: floor ~-70 dBFS, nearby chatter peaks -41..-28 dBFS).
 *   Quieter turns are dropped and listening simply continues.
 *
 * Same event contract as [PauseEndpoint]: a segment with turnEnded = true carries the whole utterance.
 */
class NoiseRobustEndpoint(
    private val pauseMs: Int = 900,
    private val minSpeechMs: Int = 300,
    private val minStartMs: Int = 200,
    private val maxTurnMs: Int = 15_000,
    private val startDb: Double = 15.0,
    private val continueDb: Double = 6.0,
    private val minLevelDb: Double = -45.0,
    private val minPeakDb: Double = -35.0,
    private val historyMs: Int = 3_000,
    private val calibrationMs: Int = 300,
) {
    private val frame = 320                       // 20 ms at 16 kHz
    private val pending = ArrayList<Float>()      // samples not yet forming a whole frame
    private val levels = ArrayDeque<Double>()     // recent frame levels (dB) for the noise floor
    private val preroll = ArrayDeque<Float>()     // audio just before speech start (kept with the turn)
    private val candidate = ArrayList<Float>()    // frames above the start threshold, not yet confirmed as speech
    private val audio = ArrayList<Float>()
    private var speaking = false
    private var voicedMs = 0
    private var silenceMs = 0
    private var startRunMs = 0
    private var framesSeen = 0
    private var floorAtStart = -60.0
    private val turnLevels = ArrayDeque<Double>()  // last second of frame levels in the current turn
    val calibrated: Boolean get() = framesSeen * 20 >= calibrationMs
    /** Current noise floor in dBFS (for diagnostics). */
    var noiseDb = -60.0; private set
    /** Loudest 20 ms frame (dBFS) and floor at start of the last finished turn, for tuning the gate on a device. */
    var lastTurnPeakDb = -100.0; private set
    var lastTurnFloorDb = -100.0; private set
    private var peakDb = -100.0

    fun accept(pcm: FloatArray): EndpointEvent {
        var started = false
        var segment: FloatArray? = null
        var ended = false
        pending.addAll(pcm.asList())
        while (pending.size >= frame && segment == null) {
            val f = FloatArray(frame) { pending[it] }
            pending.subList(0, frame).clear()
            val e = step(f)
            started = started || e.speechStarted
            if (e.segment != null) { segment = e.segment; ended = e.turnEnded }
        }
        return EndpointEvent(started, segment, ended)
    }

    private fun step(f: FloatArray): EndpointEvent {
        val rms = sqrt(f.fold(0.0) { s, v -> s + v * v } / f.size)
        val db = 20 * log10(max(rms, 1e-6))
        framesSeen++
        levels.addLast(db)
        while (levels.size > historyMs / 20) levels.removeFirst()
        // While talking the estimate may rise at most 6 dB, so a long sentence can't lift it into the speech itself.
        noiseDb = if (speaking) minOf(percentile(levels, 0.10), floorAtStart + 6.0) else percentile(levels, 0.20)
        if (!calibrated) { remember(f); return EndpointEvent(false, null, false) }

        if (!speaking) {
            val loud = db >= noiseDb + startDb && db >= minLevelDb
            if (loud) {
                candidate.addAll(f.asList()); startRunMs += 20
                if (startRunMs >= minStartMs) {          // sustained, not a click: speech started
                    speaking = true
                    floorAtStart = noiseDb
                    audio.addAll(preroll); preroll.clear()
                    audio.addAll(candidate); candidate.clear()
                    voicedMs = startRunMs; silenceMs = 0; startRunMs = 0
                    return EndpointEvent(true, null, false)
                }
            } else {
                candidate.forEach { remember(it) }; candidate.clear(); startRunMs = 0
                remember(f)
            }
            return EndpointEvent(false, null, false)
        }

        audio.addAll(f.asList())
        turnLevels.addLast(db); if (turnLevels.size > 50) turnLevels.removeFirst()
        peakDb = maxOf(peakDb, db)
        val voiced = db >= noiseDb + continueDb && db >= minLevelDb
        if (voiced) { voicedMs += 20; silenceMs = 0 } else silenceMs += 20
        val turnMs = audio.size / 16
        if (silenceMs >= pauseMs || turnMs >= maxTurnMs) {
            lastTurnPeakDb = peakDb; lastTurnFloorDb = floorAtStart
            val valid = voicedMs >= minSpeechMs && peakDb >= minPeakDb
            val chunk = if (valid) audio.toFloatArray() else null
            endTurn()
            return EndpointEvent(false, chunk, valid)
        }
        // Loud but flat for 1 s: a fan or AC came on, not a voice. Drop the turn and learn the new floor.
        if (voiced && turnLevels.size == 50 && spread(turnLevels) < STEADY_DB) {
            levels.clear(); turnLevels.forEach { levels.addLast(it) }
            noiseDb = percentile(levels, 0.20)
            endTurn()
        }
        return EndpointEvent(false, null, false)
    }

    private fun remember(f: FloatArray) { f.forEach { remember(it) } }
    private fun remember(v: Float) { preroll.addLast(v); if (preroll.size > 4800) preroll.removeFirst() }   // 300 ms

    private fun endTurn() {
        audio.clear(); candidate.clear(); preroll.clear(); turnLevels.clear()
        speaking = false; voicedMs = 0; silenceMs = 0; startRunMs = 0; peakDb = -100.0
    }

    fun preview(): FloatArray? = if (speaking && audio.size >= 16_000) audio.toFloatArray() else null
    fun flush(): FloatArray? = if (speaking && voicedMs >= minSpeechMs && audio.isNotEmpty()) audio.toFloatArray().also { endTurn() } else null

    /** New listening session: forget the turn but keep the noise estimate (the room didn't change). */
    fun reset() { endTurn(); pending.clear() }

    /** Spread of levels: 90th minus 10th percentile, in dB (syllabic speech is typically > 10 dB, fans < 3 dB). */
    private fun spread(values: Collection<Double>) = percentile(values, 0.9) - percentile(values, 0.1)

    private companion object { const val STEADY_DB = 4.0 }

    private fun percentile(values: Collection<Double>, p: Double): Double {
        if (values.isEmpty()) return -60.0
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * p).toInt()]
    }
}
