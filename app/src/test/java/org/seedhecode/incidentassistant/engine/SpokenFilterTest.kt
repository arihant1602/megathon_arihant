package org.seedhecode.incidentassistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenFilterTest {
    @Test fun nonSpeechIsDropped() {
        listOf("[Music]", " [inaudible] ", "(applause)", "♪ ♪", "Thank you.", "you", "[BLANK_AUDIO]", "*coughs*", "").forEach {
            assertNull(it, IncidentEngine.spoken(it))
        }
    }
    @Test fun answersAreKept() {
        assertEquals("yes", IncidentEngine.spoken("yes"))
        assertEquals("QRC.", IncidentEngine.spoken(" QRC."))
        assertEquals("done", IncidentEngine.spoken("[Music] done"))
        assertEquals("There is a leak on floor 3.", IncidentEngine.spoken("There is a leak (water) on floor 3."))
    }

    @Test fun domainNormalization() {
        val codes = listOf("N", "QRC")
        assertEquals("It is N building, on the fourth floor.", IncidentEngine.normalizeSpeech("It is an end building, on the fourth floor.", codes))
        assertEquals("It is in N building.", IncidentEngine.normalizeSpeech("It is in M building.", codes))
        assertEquals("N block, floor 2", IncidentEngine.normalizeSpeech("en block, floor 2", codes))
        assertEquals("QRC, second floor", IncidentEngine.normalizeSpeech("Q.R.C., second floor", codes))
        assertEquals("QRC building", IncidentEngine.normalizeSpeech("Q R C building", codes))
        assertEquals("done", IncidentEngine.normalizeSpeech("Dunn.", codes))
        assertEquals("The pipe in the building is broken.", IncidentEngine.normalizeSpeech("The pipe in the building is broken.", codes))
        assertEquals("Done, it's on floor 3", IncidentEngine.normalizeSpeech("Done, it's on floor 3", codes))
    }
}
