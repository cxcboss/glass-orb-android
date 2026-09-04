# 玻璃球顶部锚点动效与 iOS 风格设置页实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有玻璃 Shader 质感的前提下，实现顶部锚点展开/收起、跟手橡皮筋、极小球体形变和防闪烁收起，并将设置页改成跟随系统深浅色的 iOS 风格界面。

**Architecture:** 悬浮窗继续使用单个扩展画布和 TextureView；`WindowManager` 只在两个端点切换尺寸。纯 Kotlin 的弹性数学和形状几何先生成不可变渲染快照，再由 GL 线程消费；玻璃 Shader 文件保持不变。设置页以 Compose 分组列表和自绘液态玻璃底栏承载现有调参能力。

**Tech Stack:** Kotlin 2.4.10、Compose BOM 2026.06.01、Material 3、TextureView、EGL14/GLES30、JVM JUnit 4、Android Compose instrumentation tests。

## Global Constraints

- 保持包名 `com.cxcboss.glassorb`、单 `app` 模块和 `minSdk 26`。
- 不修改 `app/src/main/assets/shaders/glass.frag`、`container.frag`、`effect.frag` 的视觉公式。
- 不读取、录制或折射下层 App 内容；普通应用和桌面之上的悬浮能力不变。
- 窗口只在展开开始和收起完成两个端点调用 `WindowManager.updateViewLayout`，动画过程中不得逐帧改窗口尺寸。
- 上滑关闭阈值保持 64dp 或 800dp/s；明显向上拖动才关闭，多指触摸继续取消当前手势。
- 收起最后一帧必须在扩展窗口内绘制纯黑胶囊，下一次 Choreographer 回调才缩小窗口。
- 轻微形变最大拖拽 8dp、缩放变化不超过 2%；所有过渡保持预乘 Alpha 和透明窗口。
- 设置页浅色使用 `#F2F2F7/#FFFFFF/#007AFF/#FF3B30`，深色使用 `#000000/#1C1C1E/#0A84FF/#FF453A`，主题跟随系统。
- 本轮不重复执行 APK 仪器测试；提交前运行 JVM 单元测试、`lintDebug` 和 `assembleDebug`。

## 文件与职责

