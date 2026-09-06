package com.cxcboss.glassorb.ui

import com.cxcboss.glassorb.data.ConfigGroup
import com.cxcboss.glassorb.overlay.OverlayRuntimeStatus
import org.junit.Assert.*
import org.junit.Test

class SettingsUiLogicTest {
    @Test fun routeCodecRoundTripsEveryDestination() {
        assertEquals(SettingsRoute.Home, parseSettingsRoute("home"))
        assertEquals(SettingsRoute.OverlayDetails, parseSettingsRoute("overlay"))
        assertEquals(SettingsRoute.Presets, parseSettingsRoute("presets"))
        assertEquals(SettingsRoute.DataManagement, parseSettingsRoute("data"))
        assertEquals(SettingsRoute.About, parseSettingsRoute("about"))
        ConfigGroup.entries.forEach { group ->
            assertEquals(group, (parseSettingsRoute(routeFor(group)) as SettingsRoute.ConfigGroupDetail).group)
        }
        assertEquals(SettingsRoute.Home, parseSettingsRoute("unknown"))
    }

    @Test fun navigationPreservesRootAndRestoresDetail() {
        val root = SettingsNavigator()
        assertEquals(1, root.depth)
        assertEquals(root, root.pop())
        val detail = root.push(SettingsRoute.ConfigGroupDetail(ConfigGroup.Glass)).push(SettingsRoute.About)
        assertEquals(3, detail.depth)
        assertEquals(SettingsRoute.ConfigGroupDetail(ConfigGroup.Glass), detail.pop().current)
        assertEquals(detail, SettingsNavigator.restore(detail.save()))
        assertEquals(root, SettingsNavigator.restore(listOf("invalid")))
    }

    @Test fun masterSwitchResolvesPermissionAndRuntimeActions() {
        assertEquals(OverlayAction.RequestPermission, resolveOverlayAction(true, false, OverlayRuntimeStatus.Hidden))
        assertEquals(OverlayAction.Show, resolveOverlayAction(true, true, OverlayRuntimeStatus.Hidden))
        listOf(OverlayRuntimeStatus.Stopped, OverlayRuntimeStatus.PermissionRequired, OverlayRuntimeStatus.Error("failed")).forEach {
            assertEquals(OverlayAction.Start, resolveOverlayAction(true, true, it))
        }
        assertEquals(OverlayAction.None, resolveOverlayAction(true, true, OverlayRuntimeStatus.Visible))
        assertEquals(OverlayAction.Hide, resolveOverlayAction(false, true, OverlayRuntimeStatus.Visible))
        assertEquals(OverlayAction.Hide, resolveOverlayAction(false, false, OverlayRuntimeStatus.Hidden))
    }

    @Test fun sliderClampsCoordinatesAndMirrorsRtl() {
        assertEquals(-20f, sliderValueAt(-10f, 100f, -20f..80f, false), 0f)
        assertEquals(80f, sliderValueAt(120f, 100f, -20f..80f, false), 0f)
        assertEquals(5f, sliderValueAt(25f, 100f, -20f..80f, false), 0f)
        assertEquals(55f, sliderValueAt(25f, 100f, -20f..80f, true), 0f)
        assertEquals(80f, sliderValueAt(0f, 100f, -20f..80f, true), 0f)
        assertEquals(-20f, sliderValueAt(100f, 100f, -20f..80f, true), 0f)
        assertEquals(-20f, sliderValueAt(10f, 0f, -20f..80f, false), 0f)
    }

    @Test fun sliderLocksToHorizontalOnlyAfterTouchSlop() {
        assertEquals(SliderGestureAxis.Undecided, sliderGestureAxis(3f, 3f, 8f))
        assertEquals(SliderGestureAxis.Horizontal, sliderGestureAxis(12f, 4f, 8f))
        assertEquals(SliderGestureAxis.Vertical, sliderGestureAxis(4f, -12f, 8f))
        assertEquals(SliderGestureAxis.Undecided, sliderGestureAxis(Float.NaN, 4f, 8f))
    }

    @Test fun cancelledTapDoesNotCommitSliderValue() {
        assertTrue(shouldCommitSliderTap(dragging = false, pressed = false, consumed = false))
        assertFalse(shouldCommitSliderTap(dragging = false, pressed = false, consumed = true))
        assertFalse(shouldCommitSliderTap(dragging = true, pressed = false, consumed = false))
    }

    @Test fun defaultComparisonMatchesDisplayedPrecisionAndRestoresExactDefault() {
        assertFalse(isParameterModified(1.004f, 1f, 2))
        assertTrue(isParameterModified(1.01f, 1f, 2))
        assertTrue(isParameterModified(-1f, 1f, 1))
        assertFalse(isParameterModified(restoreParameter(1.234f), 1.234f, 3))
        assertEquals(1.234f, restoreParameter(1.234f), 0f)
    }
}
