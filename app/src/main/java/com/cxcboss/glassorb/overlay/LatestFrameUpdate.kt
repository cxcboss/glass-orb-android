package com.cxcboss.glassorb.overlay

/**
 * Keeps only the newest value until the next display frame consumes it.
 * OverlayWindowController is main-thread confined, so this deliberately stays
 * allocation-light and does not add locks to the rendering path.
 */
internal class LatestFrameUpdate<T> {
    private var latest: T? = null

    fun offer(value: T) {
        latest = value
    }

    fun take(): T? = latest.also { latest = null }

    fun clear() {
        latest = null
    }
}
