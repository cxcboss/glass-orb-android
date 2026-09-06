# Android 原生设置界面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 App 内设置界面全面改为原生 Material 3 风格，并让二级页面支持 Android 最新预测返回手势，同时保持玻璃球和配置核心行为不变。

**Architecture:** 使用 Navigation Compose 2.10.0 的单一 `NavHost` 管理首页、服务详情、七个配置页、预设、JSON 和关于页面。页面只组合当前目的地，控件使用 Material 3 标准组件；Navigation Compose 负责 back stack 和预测返回过渡，根页不拦截系统返回。

**Tech Stack:** Kotlin 2.4.10、Compose BOM 2026.06.01、Material 3、Navigation Compose 2.10.0、AndroidX Activity Compose 1.12.2、compileSdk 37 / targetSdk 36 / minSdk 26。

## Global Constraints

- 保留 `OrbConfig`、`OrbConfigRepository`、七组参数、默认值、预设、JSON schema、权限和悬浮服务行为。
- 不修改 `model/`、`data/`、`motion/`、`overlay/`、`render/`、`res/raw/`、shader 和悬浮球窗口实现。
- 移除设置页的 iOS 固定色值、Liquid Glass/Backdrop、渐变背景、自绘底栏、自绘滑块/开关和 GLES 实时预览。
- 设置控件优先使用 Material 3 `Scaffold`、`TopAppBar`、`ListItem`、`Switch`、`Slider`、`Button`、`OutlinedTextField`、`AlertDialog`、`SnackbarHost`。
- 主题使用系统动态色；不支持动态色时回退 Material 3 默认 light/dark scheme；不分发 SF Pro 或 SF Symbols。
- Manifest/Activity 开启 `android:enableOnBackInvokedCallback="true"`；根页不拦截返回，二级页由 Navigation Compose 处理可取消预测返回。
- 不运行模拟器或实体机人工测试；必须运行 `testDebugUnitTest`、`lintDebug`、`assembleDebug`、`compileDebugAndroidTestKotlin`。

---

