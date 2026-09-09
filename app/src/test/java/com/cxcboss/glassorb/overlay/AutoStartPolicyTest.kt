package com.cxcboss.glassorb.overlay

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartPolicyTest {
    @Test
    fun `boot starts only when the user enabled it and required permissions remain`() {
        assertTrue(
            AutoStartPolicy.shouldStartOnBoot(
                action = Intent.ACTION_BOOT_COMPLETED,
                enabled = true,
                overlayPermission = true,
                accessibilityEnabled = true,
                accessibilityRequired = true,
            ),
        )
    }

    @Test
    fun `boot does not start without user opt in or permissions`() {
        assertFalse(
            AutoStartPolicy.shouldStartOnBoot(
                action = Intent.ACTION_BOOT_COMPLETED,
                enabled = false,
                overlayPermission = true,
                accessibilityEnabled = true,
                accessibilityRequired = true,
            ),
        )
        assertFalse(
            AutoStartPolicy.shouldStartOnBoot(
                action = Intent.ACTION_BOOT_COMPLETED,
                enabled = true,
                overlayPermission = false,
                accessibilityEnabled = true,
                accessibilityRequired = true,
            ),
        )
        assertFalse(
            AutoStartPolicy.shouldStartOnBoot(
                action = Intent.ACTION_BOOT_COMPLETED,
                enabled = true,
                overlayPermission = true,
                accessibilityEnabled = false,
                accessibilityRequired = true,
            ),
        )
    }

    @Test
    fun `non boot broadcasts are ignored`() {
        assertFalse(
            AutoStartPolicy.shouldStartOnBoot(
                action = Intent.ACTION_SCREEN_ON,
                enabled = true,
                overlayPermission = true,
                accessibilityEnabled = true,
                accessibilityRequired = true,
            ),
        )
    }
}
