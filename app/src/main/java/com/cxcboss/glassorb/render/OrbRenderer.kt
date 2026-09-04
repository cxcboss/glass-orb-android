package com.cxcboss.glassorb.render

interface OrbRenderer {
    fun submit(snapshot: RenderSnapshot)
    fun setPaused(paused: Boolean)
    fun release()
}