- Create: `app/src/main/java/com/cxcboss/glassorb/motion/ElasticDrag.kt` — 橡皮筋压缩、收起进度和极小形变的纯数学 API。
- Create: `app/src/main/java/com/cxcboss/glassorb/render/ShapeMetrics.kt` — 顶部锚点下的形状尺寸/中心计算，供控制器和 GL 管线共用。
- Create: `app/src/main/java/com/cxcboss/glassorb/ui/IosGlassSurface.kt` — 设置页半透明卡片和悬浮底栏的 Compose 修饰器/组件。
- Modify: `app/src/main/java/com/cxcboss/glassorb/motion/AnalyticSpring.kt` — 增加带初速度的 seed 接口。
- Modify: `app/src/main/java/com/cxcboss/glassorb/model/OrbConfig.kt`、`model/OrbConfigJson.kt`、`ui/ConfigEditor.kt` — 新增可调拖拽阻力/形变/回弹参数并完成范围夹紧与 JSON 兼容。
- Modify: `app/src/main/java/com/cxcboss/glassorb/overlay/OverlayWindowController.kt`、`overlay/OverlayState.kt` — 顶部锚点、跟手收起、形变弹簧和延后一帧缩窗。
- Modify: `app/src/main/java/com/cxcboss/glassorb/render/RenderSnapshot.kt`、`render/GlOrbPipeline.kt` — 传递形变快照并保持 panel 顶部固定。
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/GlassOrbTheme.kt`、`ui/MainActivity.kt`、`ui/SettingsScreen.kt` — iOS 配色、系统栏外观、分组页面和浮动底栏。
- Modify: `app/src/test/java/com/cxcboss/glassorb/...` — 数学、锚点、配置和状态时序测试。
- Modify: `README.md`、`dist/测试说明.md` — 更新手势、主题和未验证边界说明。

---

### Task 1: 建立弹性数学与配置接口

**Files:**
- Create: `app/src/main/java/com/cxcboss/glassorb/motion/ElasticDrag.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/motion/AnalyticSpring.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/model/OrbConfig.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/model/OrbConfigJson.kt`
- Test: `app/src/test/java/com/cxcboss/glassorb/motion/ElasticDragTest.kt`
- Test: `app/src/test/java/com/cxcboss/glassorb/motion/AnalyticSpringTest.kt`
- Test: `app/src/test/java/com/cxcboss/glassorb/model/OrbConfigTest.kt`

**Interfaces:**
- `ElasticDrag.rubberBand(valueDp: Float, rangeDp: Float, resistance: Float): Float` 返回与输入同号、连续且绝对值不超过 `rangeDp` 的压缩位移。
- `ElasticDrag.collapseProgress(rawUpwardDp: Float, collapseRangeDp: Float): Float` 接收非负上滑距离并返回 `[0,1]` 进度。
- `data class DragDeformation(val offsetXDp: Float, val offsetYDp: Float, val scaleX: Float, val scaleY: Float, val topOffsetDp: Float)` 是不可变形变快照；`ElasticDrag.deformation(offsetXDp: Float, offsetYDp: Float, maxDragDp: Float, maxScaleDelta: Float): DragDeformation` 返回该类型。
- `AnalyticSpring.seed(newValue: Float, newVelocity: Float, newTarget: Float)` 原子替换值、速度和目标，不改变当前弹簧参数。
- `MotionConfig` 增加 `collapseRangeDp=48f`、`dragRangeDp=64f`、`dragResistance=0.62f`、`deformLimitDp=8f`、`deformScaleDelta=0.016f`、`deformResponse=0.24f`、`deformDamping=0.68f`；所有字段写入 `normalized()` 和 JSON 的可选字段，旧 JSON 缺失时使用这些默认值。

- [ ] **Step 1: 写失败测试**

  在 `ElasticDragTest` 中验证 `rubberBand(-64,64,.62)` 仍为负且绝对值小于 64、输入增大时输出单调、极大输入趋近 64；验证 `collapseProgress(0)=0`、`collapseProgress(48)` 在 0 和 1 之间、极大输入为 1；验证形变的两个缩放因子在 `1±0.016` 内且 `topOffsetDp` 不超过 2dp。为 `AnalyticSpringTest` 增加 `seed` 后第一步同时受初速度影响的断言，为 `OrbConfigTest` 增加新增字段 JSON 往返和越界夹紧断言。

- [ ] **Step 2: 运行失败测试**

  运行：`./gradlew testDebugUnitTest --tests 'com.cxcboss.glassorb.motion.ElasticDragTest' --tests 'com.cxcboss.glassorb.motion.AnalyticSpringTest' --tests 'com.cxcboss.glassorb.model.OrbConfigTest' --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache`

  预期：新增 API 尚不存在，测试编译失败。

- [ ] **Step 3: 实现最小数学和配置**

  `rubberBand` 使用 `sign(value) * range * (1 - exp(-abs(value) * resistance / range))`，对无效范围回退为 0；`collapseProgress` 使用 `1 - exp(-distance / collapseRangeDp)` 并夹紧；`deformation` 先对两个轴使用橡皮筋压缩，再按 `abs(offset)/maxDrag` 线性映射缩放，`topOffsetDp` 为 `-offsetY*0.25` 并夹紧到 ±2dp。`AnalyticSpring.seed` 直接更新三个内部状态。`MotionConfig.normalized` 对新字段执行有限值和范围夹紧，JSON 解析忽略未知字段并保留旧 schema 兼容。

- [ ] **Step 4: 运行通过测试**

  运行同 Step 2；预期全部通过，并运行 `./gradlew testDebugUnitTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache` 确认既有测试没有回归。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/java/com/cxcboss/glassorb/motion app/src/main/java/com/cxcboss/glassorb/model app/src/test/java/com/cxcboss/glassorb/motion app/src/test/java/com/cxcboss/glassorb/model
  git commit -m "feat: add elastic orb motion primitives"
  ```

