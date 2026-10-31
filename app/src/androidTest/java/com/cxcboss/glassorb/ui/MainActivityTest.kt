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

/** Strings are the Chinese source copy; run on a zh-CN device or emulator. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun rootListOpensGroupPageAndGoesBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withContentDescription("显示灵动岛")).check(matches(isDisplayed()))
            onView(withText("颜色与光影")).perform(scrollTo(), click())
            onView(withText("内部深度")).check(matches(isDisplayed()))
            onView(withContentDescription("返回")).perform(click())
            onView(withText("颜色与光影")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun groupResetRequiresConfirmationAndCanBeCancelled() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("颜色与光影")).perform(scrollTo(), click())
            onView(withText("恢复本组默认值")).perform(scrollTo(), click())
            onView(withText("恢复本组默认参数？")).check(matches(isDisplayed()))
            onView(withText("取消")).perform(click())
            onView(withText("恢复本组默认参数？")).check(doesNotExist())
        }
    }

    @Test
    fun runtimePageExposesAutoStart() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("运行与权限")).perform(click())
            onView(withContentDescription("开机自动启动")).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }
}
