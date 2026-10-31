package com.cxcboss.glassorb.overlay

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityOverlayBridgeTest {
    @Test
    fun emptyAssistantWindowSetEmitsDisappearanceForPreviouslyReportedPackage() {
        val event = assistantWindowDisappearanceEvent(
            reportedPackage = "com.miui.voiceassist",
            visibleAssistantPackages = emptySet(),
        )

        requireNotNull(event)
        assertEquals("com.miui.voiceassist", event.packageName)
        assertEquals(false, event.visible)
        assertEquals(AccessibilityEvent.TYPE_WINDOWS_CHANGED, event.eventType)
        assertEquals(AccessibilityEvent.WINDOWS_CHANGE_REMOVED, event.windowChanges)
    }

    @Test
    fun visibleAssistantWindowDoesNotEmitDisappearance() {
        assertNull(
            assistantWindowDisappearanceEvent(
                reportedPackage = "com.miui.voiceassist",
                visibleAssistantPackages = setOf("com.miui.voiceassist"),
            ),
        )
    }
}
