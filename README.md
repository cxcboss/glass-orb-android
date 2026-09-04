# 灵动玻璃球 · Android UI Demo

纯黑灵动岛胶囊，点击舒缓展开为透明玻璃球。原生 Kotlin / Compose 设置页，TextureView / EGL / OpenGL ES 3.0 悬浮渲染，无 WebView。

### 1.3.0-demo 更新

- 顶部位置使用物理显示坐标，并关闭系统窗口 Insets 自动避让；`0dp` 可进入状态栏／刘海所在的真实屏幕顶边区域。
- 拖动触摸区域倍率时，悬浮层以红色边框实时显示实际触摸窗口。
- 二级设置页向下退出；可拖动顶部把手下滑关闭，未超过阈值时弹回。
- 直接集成 AndroidLiquidGlass 的 Backdrop 2.0.1 渲染管线，卡片、按钮、滑杆、开关和底栏采用其 blur、lens、vibrancy、高光、内阴影与阻尼缩放结构。

- 保留原有玻璃材质与 shader；展开和收起以顶部为锚点。
- 上滑连续压缩形状，松手衔接当前速度；收起允许轻微负向回弹。
- 横向／向下拖动采用渐增阻力，位移最多 8dp、形变最多 2%，松手弹回，不改变保存的位置。
- 胶囊与球体共用一块固定透明渲染画布，动画不再触发 TextureView / EGL 换尺寸；独立透明触摸窗口只覆盖当前可交互形状，避免展开、收起跳帧和大矩形拦截下层应用。
- 胶囊与展开动画默认 120fps；移除 GL 线程的重复定时渲染，设置页预览限制为 60fps，降低主界面 GPU 压力。
- 胶囊宽度最小可与高度一致，顶部偏移 0 可贴齐屏幕顶边；可选 1–3 倍胶囊触摸区域。
- 增加自动收起开关和 1–60 秒倒计时。深色模式打开设置 App 时，收起胶囊显示细白色定位边缘，离开 App 或展开后消失。
- 所有参数分类改为自下而上的二级叠层页；每条滑杆显示默认值定位点和独立复位按钮。
- 设置页采用 AndroidLiquidGlass 示例的胶囊、高光、按压膨胀、阻尼滑动思路，包含可拖动的悬浮分类底栏。

覆盖安装保留旧参数。旧配置中的帧率也会保留；要采用 120fps 默认值，请在「性能」二级页点击全部复位，不会影响玻璃参数。

## 安装与使用

1. 将 `dist/灵动玻璃球-demo.apk` 发送到 Android 手机（Android 8.0 / API 26 及以上，支持 GLES 3.0）。
2. 从文件管理器打开 APK，按系统提示允许该安装来源。
3. 打开“灵动玻璃球”，点击“授权并返回”，为本应用开启“显示在其他应用上层”。
4. 返回应用，点击“启动悬浮层”。Android 13 及以上建议允许通知，以便使用显示、隐藏、停止动作。
5. 返回桌面或打开普通 App：胶囊继续显示。点击胶囊展开；单击小球短暂切换思考圆点；上滑小球收回胶囊。

上滑超过 64dp 或上滑速度超过 800dp/s 收起，否则弹回。触摸悬浮窗口以外的区域可正常操作下层界面。球体外部的透明余量仍属于小型悬浮窗口的矩形触控范围。

## 调参

设置页预览与系统悬浮层共用同一个 GLES 渲染器。可切换棋盘、纯白、深色背景，直接检查下半球透明度。

| 分组 | 可调整内容 |
| --- | --- |
| 胶囊与位置 | 胶囊宽高、球径、余量、效果比例、顶部/水平偏移、左/中/右位置 |
| 玻璃 | 内部场景扭曲深度、曲率、白色高光、阴影和暖色焦散 |
| 暗场 | 顶部暗度、黑色区域高度、下半部渐隐跨度和高斯陡度 |
| 波形 | 振幅、尺度、色散、线宽、亮度、填光、柔化、Bloom、色相 |
| 思考圆点 | 六组双点的环径、点径、辉光、转速 |
| 动画 | 展开/收回弹簧、负向回弹、手势阻力、微形变限幅与回弹、波形延迟、呼吸、点击反馈、圆点持续时间 |
| 性能 | 胶囊/球体帧率、内部渲染分辨率 |

