# 对接文档（接手 / 集成指南）

给下一位维护者（人或 AI）的单一入口：先读本文，再按需要跳转到其它文档。

## 1. 当前基线

| 项目 | 值 |
| --- | --- |
| 应用 | 灵动玻璃球（Android 悬浮灵动岛 + OpenGL ES 3.0 玻璃球） |
| 版本 | `2.0.29` / `versionCode 39` |
| 包名 | `com.cxcboss.glassorb` |
| SDK | `minSdk 29`、`targetSdk 30`（刻意保留）、`compileSdk 37` |
| 分支 | `main`（默认、已发布）；`codex/independent-glass-orb` 已合入 `main` |
| 交付物 | [GitHub Release v2.0.29](https://github.com/cxcboss/glass-orb-android/releases/tag/v2.0.29) 附件 `glass-orb-android-2.0.29.apk`（Debug 签名侧载包，约 10 MB）；APK 不入库 |
| UI | 平台 View 手写的 iOS 26 风格设置页 + 液态玻璃外壳；**无 Material、无 Compose** |
| 渲染 | OpenGL ES 3.0 透明悬浮 Surface，活跃阶段请求 120 Hz |
| 许可性质 | 非商业学习用途，见 `LICENSE`、`NOTICE.md`、`THIRD_PARTY_NOTICES.md` |

## 2. 十分钟上手

环境：JDK 17+（本机用 Temurin 21 验证通过）、Android SDK Platform 37、仓库自带 Gradle Wrapper（9.6）。

```sh
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # 已有则跳过
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew assembleDebug --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

- 构建目录被 `build.gradle.kts` 重定向到系统临时目录（macOS：`$TMPDIR/glass-orb-android-build`），**不要**去 `app/build` 找 APK。查询方式见 [构建与发布](BUILD_AND_RELEASE.md)。
- 安装：从 Release 下载 `glass-orb-android-2.0.29.apk` 后 `adb install -r glass-orb-android-2.0.29.apk`。
- 首次启动：设置 → 运行与权限，依次允许悬浮窗权限和无障碍触控服务（Android 12+ 必需），回到根页打开“显示灵动岛”。
- 没有真机时，设置页可在任意 API 29+ 模拟器直接预览（悬浮层功能需要权限与 GLES 3.0）。

## 3. 代码地图

| 路径（`app/src/main/java/com/cxcboss/glassorb/`） | 职责 |
| --- | --- |
| `ui/MainActivity.kt` | 单 Activity；边到边、权限/服务调用、预测性返回接线 |
| `ui/NativeSettingsUi.kt` | 设置页控制器：页面栈、导航、全部设置内容 |
| `ui/SettingsText.kt` | 中文源文案 → 英文映射（跟随系统语言） |
| `ui/SettingsUiLogic.kt` | 纯逻辑：主开关动作、滑杆吸附/手势轴、数值格式化（有单测） |
| `ui/SettingsRefreshRateController.kt` | 设置页滚动时请求最高刷新率 |
| `ui/ios/*` | iOS 控件与行构建器：`IosRows`、`IosSwitch`、`IosSlider`、`IosSegmentedControl`、`IosPalette`、`UiSpring` |
| `ui/glass/*` | 液态玻璃：`LiquidGlassView`、`GlassBackdrop`、`GlassNavBar`（滚动边缘）、`GlassSearchBar`、`GlassContextMenu`、`GlassAlert`、AGSL 镜头 |
| `data/OrbConfigRepository.kt` | 配置唯一来源（内存快照 + 防抖落盘） |
| `model/OrbConfig*.kt` | 配置模型、范围归一化、JSON 编解码与迁移 |
| `overlay/OrbOverlayService.kt` | 前台服务：生命周期、通知、命令入口 |
| `overlay/OverlayWindowController.kt` | 悬浮窗口栈、状态机、弹簧、帧调度、触控、全屏/旋转 |
| `overlay/*Blur*`、`BackgroundDimView` | 下层模糊 / 玻璃球模糊 / 下层变暗 |
| `overlay/GlassOrbAccessibilityService.kt`、`AccessibilityOverlayBridge.kt` | 可信触控窗口与系统助手窗口联动 |
| `render/*`、`assets/shaders/*` | EGL 线程、四阶段 GL 管线、GLSL |
| `motion/*` | 无帧状态的解析弹簧、拖动形变、环境频段 |

数据流和窗口层级见 [项目架构](ARCHITECTURE.md)。设置页的设计系统见 [设置页设计说明](SETTINGS_UI.md)。

## 4. 对接契约（对内接口）

应用没有对外导出的 API：服务 `exported=false`，只有进程内代码和通知按钮能驱动它。

### 4.1 悬浮服务命令

`OrbOverlayService` 通过 Intent action 接收命令，推荐使用伴生对象的辅助函数：

| 辅助函数 | Action 常量 | 行为 |
| --- | --- | --- |
| `start(ctx)` | `ACTION_START`（Intent 无 action 时的默认值） | 前台启动服务并显示悬浮层 |
| `show(ctx)` | `ACTION_SHOW` | 显示已隐藏的悬浮层 |
| `hide(ctx)` | `ACTION_HIDE` | 移除窗口，服务与通知保留 |
| `stop(ctx)` | `ACTION_STOP` | 停止服务并清除前台通知 |
| `setAppAppearance(ctx, active, dark)` | `ACTION_APP_APPEARANCE`（extras `app_active`、`dark_mode`） | 设置页前台时给胶囊加深色描边 |
| `setTouchPreview(ctx, visible)` | `ACTION_TOUCH_PREVIEW`（extra `visible`） | 调触控倍率时显示红色触控框 |

`APP_APPEARANCE` / `TOUCH_PREVIEW` 只调整正在运行的悬浮层：服务若不是前台状态会直接 `stopSelf`，不会留下空通知。

### 4.2 运行状态

`OverlayRuntime.status : StateFlow<OverlayRuntimeStatus>`，取值 `Stopped`、`Visible`、`Hidden`、`PermissionRequired`、`Error(message)`。设置页的“显示灵动岛”开关以它为准（见 `resolveOverlayAction`）。

### 4.3 配置

```kotlin
val repository = OrbConfigRepository.getInstance(context)
repository.config                      // StateFlow<OrbConfig>，悬浮层与设置页共用
repository.publishAndPersist(config)   // 立即发布 + 70 ms 防抖落盘（设置页滑杆用这个）
repository.replace(config)             // 发布并立即落盘（挂起函数）
repository.update { it.copy(...) }     // 基于当前值修改并落盘
repository.reset(ConfigGroup.Glass)    // 恢复某一组默认值
```

- 存储：DataStore `orb_config`，键 `config_json_v1`，内容为 `OrbConfigJson` 编码的 JSON，`schemaVersion = 3`。
- 所有范围、互斥关系都在 `OrbConfig.normalized()`：发布、落盘、悬浮层消费前都会归一化，不要在别处重复写范围。
- DataStore 只在进程启动时读取一次，之后内存快照是唯一真相（本进程是唯一写入者）。
- “下层模糊”与“玻璃球模糊”互斥，由 `normalized()` 保证；“下层变暗”独立。

### 4.4 无障碍桥与系统助手联动

- `AccessibilityOverlayBridge` 是服务与悬浮层之间的进程内桥：`attachController(...)` 注册触控转发、触控边界提供者、连接变化与助手窗口回调。
- 只读取白名单窗口的**包名 / 类名元数据**，不读文字、不截图、不执行手势。白名单在 `SystemAssistantPackages`。
- 新增厂商助手：把包名加入白名单，并在目标 ROM 上验证“出现”和“消失”两条路径；不要扩大读取范围。

### 4.5 开机自启

`AutoStartPreferences`（SharedPreferences `runtime_preferences`，键 `auto_start_enabled`）+ `BootReceiver` + `AutoStartPolicy`。设置入口：设置 → 运行与权限 → 开机自动启动。需要悬浮窗权限；Android 12+ 还需要无障碍服务已开启。

### 4.6 权限清单

| 权限 / 能力 | 用途 |
| --- | --- |
| `SYSTEM_ALERT_WINDOW` | 绘制悬浮层 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 用户主动开启的常驻视觉工具 |
| `POST_NOTIFICATIONS`（Android 13+） | 前台服务通知 |
| `RECEIVE_BOOT_COMPLETED` | 用户启用开机自启后恢复悬浮层 |
| 无障碍服务 | 精确触控窗口 + 白名单助手窗口元数据 |

## 5. 常见改动配方

### 5.1 新增一个可调参数

1. `model/OrbConfig.kt`：在对应分组数据类加字段（带默认值），并在 `normalized()` 补范围。
2. `model/OrbConfigJson.kt`：`encode` 与 `decode` 各加一行；旧 JSON 缺字段时必须回落到默认值。
3. `ui/NativeSettingsUi.kt`：在对应 `buildXxx` 的某个 `section { ... }` 里加 `sliderRow(...)` 或 `switchRow(...)`，回调里用 `updateConfig { it.copy(...) }`。
4. `ui/SettingsText.kt`：补英文映射（新增中文文案漏映射时英文系统会出现中文）。
5. 渲染侧消费：`render/GlOrbPipeline.kt` 设 uniform，或 `overlay/*` 里读取 `config`。
6. 补/更新 `OrbConfigTest`；若改了 JSON 结构，升级 `CURRENT_SCHEMA_VERSION` 并写迁移。

### 5.2 新增设置页面

在 `NativeSettingsController` 的 `Screen` 密封接口加一项 → `createPage` 的 `when` 加分支 → 写 `buildXxx(page)`（以 `rows.headerCard` 开头）→ 补 `screenTitle`、`screenKey`、`decodeScreen`（状态恢复），并在根页加入口行；如需被搜索到，把它加入 `buildSearchIndex` 的页面列表。需要随运行状态变化的行，把刷新闭包追加到 `page.refreshers`，不要重建页面。

### 5.3 调整玻璃观感

集中在 `ios/IosPalette.kt`（颜色）与 `glass/LiquidGlassView.kt`（模糊、色调、边缘光、阴影、镜头强度）。每个玻璃表面可单独设置 `blurDp`、`lensDp`、`bezelDp`、`dispersion`、`glassAlpha`。

### 5.4 动画 / 旋转 / 模糊 / 刷新率

先读 [项目架构](ARCHITECTURE.md) 的对应章节和下面的约束；改动后必须真机验收，见第 7 节。

## 6. 绝不能无意破坏的约束

悬浮层（沿用并确认仍然成立）：

1. 灵动岛以**真实手机竖屏顶部**为固定语义；旋转时只转换物理坐标，图形方向不跟着转。
2. 活跃动画（展开/收起/拖动/全屏转场）每个 VSync 必须提交；完全收起且稳定后必须停止持续渲染。
3. 全屏转场期间逻辑状态可能已是 `Collapsed`，渲染判断必须同时检查 `FullscreenCapsulePhase`。
4. 系统玻璃球模糊窗口必须先于 GL 窗口创建并保持 z-order；下层模糊的五条窗口同理，**创建失败后不得再重试**（晚插入会跑到 GL 上方，把球体本身模糊掉）。
5. “下层模糊”与“玻璃球模糊”互斥；“下层变暗”只画颜色。
6. 精确触控区域只覆盖当前形状，不得用整块透明画布拦截下层应用。
7. 无障碍服务只读白名单窗口包名/类名元数据。
8. 滑杆先发布内存配置、再防抖落盘；不要让磁盘 IO 阻塞手指跟随。
9. 许可、固定参考提交、应用内声明必须保留；不得改成更宽松的许可。

设置页（新增）：

10. **玻璃“背景源”（`BackdropHostLayout` 子树）里不能放任何已注册的玻璃视图**，否则捕获背景会无限递归。导航栏、搜索框、底部边缘、上下文菜单都在背景源之外。
11. 玻璃刷新依赖 `BackdropHostLayout.onDescendantInvalidated`（API 28+，同帧生效）；不要改成在 `onDraw` 里调 `invalidate()`，会造成逐帧重绘循环。
12. 玻璃降级必须保持可用：API 31 以下只画半透明纯色，API 31–32 只有模糊，API 33+ 才有 AGSL 边缘镜头；镜头着色器创建/编译失败要吞掉并回落到模糊（已实现）。
13. 开关/滑杆等状态变化**就地更新**，不要因运行状态变化重建整页（会打断按压反馈和动画）；只有“恢复本组默认值”这类批量变更才重建当前页并保持滚动位置。
14. 整行可点，最小触控高度约 48 dp；长说明可换行，不能被裁出屏幕。
15. 滑杆行必须保留恢复默认的入口（长按行 → 玻璃菜单）、无障碍 `RangeInfo` 与 `ACTION_SET_PROGRESS`。
16. 性能优化**不得降低帧率**：活跃动画保持每个 VSync 提交、120 Hz 请求不变；只允许减少每帧的冗余工作。

## 7. 交付检查表

- [ ] `assembleDebug` 通过；`lintDebug` 无 error。
- [ ] `testDebugUnitTest`、`compileDebugAndroidTestKotlin`（如需，设备上跑 `connectedDebugAndroidTest`）。
- [ ] APK 包名、版本号、签名（Debug）和 SHA-256 已核验；APK 作为 Release 附件上传，`dist/SHA256SUMS.txt` 记录其哈希；仓库里不提交 APK。
- [ ] 中文与英文设置页没有裁剪、遗漏、局部中文。
- [ ] 浅色 / 深色模式各看一遍首页、详细页、子页、对话框。
- [ ] 若动了动画/旋转/模糊：列出尚未完成的真机验收项（见 [已知问题](KNOWN_ISSUES.md)）。
- [ ] 若动了权限/助手联动：复查 Manifest、无障碍 XML、数据使用说明。
- [ ] `NOTICE.md`、`THIRD_PARTY_NOTICES.md`、`LICENSE`、`app/src/main/assets/NOTICE.txt` 与实际实现一致。

## 8. 2.0.29 验证状态（诚实版）

已做：`assembleDebug`、`lintDebug`（0 error）、`compileDebugUnitTestKotlin`、`compileDebugAndroidTestKotlin` 均通过；在 API 35 模拟器上对最终设置页做了冒烟检查（中/英文、浅色/深色、根列表、二级页头卡与内联标题、滚动边缘、长按恢复菜单、底部搜索及跳转、恢复默认确认框），未发现崩溃。API 29 降级路径在上一版界面上验证过，最终版的结构改动未在 API 29 上重跑。

**未做**：单元测试与设备测试未运行（本次明确不要求）；未在真机（OPPO/ColorOS、小米/HyperOS 等）验证；未在模拟器上打开悬浮层（需要悬浮窗与无障碍授权），因此悬浮层本身的改动（模糊控制器逐帧分配优化、`show()` 失败清理、服务命令门控）只做了编译与代码审查。AGSL 边缘镜头在 API 33+ 真机上的观感与帧率也需要实际确认。

## 9. 后续路线

完整的缺陷清单、性能项和改进计划见 [审计与改进计划](AUDIT.md)。优先级最高的三件事：

1. 真机验收悬浮层（四向旋转、全屏进出、模糊开关）与新设置页的玻璃性能。
2. 评估无障碍服务的 `typeWindowContentChanged` 订阅成本，再决定是否收窄。
3. 为 `OrbConfigRepository`（防抖落盘、启动读取）补单元测试。

## 10. 文档索引

- [项目架构](ARCHITECTURE.md) · [设置页设计说明](SETTINGS_UI.md) · [构建与发布](BUILD_AND_RELEASE.md)
- [已知问题](KNOWN_ISSUES.md) · [审计与改进计划](AUDIT.md) · [更新日志](CHANGELOG.md)
- [仓库与目录管理](REPOSITORY.md)（目录、清理、分支与发版）
- [iOS 27 Siri 视觉体验推广研究](research/ios27-siri-promotion-research.md)