### Task 2: 固定顶部锚点并扩展渲染快照

**Files:**
- Create: `app/src/main/java/com/cxcboss/glassorb/render/ShapeMetrics.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/render/RenderSnapshot.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/render/GlOrbPipeline.kt`
- Test: `app/src/test/java/com/cxcboss/glassorb/render/ShapeMetricsTest.kt`
- Test: `app/src/test/java/com/cxcboss/glassorb/render/RenderTransitionTest.kt`

**Interfaces:**
- `data class ShapeMetrics(val topDp: Float, val centerXDp: Float, val centerYDp: Float, val widthDp: Float, val heightDp: Float)` 是不可变形状快照；`ShapeMetrics.interpolate(geometry: GeometryConfig, anchorTopDp: Float, anchorCenterXDp: Float, morph: Float, deformation: DragDeformation): ShapeMetrics` 返回该类型，其中 `centerYDp = topDp + heightDp / 2`。
- `RenderSnapshot` 增加 `collapsePull: Float` 和 `deformation: DragDeformation`，保留 `capsuleCenterOffsetDp/capsuleTopOffsetDp` 以兼容预览和旧快照调用。

- [ ] **Step 1: 写失败测试**

  在 `ShapeMetricsTest` 中使用胶囊 118×34dp、球体 128dp，检查 morph 为 0 和 1 时 `topDp` 完全相同；检查五个中间进度的 `centerYDp - heightDp/2 == topDp`，形变缩放不改变顶部锚点；检查宽高和中心横坐标对非法 morph 会夹紧。为 `RenderTransitionTest` 增加新字段默认值不会影响波形/圆点权重。

- [ ] **Step 2: 运行失败测试**

  运行：`./gradlew testDebugUnitTest --tests 'com.cxcboss.glassorb.render.ShapeMetricsTest' --tests 'com.cxcboss.glassorb.render.RenderTransitionTest' --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache`

  预期：`ShapeMetrics` 和新增快照字段尚不存在，测试编译失败。

- [ ] **Step 3: 实现几何和管线接线**

  `ShapeMetrics` 先对 morph 做 `coerceIn(0f,1.08f)`，宽高从胶囊到球体插值后乘 `deformation.scaleX/scaleY`，顶部固定为 `anchorTopDp + deformation.topOffsetDp`，中心由顶部加半高得到。`GlOrbPipeline.render` 改为使用该结果计算 `uPanelOrigin/uPanelSize`、效果原点和中心，不改任何 shader 源码；收起拖动的 `collapsePull` 只参与 morph 计算，不直接平移球心。