提供“参考原版 / 柔和 / 明亮”预设；各组可独立重置。“全部恢复”回到参考默认值。位置仅通过设置页修改，不拖动胶囊定位。

“复制 JSON”导出参数；“导入 JSON”可粘贴参数。`schemaVersion=1`，缺失字段取默认值，未知字段忽略，越界值夹紧；损坏 JSON 不覆盖现有配置。应用本地保存参数。

## 效果边界

- 胶囊是纯黑色，没有波形、高光或彩色描边。
- 玻璃球使用参考源码的光谱波形、双点、超椭圆 SDF、解析弹簧、薄白高光和高斯暗场公式；上部暗、下部透明。
- **不读取屏幕、不录屏，不实现下层内容折射。** 折射仅作用于内部生成的波形/暗场场景。下层 App 内容透过透明区域原样显示，外部光影以 Android 预乘 Alpha 近似合成，因此不保证逐像素一致。
- 只承诺桌面和普通应用之上的展示；锁屏、权限/安全页面、状态栏、输入法等系统关键窗口不保证覆盖。部分应用可以主动阻止其他应用的悬浮窗。
- 无联网、麦克风、录屏、无障碍和存储权限。没有语音助手功能、开机启动或后台录音。
- 这是 debug 签名的非商业测试包，不是应用商店发布包。后台省电策略因手机厂商而异；如被回收，回设置页重新启动。

## 本地构建

工具链：AGP 9.4.0、Gradle 9.6.0、AGP 内置 Kotlin、Compose 编译插件 2.4.10、Compose BOM 2026.06.01、compileSdk 37、targetSdk 36、Java 17 字节码。当前主机使用 JDK 21 运行 Gradle。compileSdk 37 是 Backdrop 2.0.1 的 AAR 要求，不改变 Android 8.0 的最低安装版本。

在 `local.properties` 中设置自己的 `sdk.dir`，随后执行：

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
./gradlew connectedDebugAndroidTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

本机 Desktop 文件同步目录曾对生成物创建 `… 2.class` 冲突副本，因此根构建脚本将可再生输出放在 Java 临时目录的 `glass-orb-android-build/app` 下。源码仍全部位于本目录。APK 位于该构建目录的 `outputs/apk/debug/app-debug.apk`。单元测试和 Android 测试报告也在该目录下。

架构入口：`OrbConfig` → DataStore `OrbConfigRepository` → 不可变 `RenderSnapshot` → `OrbTextureView` / GL 渲染线程；`OverlayWindowController` 管理窗口和手势，`OrbOverlayService` 管理前台服务。

## 参考与许可

参考：[glass-voice-orb-study](https://github.com/cxcboss/glass-voice-orb-study)，固定提交 `3d7e98199b385358df6ddf65a9d20644754f22eb`；[在线演示](https://zq52xy.github.io/glass-voice-orb-study/)。详细归属和非商业要求见 `NOTICE.md`、`LICENSE`。

设置页表面、底栏、滑杆及拖动阻力严格参考 Apache-2.0 的 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（分析固定提交 `65ab177e90e5c1d8c62e70cf7755841982da65f6`），并直接依赖其 Backdrop 2.0.1 核心渲染库。高层控件依据仓库示例在本项目内适配，以保留现有 Android 工程与中文设置结构。

参考仓库只在临时目录分析，没有修改、提交或推送。本项目只移植必要的 shader / 动画公式，没有整包复制参考项目或其背景素材。`tools/port-reference-shaders.mjs <参考目录>` 可对固定提交重现 shader 的机械移植，使用 `apply_patch` 写入本地 Android shader。
