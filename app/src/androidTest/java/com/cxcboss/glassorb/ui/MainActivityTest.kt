package com.cxcboss.glassorb.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithText("光影与参数实时呈现").assertIsDisplayed()
        composeRule.onNodeWithText("概览").assertIsDisplayed()
        composeRule.onNodeWithText("外观").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("玻璃").assertExists()
        composeRule.onNodeWithText("动效").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("动画").assertExists()
    }
}