- [ ] **Step 4: 运行通过测试**

  运行同 Step 2，再运行完整 `testDebugUnitTest`；预期全部通过。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/java/com/cxcboss/glassorb/render app/src/test/java/com/cxcboss/glassorb/render
  git commit -m "feat: anchor orb geometry to capsule top"
  ```

### Task 3: 接入跟手收起、轻微形变和防闪烁窗口时序

**Files:**
- Modify: `app/src/main/java/com/cxcboss/glassorb/overlay/OverlayState.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/overlay/OverlayWindowController.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/overlay/OverlayLayout.kt`
- Test: `app/src/test/java/com/cxcboss/glassorb/overlay/OverlayLayoutTest.kt`
- Test: `app/src/test/java/com/cxcboss/glassorb/overlay/OverlayStateMachineTest.kt`
- Test: `app/src/test/java/com/cxcboss/glassorb/overlay/OverlayWindowMotionTest.kt`

**Interfaces:**
- 控制器内部维护 `windowExpanded`、`pendingCollapsedResizeFrames`、`anchorTopDp`、`anchorCenterXDp`、`dragXSpring`、`dragYSpring` 和 `collapsePull`。
- `OverlayWindowMotionTest` 通过纯快照/测试可见状态验证：收起稳定后先保持扩展窗口一个渲染周期，再切换窗口尺寸；恢复手势不会进入 `Collapsing`。

- [ ] **Step 1: 写失败测试**

  增加状态机断言：`SwipeTracking → Restore` 回到原状态，`SwipeTracking → Collapse` 进入 `Collapsing`；增加窗口动作测试用假时间检查 `pendingCollapsedResizeFrames` 在第一个稳定帧仍为 1、下一帧才允许缩窗。扩展 `OverlayLayoutTest` 检查 anchor 偏移在横竖屏安全区内保持一致。

- [ ] **Step 2: 运行失败测试**

  运行：`./gradlew testDebugUnitTest --tests 'com.cxcboss.glassorb.overlay.OverlayStateMachineTest' --tests 'com.cxcboss.glassorb.overlay.OverlayLayoutTest' --tests 'com.cxcboss.glassorb.overlay.OverlayWindowMotionTest' --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache`

  预期：窗口延迟标志和测试可见状态尚不存在，新增测试失败或无法编译。

- [ ] **Step 3: 实现控制器时序**

  `beginExpand` 先 `updateBounds(expanded=true)`，再把 `morphSpring` 目标设为 1；`onFrame` 使用 `ShapeMetrics` 的顶部锚点输出。移动事件同时记录 X/Y：向上方向计算 `collapsePull`，将 `visualMorph = 1 - collapseProgress` 写入快照；其他方向交给 `ElasticDrag.deformation`，通过两个形变弹簧跟随。释放时用 `AnalyticSpring.seed` 注入速度，按 `SwipeDecision` 选择 0 或 1 目标。收起稳定后状态先切 `Collapsed`、快照提交黑胶囊、设置 `pendingCollapsedResizeFrames=1`；下一次回调再 `updateBounds(false)` 并清零偏移。所有多指/取消路径清理 VelocityTracker 和形变目标。

- [ ] **Step 4: 运行通过测试**

  运行 Step 2 的测试集合和完整 `testDebugUnitTest`；预期所有 JVM 测试通过。只做源码级行为验证，不重新执行 APK 仪器测试。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/java/com/cxcboss/glassorb/overlay app/src/main/java/com/cxcboss/glassorb/render app/src/test/java/com/cxcboss/glassorb/overlay
  git commit -m "fix: make orb expansion and collapse elastic"
  ```

### Task 4: 设置页 iOS 主题、分组导航和液态玻璃底栏

**Files:**
- Create: `app/src/main/java/com/cxcboss/glassorb/ui/IosGlassSurface.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/GlassOrbTheme.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/cxcboss/glassorb/ui/ConfigEditor.kt`
- Modify: `app/src/androidTest/java/com/cxcboss/glassorb/ui/MainActivityTest.kt`

**Interfaces:**
- `enum class SettingsSection { Overview, Appearance, Motion }`。
- `IosGlassCard` 和 `IosFloatingTabBar(selected: SettingsSection, onSelected: (SettingsSection) -> Unit)` 为纯 Compose 组件，不持有业务状态。

- [ ] **Step 1: 写失败测试**

  在 `MainActivityTest` 增加语义断言，启动设置页后能找到“概览”“外观”“动效”三个底栏标签；切换“外观”后能看到“玻璃”分组，切换“动效”后能看到“动画”分组。保留现有权限、预览和控件断言。

- [ ] **Step 2: 运行失败测试**

  运行：`./gradlew compileDebugAndroidTestKotlin --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache`

  预期：新底栏语义节点对应的 Compose 测试尚未实现；该命令只编译测试 APK，不启动设备。

