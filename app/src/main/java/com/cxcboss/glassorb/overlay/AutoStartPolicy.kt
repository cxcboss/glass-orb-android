package com.cxcboss.glassorb.overlay

import android.content.Intent

internal object AutoStartPolicy {
    fun shouldStartOnBoot(
        action: String?,
        enabled: Boolean,
        overlayPermission: Boolean,
        accessibilityEnabled: Boolean,
        accessibilityRequired: Boolean,
    ): Boolean = action == Intent.ACTION_BOOT_COMPLETED &&
        enabled &&
        overlayPermission &&
        (!accessibilityRequired || accessibilityEnabled)
}