### Task 1: 导航依赖、路由模型与预测返回基础

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/SettingsUiLogic.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/MainActivity.kt`
- Test: `app/src/test/java/com/cxcboss/glassorb/ui/SettingsUiLogicTest.kt`

**Interfaces:**
- Produces `SettingsRoute` string routes (`home`, `overlay`, `group/{name}`, `presets`, `data`, `about`) and `routeFor(ConfigGroup)` for the NavHost.
- Preserves `resolveOverlayAction`, `sliderValueAt`, `isParameterModified` and existing `OrbConfig` callback contracts.

- [ ] **Step 1: Write failing route and predictive-back configuration tests**

```kotlin
@Test fun routeCodecRoundTripsEveryDestination() {
    ConfigGroup.entries.forEach { group ->
        assertEquals(group, parseSettingsRoute(routeFor(group)).group)
    }
    assertEquals(SettingsRoute.Home, parseSettingsRoute("home"))
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests 'com.cxcboss.glassorb.ui.SettingsUiLogicTest' --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache`

Expected: compile failure for the new route codec symbols.

- [ ] **Step 3: Add Navigation Compose 2.10.0 and opt into predictive back**

Add `implementation("androidx.navigation:navigation-compose:2.10.0")`. Add `android:enableOnBackInvokedCallback="true"` to the launcher activity or application. Keep targetSdk 36 and minSdk 26.

- [ ] **Step 4: Implement route codec and remove the obsolete custom stack API**

Use a sealed route model and deterministic parser:

```kotlin
sealed interface SettingsRoute {
    data object Home : SettingsRoute
    data object OverlayDetails : SettingsRoute
    data class ConfigGroupDetail(val group: ConfigGroup) : SettingsRoute
    data object Presets : SettingsRoute
    data object DataManagement : SettingsRoute
    data object About : SettingsRoute
}
fun routeFor(group: ConfigGroup) = "group/${group.name}"
```

Move only pure route parsing into `SettingsUiLogic.kt`; no configuration or overlay code moves.

- [ ] **Step 5: Replace `BackHandler` interception at the activity root**

`MainActivity` continues to call `setContent { GlassOrbTheme { SettingsScreen(...) } }`; it must not call `onBackPressedDispatcher` or intercept an empty NavController back stack. The root destination is allowed to finish the activity through the system.

- [ ] **Step 6: Run the focused test and commit**

Run the focused command from Step 2; expected all route and existing logic tests pass. Commit:
`git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/cxcboss/glassorb/ui/MainActivity.kt app/src/main/java/com/cxcboss/glassorb/ui/SettingsUiLogic.kt app/src/test/java/com/cxcboss/glassorb/ui/SettingsUiLogicTest.kt && git commit -m "feat: add navigation compose predictive back foundation"`

### Task 2: Material 3 主题和原生页面骨架

**Files:**
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/GlassOrbTheme.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/MainActivityTest.kt`
- Delete or stop referencing: `app/src/main/java/com/cxcboss/glassorb/ui/IosGlassSurface.kt`
- Test: `app/src/androidTest/java/com/cxcboss/glassorb/ui/MainActivityTest.kt`

**Interfaces:**
- `SettingsScreen` keeps the existing public callback parameters and receives/creates a `NavHostController` internally.
- `SettingsScreen` renders `Scaffold(topBar, snackbarHost)` and one `NavHost(startDestination = "home")`.

- [ ] **Step 1: Add failing Compose assertions for native structure**

```kotlin
composeRule.onNodeWithText("灵动玻璃球").assertIsDisplayed()
composeRule.onNodeWithText("悬浮球").assertIsDisplayed()
composeRule.onNodeWithText("外观").assertIsDisplayed()
composeRule.onNodeWithTag("settings-bottom-bar").assertDoesNotExist()
```

- [ ] **Step 2: Rebuild the theme around Material 3 color schemes**

Use `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` on API 31+, with `lightColorScheme()` / `darkColorScheme()` fallback. Do not hard-code the previous iOS palette. Keep system bars synchronized with the active scheme without API 26 lint violations.

- [ ] **Step 3: Replace `SettingsScreen` composition tree**

Remove the full-screen backdrop recorder, iOS groups, custom bottom bar, custom confirmation `Dialog`, and old `BackHandler`. Add `rememberNavController()`, `Scaffold`, `LargeTopAppBar` on Home, `TopAppBar` on secondary routes, and Material `NavHost` transitions. Use `popEnterTransition`/`popExitTransition` and Navigation Compose 2.10.0 predictive pop defaults so a canceled edge gesture restores the current page.

- [ ] **Step 4: Run Android source tests and commit**

Run: `./gradlew compileDebugAndroidTestKotlin --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache`

Expected: source compilation succeeds and no test references the removed bottom bar or preview. Commit:
`git add app/src/main/java/com/cxcboss/glassorb/ui/GlassOrbTheme.kt app/src/main/java/com/cxcboss/glassorb/ui/SettingsScreen.kt app/src/main/java/com/cxcboss/glassorb/ui/MainActivityTest.kt app/src/main/java/com/cxcboss/glassorb/ui/IosGlassSurface.kt && git commit -m "feat: rebuild settings scaffold with material 3"`

### Task 3: 首页、服务详情、预设、JSON 和关于页

**Files:**
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/SettingsScreen.kt`
- Modify: `app/src/androidTest/java/com/cxcboss/glassorb/ui/MainActivityTest.kt`

**Interfaces:**
- Navigation destinations call existing overlay/config callbacks only; no service implementation changes.
- Destructive actions use Material `AlertDialog`; transient results use `SnackbarHostState`.

- [ ] **Step 1: Add failing screen interaction assertions**

```kotlin
composeRule.onNodeWithText("运行与权限").performClick()
composeRule.onNodeWithText("停止悬浮层").assertIsDisplayed()
composeRule.onNodeWithContentDescription("返回").performClick()
composeRule.onNodeWithText("导入与导出").performClick()
composeRule.onNodeWithText("复制 JSON").assertIsDisplayed()
```

- [ ] **Step 2: Implement native Home and OverlayDetails destinations**

Use `ListItem` rows with trailing `Switch` only for the master switch. Keep action resolution exactly `resolveOverlayAction(enabled, permission, runtimeStatus)`. Use `Button`/`TextButton` for service operations and `AlertDialog` for stop confirmation.

- [ ] **Step 3: Implement Presets, DataManagement and About destinations**

Use `SingleChoiceSegmentedButtonRow` only when it is a standard Material 3 component; otherwise use `ListItem` + `RadioButton`. Use `OutlinedTextField(minLines = 8)` for JSON, `Button` for import/export, and `SnackbarHost` for success/errors. Preserve attribution and license text.

- [ ] **Step 4: Run Android source compilation and commit**

Run the command from Task 2 Step 4; expected PASS. Commit:
`git add app/src/main/java/com/cxcboss/glassorb/ui/SettingsScreen.kt app/src/androidTest/java/com/cxcboss/glassorb/ui/MainActivityTest.kt && git commit -m "feat: add native material settings destinations"`

### Task 4: 七组参数页改用标准 Material 控件

**Files:**
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/ConfigEditor.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/kyant/backdrop/catalog/components/LiquidSlider.kt`
- Modify: `app/src/main/java/com/kyant/backdrop/catalog/components/LiquidToggle.kt`
- Test: `app/src/test/java/com/cxcboss/glassorb/ui/SettingsUiLogicTest.kt`
- Test: `app/src/androidTest/java/com/cxcboss/glassorb/ui/MainActivityTest.kt`

**Interfaces:**
- `ConfigEditor(config, onConfigChange, group, onReset)` remains the public entry point.
- Each parameter callback copies the same `OrbConfig` subgroup and keeps the explicit default value/reset behavior.

- [ ] **Step 1: Add regression tests for standard control semantics**

Assert that each parameter page exposes a `Slider` semantics node with `ProgressBarRangeInfo`, that the master switch exposes `Role.Switch`, and that reset returns the exact `ReferenceConfig` value. Keep tests independent of pixels and custom drawing.

- [ ] **Step 2: Replace custom slider/toggle visuals and pointer input**

Use Material `Slider` with `value`, `onValueChange`, `valueRange`, `steps`, and `onValueChangeFinished`; use `Switch`/`Checkbox`/`RadioButton` for their matching parameter types. Remove Kyant Backdrop imports and pointer-input gesture code from settings controls. Keep `sliderValueAt` only for pure mapping tests if still needed by overlay code.

- [ ] **Step 3: Preserve all seven groups and special controls**

Keep capsule geometry/position, glass, dark field, waveform, dots, motion, performance, touch-area multiplier with its red preview callback, auto-collapse toggle/countdown, presets, group reset and all-reset. Use `LazyColumn` with stable item keys and `rememberUpdatedState` only where an external callback needs it.

- [ ] **Step 4: Run unit and Android source tests and commit**

Run: `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache`

Expected: all tests pass and source compilation succeeds. Commit:
`git add app/src/main/java/com/cxcboss/glassorb/ui/ConfigEditor.kt app/src/main/java/com/cxcboss/glassorb/ui/SettingsScreen.kt app/src/main/java/com/kyant/backdrop/catalog/components/LiquidSlider.kt app/src/main/java/com/kyant/backdrop/catalog/components/LiquidToggle.kt app/src/test/java/com/cxcboss/glassorb/ui/SettingsUiLogicTest.kt app/src/androidTest/java/com/cxcboss/glassorb/ui/MainActivityTest.kt && git commit -m "feat: use native material parameter controls"`

### Task 5: 文档、构建与交付

**Files:**
- Modify: `README.md`
- Modify: `THIRD_PARTY_NOTICES.md`
- Modify: `dist/测试说明.md`
- Modify: `dist/SHA256SUMS.txt`
- Replace: `dist/灵动玻璃球-demo.apk`

- [ ] **Step 1: Update documentation**

Document Material 3/dynamic color, Navigation Compose 2.10.0 predictive back behavior, preserved functionality, and the explicit no-device-test boundary. Remove claims that settings controls use Liquid Glass.

- [ ] **Step 2: Run the final verification commands**

Run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Expected: BUILD SUCCESSFUL, JVM tests have 0 failures, lint has 0 errors. Locate the generated APK with `BUILD_APK=$(find /var/folders -path '*/glass-orb-android-build/app/outputs/apk/debug/app-debug.apk' -type f -print -quit)`, copy it to `dist/灵动玻璃球-demo.apk`, run `shasum -a 256 -c dist/SHA256SUMS.txt`, and verify APK v2 signing plus package metadata.

- [ ] **Step 3: Commit and push**

Commit docs/APK with `git commit -m "feat: complete native Android settings redesign"`, verify `git diff --check`, then `git push origin main` as requested. Confirm `git status --short --branch` is clean and local/remote `main` resolve to the same commit.
