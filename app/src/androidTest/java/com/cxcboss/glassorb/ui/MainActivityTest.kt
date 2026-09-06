package com.cxcboss.glassorb.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun homeNavigatesToGroupAndBackWithoutPreviewOrTabs() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("灵动玻璃球")).check(matches(isDisplayed()))
            onView(withContentDescription("显示悬浮球")).check(matches(isDisplayed()))
            onView(withText("实时预览")).check(doesNotExist())
            onView(withText("概览")).check(doesNotExist())
            onView(withText("玻璃")).perform(click())
            onView(withText("内部深度")).check(matches(isDisplayed()))
            onView(withContentDescription("返回")).perform(click())
            onView(withText("灵动玻璃球")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun jsonImportIsAFullPageAndInvalidInputShowsInlineFeedback() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("导入与导出")).perform(scrollTo(), click())
            onView(withContentDescription("参数 JSON")).check(matches(isDisplayed()))
            onView(withText("导入参数")).perform(scrollTo(), click())
            onView(withText("请先粘贴参数 JSON")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun resetRequiresConfirmationAndCanBeCancelled() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("全部恢复")).perform(scrollTo(), click())
            onView(withText("恢复全部默认参数？")).check(matches(isDisplayed()))
            onView(withText("取消")).perform(click())
            onView(withText("恢复全部默认参数？")).check(doesNotExist())
        }
    }
}
