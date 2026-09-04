package com.cxcboss.glassorb.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsScreenShowsPermissionAndSharedRendererPreview() {
        // The live GLES preview deliberately awaits every Compose frame forever.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("灵动玻璃球").assertIsDisplayed()
        composeRule.onNodeWithText("系统悬浮层").assertIsDisplayed()
        composeRule.onNodeWithText("实时预览").assertIsDisplayed()
        composeRule.onNodeWithText("与悬浮层共用 GLES 渲染器").assertIsDisplayed()
    }
}
