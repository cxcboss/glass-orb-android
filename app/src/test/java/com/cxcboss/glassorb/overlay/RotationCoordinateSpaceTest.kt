package com.cxcboss.glassorb.overlay

import com.cxcboss.glassorb.model.GeometryConfig
import com.cxcboss.glassorb.model.HorizontalAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationCoordinateSpaceTest {
    private val portraitDisplay = IntRect(0, 0, 1080, 2280)
    private val landscapeDisplay = IntRect(0, 0, 2280, 1080)

    @Test
    fun `portrait points follow Android display rotation rules`() {
        val point = FloatPoint(120f, 340f)

        assertEquals(FloatPoint(120f, 340f), RotationCoordinateSpace.mapPortraitPoint(point, portraitDisplay, 0))
        assertEquals(FloatPoint(1940f, 120f), RotationCoordinateSpace.mapPortraitPoint(point, landscapeDisplay, 1))
        assertEquals(FloatPoint(960f, 1940f), RotationCoordinateSpace.mapPortraitPoint(point, portraitDisplay, 2))
        assertEquals(FloatPoint(340f, 960f), RotationCoordinateSpace.mapPortraitPoint(point, landscapeDisplay, 3))
    }

    @Test
    fun `portrait and current points round trip for every rotation`() {
        val point = FloatPoint(421.5f, 1678.25f)

        for (rotation in 0..3) {
            val display = if (rotation == 1 || rotation == 3) landscapeDisplay else portraitDisplay
            val current = RotationCoordinateSpace.mapPortraitPoint(point, display, rotation)
            assertEquals(point, RotationCoordinateSpace.mapCurrentPoint(current, display, rotation))
        }
    }

    @Test
    fun `portrait anchor placement reaches both physical landscape sides`() {
        val left = RotationCoordinateSpace.mapPortraitPoint(FloatPoint(540f, 80f), landscapeDisplay, 1)
        val right = RotationCoordinateSpace.mapPortraitPoint(FloatPoint(540f, 80f), landscapeDisplay, 3)

        assertTrue(left.x > landscapeDisplay.width * 0.8f)
        assertTrue(right.x < landscapeDisplay.width * 0.2f)
        assertEquals(540f, left.y, 0f)
        assertEquals(540f, right.y, 0f)
    }

    @Test
    fun `portrait left and right anchors stay on their corresponding current edges`() {
        val display = RotationDisplayGeometry(
            physicalBounds = landscapeDisplay,
            safeBounds = IntRect(0, 0, 2280, 1080),
            systemInsets = IntRect(0, 0, 0, 0),
            rotation = 1,
        )
        val left = OverlayLayout.collapsedBounds(
            GeometryConfig(horizontalAnchor = HorizontalAnchor.Left, capsuleHeightDp = 48f), display, 1f,
        )
        val right = OverlayLayout.collapsedBounds(
            GeometryConfig(horizontalAnchor = HorizontalAnchor.Right, capsuleHeightDp = 48f), display, 1f,
        )

        assertTrue(left.top < right.top)
        assertEquals(left.left, right.left)
    }

    @Test
    fun `axis aligned placement clamps mapped shape to inset safe bounds`() {
        val safe = IntRect(left = 96, top = 24, right = 2184, bottom = 1056)
        val placed = RotationCoordinateSpace.placeAxisAligned(
            center = FloatPoint(2270f, 540f),
            width = 360,
            height = 360,
            safeBounds = safe,
        )

        assertEquals(safe.right, placed.right)
        assertEquals(360, placed.width)
        assertEquals(360, placed.height)
        assertTrue(placed.top >= safe.top)
        assertTrue(placed.bottom <= safe.bottom)
    }

    @Test
    fun `current edge insets become portrait reference edge insets`() {
        val display = RotationDisplayGeometry(
            physicalBounds = landscapeDisplay,
            safeBounds = IntRect(0, 0, 2280, 1080),
            systemInsets = IntRect(left = 14, top = 22, right = 34, bottom = 46),
            rotation = 1,
        )

        val portraitSafe = RotationCoordinateSpace.portraitSafeBounds(display)

        assertEquals(22, portraitSafe.left)
        assertEquals(1034, portraitSafe.right)
        assertEquals(0, portraitSafe.top)
        assertEquals(2266, portraitSafe.bottom)
    }

    @Test
    fun `portrait safe bounds preserve all four mapped edge insets`() {
        val insets = IntRect(left = 14, top = 22, right = 34, bottom = 46)
        val expected = mapOf(
            0 to IntRect(14, 0, 1046, 2234),
            1 to IntRect(22, 0, 1034, 2266),
            2 to IntRect(34, 0, 1066, 2258),
            3 to IntRect(46, 0, 1058, 2246),
        )

        for ((rotation, safe) in expected) {
            val display = RotationDisplayGeometry(
                physicalBounds = if (rotation == 1 || rotation == 3) landscapeDisplay else portraitDisplay,
                safeBounds = IntRect(0, 0, 2280, 1080),
                systemInsets = insets,
                rotation = rotation,
            )
            assertEquals("rotation $rotation", safe, RotationCoordinateSpace.portraitSafeBounds(display))
        }
    }

    @Test
    fun `capsule position slider can place visual island inside status bar`() {
        val display = RotationDisplayGeometry(
            physicalBounds = portraitDisplay,
            safeBounds = IntRect(0, 0, 1080, 2208),
            systemInsets = IntRect(left = 0, top = 96, right = 0, bottom = 72),
            rotation = 0,
        )

        val capsule = OverlayLayout.collapsedBounds(
            GeometryConfig(verticalOffsetDp = 0f, capsuleWidthDp = 118f, capsuleHeightDp = 34f),
            display,
            1f,
        )

        assertEquals(0, capsule.top)
        assertTrue(capsule.bottom > capsule.top)
    }

    @Test
    fun `mapped landscape center moves to physical side without rotating dimensions`() {
        val geometry = GeometryConfig(
            capsuleWidthDp = 118f,
            capsuleHeightDp = 48f,
            horizontalAnchor = HorizontalAnchor.Center,
        )
        val display = RotationDisplayGeometry(
            physicalBounds = landscapeDisplay,
            safeBounds = IntRect(0, 0, 2280, 1080),
            systemInsets = IntRect(0, 0, 0, 0),
            rotation = 1,
        )

        val collapsed = OverlayLayout.collapsedBounds(geometry, display, 1f)

        assertEquals(118, collapsed.width)
        assertEquals(48, collapsed.height)
        assertTrue(collapsed.left > landscapeDisplay.width / 2)
        assertEquals(540, collapsed.top + collapsed.height / 2)
    }

    @Test
    fun `expanded and orb touch geometry preserve independent dimensions and expansion gap`() {
        val geometry = GeometryConfig(
            capsuleWidthDp = 118f,
            capsuleHeightDp = 48f,
            orbWidthDp = 160f,
            orbHeightDp = 96f,
            expandBelowCapsule = true,
        )
        val display = RotationDisplayGeometry(
            physicalBounds = landscapeDisplay,
            safeBounds = IntRect(0, 0, 2280, 1080),
            systemInsets = IntRect(0, 0, 0, 0),
            rotation = 1,
        )
        val capsule = OverlayLayout.collapsedBounds(geometry, display, 1f)
        val orb = OverlayLayout.orbTouchBounds(geometry, display, 1f)

        assertEquals(160, orb.width)
        assertEquals(96, orb.height)
        assertEquals(68, orb.top - capsule.top)
    }

    @Test
    fun `downward expansion changes orb position without moving capsule anchor`() {
        val normal = GeometryConfig(capsuleHeightDp = 48f, expandBelowCapsule = false)
        val below = normal.copy(expandBelowCapsule = true)
        val display = RotationDisplayGeometry(
            physicalBounds = portraitDisplay,
            safeBounds = IntRect(0, 0, 1080, 2208),
            systemInsets = IntRect(0, 24, 0, 72),
            rotation = 0,
        )
        val normalCapsule = OverlayLayout.collapsedBounds(normal, display, 1f)
        val belowCapsule = OverlayLayout.collapsedBounds(below, display, 1f)
        val normalFrame = OverlayLayout.expandedFrame(normal, display, 1f)
        val belowFrame = OverlayLayout.expandedFrame(below, display, 1f)
        val normalAnchor = OverlayLayout.anchorOffsets(normalFrame.bounds, normalCapsule, 1f)
        val belowAnchor = OverlayLayout.anchorOffsets(belowFrame.bounds, belowCapsule, 1f)

        assertEquals(normalCapsule.left, belowCapsule.left)
        assertEquals(normalCapsule.top, belowCapsule.top)
        assertEquals(normalCapsule.top, normalFrame.bounds.top + normalAnchor.topDp.toInt())
        assertEquals(belowCapsule.top, belowFrame.bounds.top + belowAnchor.topDp.toInt())
        assertEquals(20, OverlayLayout.orbTouchBounds(below, display, 1f).top - belowCapsule.bottom)
    }

    @Test
    fun `expanded and capsule touch bounds follow the mapped displayed shape`() {
        val geometry = GeometryConfig(
            capsuleWidthDp = 118f,
            capsuleHeightDp = 48f,
            orbWidthDp = 160f,
            orbHeightDp = 96f,
            enlargedTouchArea = true,
            touchAreaScale = 2f,
        )
        val display = RotationDisplayGeometry(
            physicalBounds = landscapeDisplay,
            safeBounds = IntRect(96, 24, 2184, 1056),
            systemInsets = IntRect(96, 0, 0, 24),
            rotation = 1,
        )
        val capsule = OverlayLayout.collapsedBounds(geometry, display, 1f)
        val capsuleTouch = OverlayLayout.capsuleTouchBounds(geometry, display, 1f)
        val orbTouch = OverlayLayout.orbTouchBounds(geometry, display, 1f)
        val expanded = OverlayLayout.expandedFrame(geometry, display, 1f)

        assertTrue(capsule.left > display.safeBounds.width / 2)
        assertTrue(capsuleTouch.left <= capsule.left)
        assertTrue(capsuleTouch.right >= capsule.right)
        assertEquals(display.safeBounds.right, capsuleTouch.right)
        assertTrue(capsuleTouch.width >= capsule.width)
        assertEquals(160, orbTouch.width)
        assertEquals(96, orbTouch.height)
        assertTrue(expanded.bounds.left >= display.safeBounds.left)
        assertTrue(expanded.bounds.right <= display.safeBounds.right)
        assertTrue(expanded.visualBottomPx <= expanded.bounds.bottom)
        assertEquals(expanded.visualBottomPx,
            OverlayLayout.expandedVisualBottomPx(geometry, display, 1f))
    }

    @Test
    fun `stable geometry selection rejects a rotation transition sample`() {
        val previous = RotationDisplayGeometry(
            physicalBounds = portraitDisplay,
            safeBounds = IntRect(0, 24, 1080, 2208),
            systemInsets = IntRect(0, 24, 0, 72),
            rotation = 0,
        )
        val transition = RotationDisplaySample(
            geometry = RotationDisplayGeometry(
                physicalBounds = landscapeDisplay,
                safeBounds = IntRect(0, 0, 2280, 1056),
                systemInsets = IntRect(0, 0, 0, 24),
                rotation = 1,
            ),
            rotationBefore = 1,
            rotationAfter = 0,
        )
        val stable = transition.copy(rotationAfter = 1)

        assertEquals(stable.geometry,
            RotationCoordinateSpace.selectStableGeometry(listOf(transition, stable), previous))
        assertEquals(previous,
            RotationCoordinateSpace.selectStableGeometry(listOf(transition), previous))
    }

    @Test
    fun `presentation snapshot retains one display geometry and final expanded frame`() {
        val display = RotationDisplayGeometry(
            physicalBounds = landscapeDisplay,
            safeBounds = IntRect(0, 0, 2280, 1080),
            systemInsets = IntRect(0, 0, 0, 0),
            rotation = 1,
        )
        val snapshot = OverlayLayout.presentationSnapshot(GeometryConfig(), display, 1f)

        assertEquals(display, snapshot.display)
        assertEquals(snapshot.expandedFrame,
            OverlayLayout.expandedFrame(GeometryConfig(), snapshot.display, 1f))
        assertEquals(snapshot.expandedFrame.visualBottomPx,
            OverlayLayout.expandedVisualBottomPx(GeometryConfig(), snapshot.display, 1f))
    }
}
