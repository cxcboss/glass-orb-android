package com.cxcboss.glassorb.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatestFrameUpdateTest {
    @Test
    fun keepsOnlyTheNewestConfigurationBetweenFrames() {
        val updates = LatestFrameUpdate<Int>()
        updates.offer(1)
        updates.offer(2)
        updates.offer(3)

        assertEquals(3, updates.take())
        assertNull(updates.take())
    }

    @Test
    fun clearDropsAQueuedUpdateWithoutAffectingTheNextOne() {
        val updates = LatestFrameUpdate<String>()
        updates.offer("old")
        updates.clear()
        assertNull(updates.take())

        updates.offer("new")
        assertEquals("new", updates.take())
    }
}
