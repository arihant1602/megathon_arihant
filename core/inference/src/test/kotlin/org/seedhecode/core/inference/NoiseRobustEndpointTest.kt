package org.seedhecode.core.inference

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic 16 kHz signals: white noise at a level, and "speech" = a voiced tone with syllable-rate amplitude gaps. */
class NoiseRobustEndpointTest {
    private val rnd = Random(7)
    private fun noise(ms: Int, amp: Float) = FloatArray(ms * 16) { (rnd.nextFloat() * 2 - 1) * amp }
    private fun speech(ms: Int, amp: Float, noiseAmp: Float) = FloatArray(ms * 16) { i ->
        val syllable = if ((i / 16 / 180) % 3 == 2) 0.15f else 1f          // short dips between syllables
        (sin(2 * PI * 180 * i / 16000.0).toFloat() * amp * syllable) + (rnd.nextFloat() * 2 - 1) * noiseAmp
    }

    /** Feeds audio in 20 ms frames like the capture thread; returns (started, ended, segment length ms, end time ms). */
    private fun run(e: NoiseRobustEndpoint, vararg parts: FloatArray): Result {
        var started = false; var ended = false; var segMs = 0; var endAt = -1; var t = 0
        for (p in parts) for (o in p.indices step 320) {
            val ev = e.accept(p.copyOfRange(o, minOf(p.size, o + 320)))
            t += 20
            if (ev.speechStarted) started = true
            if (ev.turnEnded && !ended) { ended = true; segMs = ev.segment!!.size / 16; endAt = t }
        }
        return Result(started, ended, segMs, endAt)
    }
    data class Result(val started: Boolean, val ended: Boolean, val segmentMs: Int, val endAtMs: Int)

    @Test fun steadyNoiseNeverStartsATurn() {
        val r = run(NoiseRobustEndpoint(), noise(8000, 0.03f))   // loud fan, nobody talking
        assertFalse(r.started); assertFalse(r.ended)
    }

    @Test fun speechInNoiseEndsAfterThePause() {
        val r = run(NoiseRobustEndpoint(), noise(1000, 0.02f), speech(2500, 0.25f, 0.02f), noise(2000, 0.02f))
        assertTrue(r.started); assertTrue(r.ended)
        // ends ~0.55 s into the trailing noise (1000 + 2500 + 550 ms), not held open by the noise
        assertTrue("ended at ${r.endAtMs}", r.endAtMs in 3950..4350)
        assertTrue(r.segmentMs >= 2500)
    }

    @Test fun clickDoesNotStartATurn() {
        val r = run(NoiseRobustEndpoint(), noise(1000, 0.005f), FloatArray(16 * 60) { 0.6f }, noise(2000, 0.005f))
        assertFalse(r.started)
    }

    @Test fun fanSwitchingOnIsLearnedNotTreatedAsSpeech() {
        val r = run(NoiseRobustEndpoint(), noise(1000, 0.003f), noise(6000, 0.03f), speech(2000, 0.3f, 0.03f), noise(2000, 0.03f))
        assertTrue(r.ended)
        assertTrue("ended at ${r.endAtMs}", r.endAtMs in 9350..9750)   // the speech turn, not the fan
    }

    @Test fun nonStopNoiseCannotHoldATurnOpenForever() {
        // talking over babble that never pauses: the turn is cut at the 15 s cap
        val r = run(NoiseRobustEndpoint(), noise(1000, 0.003f), speech(20000, 0.3f, 0.2f))
        assertTrue(r.ended)
        assertTrue("ended at ${r.endAtMs}", r.endAtMs <= 1000 + 15000 + 100)
    }

    /** Babble = speech-like noise from other people: modulated, so not "steady", and well above the floor. */
    private fun babble(ms: Int, amp: Float) = FloatArray(ms * 16) { i ->
        val env = 0.4f + 0.6f * kotlin.math.abs(sin(2 * PI * 3.3 * i / 16000.0)).toFloat()
        (rnd.nextFloat() * 2 - 1) * amp * env }

    @Test fun backgroundChatterDoesNotHoldTheTurnOpen() {
        // user at ~-15 dBFS, other people talking at ~-38 dBFS the whole time (kit: -60 dB floor, 15 s turns before)
        val r = run(NoiseRobustEndpoint(), babble(3000, 0.02f), speechOver(2500, 0.25f, 0.02f), babble(4000, 0.02f))
        assertTrue(r.ended)
        assertTrue("ended at ${r.endAtMs}", r.endAtMs < 3000 + 2500 + 1200)   // the user's pause, not the 15 s cap
    }
    private fun speechOver(ms: Int, amp: Float, babbleAmp: Float): FloatArray {
        val s = speech(ms, amp, 0f); val b = babble(ms, babbleAmp)
        return FloatArray(s.size) { s[it] + b[it] }
    }

    @Test fun distantTalkerIsIgnored() {
        // someone a few metres away: clearly speech, but peaks around -40 dBFS
        val r = run(NoiseRobustEndpoint(), noise(1000, 0.0003f), speech(2000, 0.012f, 0.0003f), noise(2000, 0.0003f))
        assertFalse(r.ended)
    }

    @Test fun quietRoomNormalSpeech() {
        val r = run(NoiseRobustEndpoint(), noise(500, 0.001f), speech(1500, 0.1f, 0.001f), noise(1500, 0.001f))
        assertTrue(r.started); assertTrue(r.ended)
    }
}
