package com.cxcboss.glassorb.ui.glass

import android.content.Context
import android.view.View
import android.widget.FrameLayout

/**
 * Registry that ties glass surfaces to the view subtree they refract.
 *
 * [source] must never contain a registered glass view, otherwise capturing the
 * backdrop would recurse into the glass itself. Chrome (navigation bar, tab bar,
 * dialogs) therefore lives beside the source, not inside it.
 */
internal class GlassBackdrop(val source: View) {
    private val surfaces = ArrayList<LiquidGlassView>()

    fun attach(view: LiquidGlassView) {
        if (!surfaces.contains(view)) surfaces.add(view)
    }

    fun detach(view: LiquidGlassView) {
        surfaces.remove(view)
    }

    private val targetLocation = IntArray(2)
    private val surfaceLocation = IntArray(2)

    /**
     * Marks glass surfaces that overlap [target] for a fresh backdrop capture.
     * A slider dragged in the middle of the page therefore no longer re-blurs
     * the navigation and search glass on every frame; scrolling (the whole
     * page is the target) still refreshes everything in the same frame.
     */
    fun invalidateSurfaces(target: View? = null) {
        if (target == null || !target.isAttachedToWindow) {
            for (index in surfaces.indices) surfaces[index].invalidateBackdrop()
            return
        }
        target.getLocationInWindow(targetLocation)
        val left = targetLocation[0]
        val top = targetLocation[1]
        val right = left + (target.width * target.scaleX).toInt()
        val bottom = top + (target.height * target.scaleY).toInt()
        for (index in surfaces.indices) {
            val surface = surfaces[index]
            surface.getLocationInWindow(surfaceLocation)
            val overlaps = surfaceLocation[0] < right && surfaceLocation[0] + surface.width > left &&
                surfaceLocation[1] < bottom && surfaceLocation[1] + surface.height > top
            if (overlaps) surface.invalidateBackdrop()
        }
    }
}

/**
 * Container for scrolling content. Any descendant invalidation (scroll, page
 * slide, ripple, switch animation) synchronously dirties the glass surfaces, so
 * they re-capture in the same frame instead of lagging one frame behind.
 */
internal class BackdropHostLayout(context: Context) : FrameLayout(context) {
    var backdrop: GlassBackdrop? = null

    override fun onDescendantInvalidated(child: View, target: View) {
        super.onDescendantInvalidated(child, target)
        backdrop?.invalidateSurfaces(target)
    }
}