- [ ] **Step 3: 实现主题和页面**

  `GlassOrbTheme` 使用 `isSystemInDarkTheme()` 选择固定 iOS light/dark `ColorScheme`；`MainActivity` 在 `SideEffect` 中同步状态栏/导航栏图标明暗。`IosGlassCard` 使用圆角、半透明填充、细描边、顶部高光和阴影，API 31+ 可叠加轻微 blur，API 26 保持无 blur 的透明降级。`SettingsScreen` 用 `SettingsSection` 决定卡片顺序，概览展示服务/预览/预设，外观展示几何/玻璃/暗场/波形/圆点，动效展示动画/性能/JSON/说明；底部栏使用 `navigationBars` inset 和额外底部 padding，确保最后一项不被遮挡。`ConfigEditor` 增加新 MotionConfig 参数的 iOS 风格 Slider 行。

- [ ] **Step 4: 运行通过测试**

  运行完整 JVM 测试和 `lintDebug`；检查 light/dark 编译路径、无障碍语义和底栏 inset。本轮不重新执行 APK 仪器测试套件。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/java/com/cxcboss/glassorb/ui app/src/androidTest/java/com/cxcboss/glassorb/ui
  git commit -m "feat: add iOS-style adaptive settings surface"
  ```

### Task 5: 文档、回归构建与交付包

**Files:**
- Modify: `README.md`
- Modify: `dist/测试说明.md`
- Modify: `dist/SHA256SUMS.txt`
- Replace: `dist/灵动玻璃球-demo.apk` with the newly assembled debug APK

- [ ] **Step 1: 更新文档**

  在 README 的使用和调参章节补充顶部锚点、上滑橡皮筋、极小拖拽形变、系统深浅色和底栏分类说明；在测试说明中明确本轮未重复执行 APK 仪器测试，保留用户已验证的跨应用结果，并列出当前构建命令。

- [ ] **Step 2: 运行提交前验证**

  运行：`./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache`

  预期：JVM 测试 0 failures，lint 0 errors，assembleDebug exit 0。不要把“未运行的 connectedDebugAndroidTest”写成通过。

- [ ] **Step 3: 同步 APK 和校验文件**

  将 `/tmp/glass-orb-android-build/app/outputs/apk/debug/app-debug.apk` 复制为 `dist/灵动玻璃球-demo.apk`，执行 `shasum -a 256 dist/灵动玻璃球-demo.apk`，把新哈希写入 `dist/SHA256SUMS.txt`，保留现有截图和测试说明。

- [ ] **Step 4: 检查仓库内容**

  运行：`git diff --check -- . ':(exclude)gradlew.bat'`、`git status --short --ignored` 和 `git diff --stat`；确认没有 `.gradle/`、`build/`、`.omx/`、`local.properties` 被暂存，`glass.frag` 等 Shader 只有未修改或计划内接线变化。

- [ ] **Step 5: 提交**

  ```bash
  git add README.md dist docs/superpowers/plans/2026-09-04-glass-orb-motion-ios-plan.md
  git commit -m "docs: describe elastic motion and iOS settings"
  ```

## 最终检查清单

- [ ] `testDebugUnitTest` 通过，新增数学/锚点/窗口时序测试均有结果。
- [ ] `lintDebug` 无错误，`assembleDebug` 成功，APK SHA-256 已更新。
- [ ] 玻璃 Shader 视觉公式未改，纯黑胶囊和上黑下透明表现保持。
- [ ] 展开/收起均以顶部为锚点，收起最后一帧后才缩窗，无闪烁路径。
- [ ] 拖拽形变 <=2%，橡皮筋有界，释放有回弹，多指可取消。
- [ ] 设置页 light/dark 跟随系统，底部悬浮栏三分类可达且不遮挡内容。
- [ ] 仓库没有机器本地配置、构建缓存或 `.omx` 运行记录。
