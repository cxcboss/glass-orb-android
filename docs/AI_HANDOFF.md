# AI 接手说明

## 当前基线

- 当前开发分支：`codex/independent-glass-orb`
- 应用版本：`2.0.28` / `versionCode 38`
- 包名：`com.cxcboss.glassorb`
- 唯一交付包：`dist/灵动玻璃球-2.0.28.apk`
- UI：原生 Android Views + Material 3，不使用 Compose。
- 渲染：OpenGL ES 3.0，透明悬浮 Surface，活跃阶段请求 120 Hz。
- 发布性质：非商业学习用途、Debug 签名侧载包。

开始工作时先执行：

```sh
git status --short --branch
git log -5 --oneline
./gradlew properties --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache \
  | rg 'buildDir|buildDirectory'
```

不要假定 `app/build` 是最新构建目录，也不要删除或覆盖用户未提交的改动。

## 绝不能无意破坏的约束

1. 灵动岛以真实手机竖屏顶部为固定语义。屏幕旋转时只转换物理坐标，图形方向不能跟着横屏旋转。
2. 活跃玻璃球、展开/收起和全屏隐藏/显示必须在每次 VSync 提交；完全收起稳定后必须停止持续渲染以省电。
3. 全屏转场期间逻辑状态可能是 `Collapsed`，渲染条件必须同时检查 `FullscreenCapsulePhase`。
4. 系统玻璃球模糊窗口必须先于 GL 窗口创建并保持原 z-order；不要在展开后临时重建到 GL 上方。
5. “下层模糊”与“玻璃球模糊”互斥；“下层变暗”只画颜色，不得暗中附带模糊。
6. 模糊开关禁用时，对应滑杆要立即灰显且不可触控；不能通过重建整页导致滚动位置跳动。
7. 设置项整行都应可点击，最小触控高度保持约 48 dp；长描述可以换行，但不能被裁剪到屏幕外。
8. 滑杆修改先发布内存配置，再防抖写入 DataStore；不要让磁盘 IO 阻塞手指跟随。
9. 精确触控区域只覆盖当前灵动岛/玻璃球形状，不得用整个透明画布拦截下层应用。
10. 无障碍服务只读取白名单窗口包名/类名元数据，不能扩展到文字、截图、录音、按键或手势。
11. 许可、固定参考提交和应用内声明必须保留。

## 常见修改入口

| 需求 | 首先查看 |
| --- | --- |
| 设置布局、文字、点击反馈 | `ui/NativeSettingsUi.kt`、`ui/SettingsUiLogic.kt` |
| 设置即时同步与存储 | `ui/MainViewModel.kt`、`data/OrbConfigRepository.kt` |
| 默认值、范围、互斥关系 | `model/OrbConfig.kt`、`model/OrbConfigJson.kt` |
| 展开/收起/拖动/全屏动画 | `overlay/OverlayWindowController.kt`、`motion/AnalyticSpring.kt` |
| 横竖屏物理位置 | `overlay/RotationCoordinateSpace.kt`、`overlay/OverlayLayout.kt` |
| 120 Hz 请求与空闲策略 | `overlay/OverlayRefreshRatePolicy.kt`、`overlay/OverlayRenderPolicy.kt`、`render/GlRenderLoop.kt` |
| 玻璃球系统模糊 | `overlay/AndroidBackgroundBlurWindow.kt`、`overlay/BackgroundBlurController.kt` |
| 下层模糊/变暗 | `overlay/BackgroundAmbientBlurController.kt`、`overlay/BackgroundDimView.kt` |
| Shader 和玻璃效果 | `assets/shaders/*`、`render/GlOrbPipeline.kt` |
| 系统助手联动 | `overlay/GlassOrbAccessibilityService.kt`、`overlay/SystemAssistantPackages.kt` |

## 建议工作顺序

1. 把用户现象映射到 UI、配置、窗口、状态机、GL 或系统策略中的一个层级。
2. 先补或运行最接近该层的纯逻辑测试；视觉/厂商问题保留真机验收项。
3. 修改时保持窗口创建顺序、同帧几何快照和配置即时发布机制。
4. 先运行目标测试，再运行完整验证命令。
5. 从真实临时构建目录取得 APK，核验版本、签名和哈希后再替换 `dist`。
6. 提交前检查 `git diff --check`、`git status` 和 `dist` 中 APK 数量。

## 最低交付检查表

- [ ] `testDebugUnitTest` 通过。
- [ ] `lintDebug` 通过。
- [ ] `compileDebugAndroidTestKotlin` 通过。
- [ ] `assembleDebug` 通过。
- [ ] APK 包名、版本号、签名和 SHA-256 已重新核验。
- [ ] `dist` 目录只有一个 APK，`SHA256SUMS.txt` 只记录该文件。
- [ ] 中文和英文设置页没有裁剪、遗漏或错误标题。
- [ ] 若改动画/旋转/模糊，发布说明明确列出尚未完成的真机验收项。
- [ ] 若改权限或助手联动，重新检查 Manifest、无障碍 XML 和数据使用说明。
- [ ] `NOTICE.md`、`THIRD_PARTY_NOTICES.md`、`LICENSE` 未被删改为更宽松的许可。

## 当前仍需真机观察的重点

- OPPO/ColorOS 和小米/HyperOS 静置后是否把玻璃球从 120 Hz 降到 60 Hz。
- 开启系统玻璃球模糊后，呼吸、拖动形变和完全展开阶段的轮廓是否一致。
- 全屏进入/退出时灵动岛隐藏与恢复是否保持满帧，且收起后确实停止持续工作。
- 四向旋转、刘海/挖孔和状态栏区域的视觉位置及触控区域。
- 系统助手窗口出现与退出事件在目标 ROM 上是否可见。
