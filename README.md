# 灵动玻璃球 · Android UI Demo

纯黑灵动岛胶囊，点击舒缓展开为透明玻璃球。Kotlin / Compose 设置页，TextureView / EGL / OpenGL ES 3.0 悬浮渲染，无 WebView。

## 1.5.0-demo

本版重构 App 内设置页：采用 iOS 26 设置层级、系统浅深色、不透明分组与居中二级标题。移除底栏、底部参数弹层、内置 GLES 实时预览和彩色背景；设置页不再持续提交渲染帧。导航采用约 360ms 横向过渡，返回按钮与 Android 系统返回共享导航栈，根页交由系统退出；当前使用普通系统返回，未提供预测返回进度预览。

首页依次为悬浮球、外观、交互、参数、关于。总开关仅在悬浮球正在显示时开启：无权限时进入授权，已隐藏时显示，其余状态启动；关闭时隐藏。启动、显示、隐藏和停止操作均可在「运行与权限」详情页使用，停止需确认。

七个参数分组使用独立 LazyColumn 页面，保留全部参数范围、单位、精度、位置预设和依赖显示。每条滑杆显示默认值标记，偏离默认值时显示「还原」；分组与全部恢复均需确认。触摸区域倍率调节期间仍显示悬浮层红框，触发使用显式布尔标记，不依赖界面文案。

Liquid Glass 仅用于导航、主要操作、滑块头与列表开关。沿用 AndroidLiquidGlass / Backdrop 2.0.1 的 lens、高光、阻尼及拉伸；本版修补最新状态/回调同步、整条 44dp 滑杆触摸区、取消/RTL、51×31dp 开关和 44dp 按钮。分组内容不使用玻璃卡片。未引入 Apple 字体或 SF Symbols。

玻璃球模型、数据、悬浮窗、运动和渲染代码及 shader 本版均未改动，配置 schema 与默认值保持原样。覆盖安装保留现有参数。版本为 versionCode 6 / versionName 1.5.0-demo。

## 安装与使用

1. 安装 `dist/灵动玻璃球-demo.apk`，要求 Android 8.0 / API 26 及以上、GLES 3.0。
2. 打开 App 并开启「显示悬浮球」；首次进入系统授权页，允许「显示在其他应用上层」后返回，再开启开关。
3. Android 13 及以上建议允许通知，以便使用显示、隐藏、停止动作。
4. 回到桌面或普通 App：点击胶囊展开；单击小球短暂切换思考圆点；上滑小球收回胶囊。

上滑超过 64dp 或速度超过 800dp/s 收起，否则弹回。窗口以外区域可操作下层应用；球体周围的小型透明余量仍属于触摸窗口。位置只能通过设置修改。

## 调参

通过系统悬浮球观察修改效果，App 内不再提供实时预览。

| 分组 | 可调整内容 |
| --- | --- |
| 胶囊与位置 | 胶囊宽高、球径、余量、画布比例、顶部/水平偏移、左/中/右位置、触控扩展与倍率 |
| 玻璃 | 内部深度、曲率、高光、阴影、焦散及柔度 |
| 暗场 | 强度、纯黑区域、渐隐跨度、高斯斜率 |
| 波形 | 振幅、尺度、色散、线宽、亮度、填光及厚度、柔化、Bloom、色相 |
| 思考圆点 | 环半径、点半径、辉光、转速 |
| 动画 | 手势阻力、微形变、展开/收起弹簧、呼吸、按压、思考时间、自动收起与倒计时 |
| 性能 | 胶囊/展开帧率、渲染比例 |

提供「参考原版 / 柔和 / 明亮」预设，应用前确认替换全部参数。「导入与导出」是全屏表单：复制 JSON 备份，再粘贴并导入；成功/复制采用 App 内提示，错误显示在表单中。schemaVersion=1，缺失字段用默认值，未知字段忽略，越界值夹紧；损坏 JSON 不覆盖现有配置。参数通过 DataStore 本地保存。

## 效果边界

- 胶囊稳定状态为纯黑色；玻璃球使用参考的光谱波形、双点、超椭圆 SDF、解析弹簧、薄白高光和高斯暗场公式，上部暗、下部透明。
- 不读取屏幕、不录屏，折射仅作用于内部生成场景；下层内容透过透明区域原样显示。外部光影以 Android 预乘 Alpha 近似合成，不保证逐像素一致。
- 锁屏、安全页面、状态栏、输入法等系统关键窗口不保证覆盖；部分 App 会阻止悬浮窗。物理顶边被系统状态栏挡住的触摸高度会补到下方，红框显示实际触控窗口。
- 无联网、麦克风、录屏、无障碍或存储权限，没有语音助手、开机启动或后台录音。
- debug 签名非商业测试包，不是商店发布包。后台省电策略因厂商不同；服务被回收时，在前台设置页重新开启。

## 本地构建

AGP 9.4.0、Gradle 9.6.0、AGP 内置 Kotlin、Compose 编译插件 2.4.10、Compose BOM 2026.06.01。compileSdk 37 / targetSdk 36 / minSdk 26；Java 17 字节码，当前主机使用 JDK 21。

在 `local.properties` 设置 `sdk.dir` 后执行：

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
./gradlew compileDebugAndroidTestKotlin --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

本版按要求未运行模拟器或实体机测试。导航、总开关、坐标映射与默认值逻辑有 JVM 测试；Android 界面测试更新并编译，尚未 connected 运行。120Hz 跟随 Compose/Choreographer，未添加 60fps 限制；实际刷新率与手势观感需目标设备验证。

生成物位于 Java 临时目录的 `glass-orb-android-build/app`，以避免 Desktop 同步目录的冲突副本。APK 为其 `outputs/apk/debug/app-debug.apk`，交付副本与 SHA-256 位于 `dist/`。

架构：`OrbConfig` → DataStore `OrbConfigRepository` → `RenderSnapshot` → `OrbTextureView` / GL；`OverlayWindowController` 管理窗口与手势，`OrbOverlayService` 管理服务。新设置导航与纯逻辑位于 `ui/SettingsUiLogic.kt`。

## 参考与许可

效果参考：[glass-voice-orb-study](https://github.com/cxcboss/glass-voice-orb-study)，固定提交 `3d7e98199b385358df6ddf65a9d20644754f22eb`；[在线演示](https://zq52xy.github.io/glass-voice-orb-study/)。归属和非商业要求见 `NOTICE.md`、`LICENSE`。

设置控件基于 Apache-2.0 的 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)，固定提交 `65ab177e90e5c1d8c62e70cf7755841982da65f6`，依赖 Backdrop 2.0.1 / Shapes 1.2.1；具体修改与许可见 `THIRD_PARTY_NOTICES.md`。底栏源码仅作为未使用的第三方文件保留，不进入界面运行路径。

参考仓库只在临时目录分析，没有修改或提交；`tools/port-reference-shaders.mjs <参考目录>` 可重现固定提交 shader 的机械移植。
