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
}
