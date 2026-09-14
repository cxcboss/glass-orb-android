# 项目架构

本文描述 `2.0.28 / versionCode 38` 的实际实现。修改前应先确认代码仍与本文一致。

## 总体数据流

```text
MainActivity + NativeSettingsController
                │
                ▼
           MainViewModel
                │ 即时发布，70 ms 防抖持久化
                ▼
       OrbConfigRepository / DataStore
                │ StateFlow<OrbConfig>
                ▼
          OrbOverlayService
                │
                ▼
       OverlayWindowController
        ├─ 窗口布局与触控区域
        ├─ 状态机、弹簧与帧调度
        ├─ 系统背景模糊与下层变暗
        └─ OrbTextureView → GlRenderLoop → GlOrbPipeline
```

设置和悬浮层运行在同一应用进程。滑杆先通过 `OrbConfigRepository.publish()` 更新内存快照，让悬浮层下一帧立即响应；`MainViewModel` 再把最终值写入 DataStore，避免每个触摸采样都触发磁盘写入。

## 目录与职责

| 路径 | 职责 |
| --- | --- |
| `app/src/main/java/.../ui` | 原生设置界面、导航、交互逻辑和设置页高刷新请求 |
| `app/src/main/java/.../data` | DataStore、运行时配置发布和开机自启偏好 |
| `app/src/main/java/.../model` | 配置模型、归一化、JSON 兼容与迁移 |
| `app/src/main/java/.../overlay` | 前台服务、窗口层级、触控、旋转、模糊、全屏与助手联动 |
| `app/src/main/java/.../motion` | 无帧状态的解析弹簧、拖动形变和环境频段 |
| `app/src/main/java/.../render` | EGL/GL 渲染线程、离屏纹理、形状和动态调色板 |
| `app/src/main/assets/shaders` | GLES 3.0 顶点/片元 Shader |
| `app/src/test` | 纯逻辑与策略单元测试 |
| `app/src/androidTest` | 需要设备或模拟器的窗口、像素和界面测试 |
| `dist` | 唯一可安装 APK、哈希和交付说明 |

## 悬浮窗口层级

`OverlayWindowController.show()` 按固定顺序创建并长期复用窗口，顺序不能随意调整：

1. `BackgroundDimView`：全屏黑色渐变，仅实现“下层变暗”。
2. `BackgroundAmbientBlurController`：五条系统模糊窗口，近似从物理顶部向下递减的“下层模糊”。
3. `AndroidBackgroundBlurWindow`：单个透明圆角窗口，位于 GL 下方，实现玻璃球轮廓内的系统背景模糊。
4. `FrameLayout + OrbTextureView`：真正的灵动岛和玻璃球画面。
5. 独立触控代理 `View`：只接收当前形状对应的输入区域。

玻璃球模糊窗口会在 GL 窗口之前预创建，关闭时只隐藏并把半径归零，避免重新插入窗口后跑到 GL 上方，把球体本身一起模糊。

## 状态、动画与帧率

`OverlayStateMachine` 的主要状态是 `Hidden`、`Collapsed`、`Expanding`、`Wave`、`Thinking` 和 `Collapsing`。动画由 `AnalyticSpring` 根据时间求值，不依赖固定帧步长。

- 展开、拖动、收起和全屏隐藏/显示期间，主线程在每次 `Choreographer` 回调提交一帧。
- 所有悬浮窗口通过 `OverlayRefreshRatePolicy` 请求当前分辨率的精确 120 Hz 模式，并设置 `preferredRefreshRate`。
- `GlRenderLoop` 对 EGL `Surface` 调用 `Surface.setFrameRate(120f, ...)`。
- 全屏转场发生时，逻辑状态可能已经是 `Collapsed`；`shouldSubmitOverlayFrame()` 必须额外检查 `FullscreenCapsulePhase`，否则只会绘制转场第一帧。
- 完全收起且按压弹簧稳定后停止 `Choreographer` 和 GL 提交，保留最后一帧，进入低功耗状态。

这些 API 是向系统表达偏好，不是绕过厂商动态刷新策略的强制开关，详见 [已知问题](KNOWN_ISSUES.md)。

## 旋转与几何

界面语义始终以手机物理竖屏为基准：灵动岛固定在真实机身顶部中央，横屏时整体位置和方向通过反向坐标映射保持不变。

- `RotationCoordinateSpace`：在竖屏设计空间与当前显示物理空间之间转换点、矩形和旋转。
- `OverlayLayout`：计算收起边界、展开画布、触控边界和锚点。
- `RenderPresentationFrame`：把同一帧几何快照同时提供给窗口、输入区域、模糊和 GL，避免各层错一帧。
- `LatestFrameUpdate`：合并设置滑杆产生的高频窗口几何更新，只在下一显示帧应用最新值。

## OpenGL 管线

`OrbTextureView` 只负责 Surface 生命周期；`GlRenderLoop` 在独立线程拥有 EGL Context；`GlOrbPipeline` 完成四个阶段：

1. `effect.frag` 生成波形和粒子。
2. `palette.frag` 把动态内容缩减为调色板采样。
3. `container.frag` 合成离屏场景。
4. `glass.frag` 依据实时圆角矩形 SDF 完成折射、高光、暗部和焦散，并输出到透明 Surface。

稳定灵动岛仍由最后一帧 GL 画面表示。不要在收起状态留下半透明边缘，也不要让旧 Surface 缓冲泄漏成矩形背景。

## 三种背景效果

- 下层变暗：纯黑到透明的渐变 View，不调用模糊 API，可和任一种模糊同时使用。
- 下层模糊：五条矩形系统背景模糊窗口，模拟顶部强、底部弱的渐变模糊。
- 玻璃球模糊：一个跟随球体实时圆角轮廓的系统背景模糊窗口。

`OrbConfig.normalized()` 保证“下层模糊”和“玻璃球模糊”互斥。系统模糊只在 Android 12+ 且系统报告跨窗口模糊可用时生效。玻璃球展开/收起转场期间关闭系统模糊，完全展开后再淡入，以减少窗口几何更新对动画的影响。

## 设置与本地化

`NativeSettingsUi.kt` 使用 Material 3 Views 动态创建单栏设置页和大圆角分组卡片。首页底栏为“常用 / 详细”；详细页按配置组展示，最下方“其它”仅保留“运行权限”和“关于”。

中文是代码中的主文案；英文由 `ui()` 映射和 `values-en/strings.xml` 共同提供，跟随系统语言自动切换。新增或修改文案时必须同时检查这两处，避免局部中文残留。

## 权限与助手联动

- `SYSTEM_ALERT_WINDOW`：创建悬浮层。
- 前台服务：维持用户主动开启的悬浮效果。
- 通知权限：Android 13+ 前台服务通知。
- 开机广播：只在用户启用开机自启后启动服务。
- 无障碍服务：提供精确窗口触控区域，并读取白名单系统助手窗口的包名/类名元数据；不读取文字、不截图、不录音、不执行手势。

Android 12+ 当前启动流程要求无障碍窗口上下文。修改权限流程前必须在 Android 10/11 与 Android 12+ 分别验证。
