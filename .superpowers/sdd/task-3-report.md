# Task 3：可选 Android 12+ 局部背景模糊报告

## 状态

已完成实现并通过 focused/full debug JVM 单元测试。实现基于当前基线 `81a5310`，未修改 rotation geometry、dynamic palette shader 或 performance refactor。

## 实现范围

- `ContainerConfig` 新增 `backgroundBlurEnabled`，默认值为 `false`。
- `OrbConfigJson` 对该字段进行 encode/decode；缺失字段按旧配置迁移为关闭，normalized 配置继续使用当前 schema。
- Native 设置页“背景与暗部”加入“背景局部模糊”原生开关。开关通过现有 ViewModel/DataStore 链路立即发布，页面更新保留当前 ScrollView 的 scrollY，不新增权限。
- 新增 `BackgroundBlurPolicy`：仅 API 31+、用户开启、`WindowManager.isCrossWindowBlurEnabled == true`、可见展开态、圆形且有效的 exact orb bounds 才允许 blur；独立 `orbWidthDp != orbHeightDp` 直接标记为 ellipse 并只关闭 blur。
- 新增可测试的 `BackgroundBlurController` 与 `BackgroundBlurWindow` seam，更新无变化时不重复调用；隐藏、销毁和更新失败均安全幂等，失败只丢弃可选 blur 层。
- 新增 `AndroidBackgroundBlurWindow`：使用真实透明 Dialog/Window，依据现有 accessibility/application overlay 上下文设置窗口类型；flags 为 `NOT_FOCUSABLE | NOT_TOUCHABLE | LAYOUT_IN_SCREEN`，透明圆形 background，清除 dim，不使用 `FLAG_BLUR_BEHIND` 或隐藏 API。
- Overlay 的 blur controller 与既有 GL host/dim/touch 生命周期绑定；只有请求合格时才创建可选 Dialog。展开/收起、shape、rotation/configuration、fullscreen visibility、screen on/off、capability listener 和 destroy 均通过同一 controller 更新或清理。

## TDD 与验证

先加入测试并运行 focused 命令，确认缺少配置字段、策略和 controller seam 的预期编译失败；随后加入最小实现，focused 测试转绿。

Focused：

```text
./gradlew testDebugUnitTest --tests 'com.cxcboss.glassorb.model.OrbConfigTest' --tests 'com.cxcboss.glassorb.overlay.BackgroundBlurPolicyTest' --tests 'com.cxcboss.glassorb.ui.SettingsUiLogicTest' --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
BUILD SUCCESSFUL
```

Full：

```text
./gradlew testDebugUnitTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
BUILD SUCCESSFUL
```

Full 结果 XML 显示 20 个 test suites、102 个 tests，failures/errors 均为 0。构建输出只有当前工程已有的 `Display.getRealSize`、`statusBarColor`、`navigationBarColor` deprecated warnings。

## 设备验证与剩余顾虑

当前环境没有可用 Android 真机/模拟器用于验证 SurfaceFlinger 的实际跨窗口模糊效果，因此未宣称真机视觉验收完成。真实设备仍需确认：设备/厂商是否开启 cross-window blur、Dialog overlay 类型是否被 OEM 接受、透明圆形 background 是否按预期成为 blur mask，以及 capability 动态开关时是否无残留。设备不支持或上述任一运行时步骤失败时，代码会保留既有 GL/dim/touch 效果，仅关闭背景 blur。

## 提交

待本报告写入后创建本 Task 3 专用提交。

## 复审修复（2026-09-12）

复审指出的生命周期问题已修复。此前 `OverlayWindowController.show()` 无条件调用 `BackgroundBlurController.show(IntRect)`，即使 collapsed、默认关闭、API<31、capability 关闭或椭圆状态也会创建透明 Dialog。现在 controller 的 `show/update` 都接受 `BackgroundBlurRequest` 并先调用纯 `BackgroundBlurPolicy.isEligible()`：

- 不合格初始 request 直接返回，不调用 `BackgroundBlurWindow.show`，因此不创建/attach 透明层。
- 合格的 user-enabled、API31+、cross-window blur、expanded visible、circular、valid square bounds 才 attach，并直接应用固定 blur radius。
- 已 attach 后若开关关闭、capability 关闭、进入 fullscreen/screen-off、变成 ellipse 或 bounds 无效，立即 `hide()` + `destroy()`；恢复合格条件时允许重新 attach。
- 未改变既有 GL host、dim window 或 touch window；Dialog 创建/更新失败只 detach 可选 blur 层。

本次 focused 实际输出：

```text
./gradlew testDebugUnitTest --tests 'com.cxcboss.glassorb.overlay.BackgroundBlurPolicyTest' --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
BUILD SUCCESSFUL in 44s
31 actionable tasks: 31 executed
```

本次 full 实际输出：

```text
./gradlew testDebugUnitTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
BUILD SUCCESSFUL in 44s
31 actionable tasks: 31 executed
```

两次输出均只有既有 `Display.getRealSize`、`statusBarColor`、`navigationBarColor` deprecated warnings，无编译错误或测试失败。真机 SurfaceFlinger blur 视觉验证仍未完成。
