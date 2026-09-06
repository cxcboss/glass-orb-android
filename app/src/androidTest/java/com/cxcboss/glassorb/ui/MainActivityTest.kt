package com.cxcboss.glassorb.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeNavigatesToGroupAndBackWithoutPreviewOrTabs() {
        composeRule.onNodeWithText("灵动玻璃球").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("显示悬浮球").assertExists()
        composeRule.onNodeWithText("实时预览").assertDoesNotExist()
        composeRule.onNodeWithText("概览").assertDoesNotExist()
        composeRule.onNodeWithText("玻璃").performClick()
        composeRule.onNodeWithText("内部深度").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("灵动玻璃球").assertIsDisplayed()
    }

    @Test
    fun jsonImportIsAFullPageAndInvalidInputShowsInlineFeedback() {
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasText("导入与导出"))
        composeRule.onNodeWithText("导入与导出").performClick()
        composeRule.onNodeWithText("参数 JSON").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasText("导入参数"))
        composeRule.onNodeWithText("导入参数").performClick()
        composeRule.onNodeWithText("请先粘贴参数 JSON").assertIsDisplayed()
    }

    @Test
    fun resetRequiresConfirmationAndCanBeCancelled() {
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasText("全部恢复"))
        composeRule.onNodeWithText("全部恢复").performClick()
        composeRule.onNodeWithText("恢复全部默认参数？").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("恢复全部默认参数？").assertDoesNotExist()
    }
}
