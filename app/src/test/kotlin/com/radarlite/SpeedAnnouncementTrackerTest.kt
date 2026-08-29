package com.radarlite

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedAnnouncementTrackerTest {
    private val selected = setOf(30, 50, 60, 80, 90, 100)

    @Test fun `first fix and slowing down stay quiet`() {
        val tracker = SpeedAnnouncementTracker()

        assertEquals(null, tracker.next(29f, selected))
        assertEquals(30, tracker.next(35f, selected))
        assertEquals(null, tracker.next(29f, selected))
        assertEquals(30, tracker.next(35f, selected))
    }

    @Test fun `sparse fix announces only its highest crossing`() {
        val tracker = SpeedAnnouncementTracker()

        assertEquals(null, tracker.next(20f, selected))
        assertEquals(80, tracker.next(85f, selected))
        assertEquals(100, tracker.next(105f, selected))
    }

    @Test fun `changing selection establishes a quiet baseline`() {
        val tracker = SpeedAnnouncementTracker()

        assertEquals(null, tracker.next(20f, setOf(50)))
        assertEquals(50, tracker.next(55f, setOf(50)))
        assertEquals(null, tracker.next(60f, emptySet()))
        assertEquals(null, tracker.next(60f, setOf(50)))
        assertEquals(null, tracker.next(49f, setOf(50)))
        assertEquals(50, tracker.next(55f, setOf(50)))
    }
}
