# 灵动玻璃球 · Android UI Demo

纯黑灵动岛胶囊，点击舒缓展开为透明玻璃球。原生 Kotlin / Compose 设置页，TextureView / EGL / OpenGL ES 3.0 悬浮渲染，无 WebView。

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
| 动画 | 展开/收回弹簧、负向回弹、波形延迟、呼吸、点击反馈、圆点持续时间 |
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

工具链：AGP 9.4.0、Gradle 9.6.0、AGP 内置 Kotlin、Compose 编译插件 2.4.10、Compose BOM 2026.06.01、compile/targetSdk 36、Java 17 字节码。当前主机使用 JDK 21 运行 Gradle。

在 `local.properties` 中设置自己的 `sdk.dir`，随后执行：

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
./gradlew connectedDebugAndroidTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

本机 Desktop 文件同步目录曾对生成物创建 `… 2.class` 冲突副本，因此根构建脚本将可再生输出放在 Java 临时目录的 `glass-orb-android-build/app` 下。源码仍全部位于本目录。APK 位于该构建目录的 `outputs/apk/debug/app-debug.apk`。单元测试和 Android 测试报告也在该目录下。

架构入口：`OrbConfig` → DataStore `OrbConfigRepository` → 不可变 `RenderSnapshot` → `OrbTextureView` / GL 渲染线程；`OverlayWindowController` 管理窗口和手势，`OrbOverlayService` 管理前台服务。

## 参考与许可

参考：[glass-voice-orb-study](https://github.com/cxcboss/glass-voice-orb-study)，固定提交 `3d7e98199b385358df6ddf65a9d20644754f22eb`；[在线演示](https://zq52xy.github.io/glass-voice-orb-study/)。详细归属和非商业要求见 `NOTICE.md`、`LICENSE`。

参考仓库只在临时目录分析，没有修改、提交或推送。本项目只移植必要的 shader / 动画公式，没有整包复制参考项目或其背景素材。`tools/port-reference-shaders.mjs <参考目录>` 可对固定提交重现 shader 的机械移植，使用 `apply_patch` 写入本地 Android shader。
