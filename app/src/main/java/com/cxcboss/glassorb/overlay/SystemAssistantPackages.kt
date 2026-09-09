package com.cxcboss.glassorb.overlay

/**
 * Vendor package mapping for assistant window lifecycle detection.
 *
 * Keep this list narrow: packages that provide the assistant's wakeup engine
 * or home feed are intentionally excluded because their background windows
 * are always present and would cause false expansions.
 */
internal object SystemAssistantPackages {
    private val supportedPackages = setOf(
        // OPPO ColorOS 16 voice assistant observed on the connected device.
        "com.heytap.speechassist",
    )

    fun isSupported(packageName: String): Boolean = packageName in supportedPackages
}
