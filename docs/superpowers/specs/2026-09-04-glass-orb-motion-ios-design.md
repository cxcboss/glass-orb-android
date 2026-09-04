# 玻璃球顶部锚点动效与 iOS 风格设置页设计

## 目标

在不改变当前玻璃球 Shader、颜色层次和透明背景表现的前提下，优化胶囊与玻璃球之间的展开/收起动效，并把设置页改成跟随系统深浅色的 iOS 风格界面。悬浮层仍只承诺桌面和普通第三方 App 上方的 UI 展示，不读取屏幕内容，也不实现真实背景折射。

## 已确认的交互

- 胶囊是纯黑色，点击后展开为玻璃球。
- 展开和收起都以胶囊顶部作为视觉锚点；球体高度向下增长或向上收回，顶部不发生中心漂移。
- 上滑关闭继续使用 64dp 位移或 800dp/s 速度阈值。拖动过程实时跟手，但采用连续橡皮筋压缩，位移越大阻力越强。
- 没有达到关闭阈值时，球体回到展开状态；达到阈值时，球体从当前形态接续弹簧收回胶囊。
- 展开后的球体支持任意方向的轻微拖拽形变。形变只改变尺寸和内部几何，不让窗口大幅移动；最大拉扯约 8dp，缩放变化不超过 2%，松手后带轻微反向过冲回弹。明显向上拖动才进入关闭手势，多指触摸取消当前手势。
- 收起动画先在扩展画布中完成最后一帧纯黑胶囊渲染，再缩小悬浮窗，避免 Surface 重建造成闪烁。
- 设置页增加 iOS 风格的底部悬浮栏（概览、外观、动效），只影响设置页，不改变跨应用悬浮层行为。

## 方案与边界

### 推荐方案：单扩展画布 + GPU 形变

`WindowManager` 只在两个端点调整窗口尺寸：点击展开时一次性切到扩展画布，收起完成后延后一帧切回胶囊窗口。动画过程不调用 `updateViewLayout`。`OrbTextureView` 保持同一 Surface，主线程提交不可变快照，GL 线程只更新 uniforms。这样既保留透明窗口的触摸边界，也避免每帧布局抖动和 Surface 闪烁。

逐帧改变窗口大小不采用，因为会频繁触发窗口布局，在部分厂商系统上会出现黑帧或掉帧。另建 Compose 浮层不采用，因为它无法可靠覆盖普通第三方 App。

## 动效模型

### 顶部锚点

`OverlayWindowController` 在窗口切换时计算 `anchorTopPx` 和 `anchorCenterXPx`，保存为相对于扩展画布的值。渲染时先计算形状尺寸，再使用：

```text
shapeTop = anchorTop + tinyDeformationTopOffset
shapeCenterY = shapeTop + shapeHeight / 2
```

`shapeTop` 不由形状中心插值，因此胶囊顶部和球体顶部始终重合。`tinyDeformationTopOffset` 仅来自球体轻微拖拽，范围不超过 2dp；收起手势本身不把球心向上平移。

### 展开

1. 点击胶囊后立即切换到扩展画布，`morphSpring` 保持 0，首个提交快照仍由 `glass.frag` 的纯黑胶囊分支绘制。
2. 将 `morphSpring` 目标设为 1，使用 `openResponse=0.36s`、`openDamping=0.84` 的轻微欠阻尼弹簧。
3. 形状宽高按 `lerp(capsule, orb, easedMorph)` 计算，顶部固定；`easedMorph` 对弹簧值做 `smoothstep` 和有限过冲压缩，避免极小尺寸停顿。
4. 波形/圆点在形状进入约 8% 后开始出现，延迟沿用 80ms；玻璃 Shader 参数不改。

### 跟手收起与橡皮筋

触摸移动得到原始位移 `rawDy`。上滑关闭方向使用连续压缩：

```text
rubberDy = -sign(rawDy) * range * (1 - exp(-abs(rawDy) * resistance / range))
pull = 1 - exp(-abs(rawDy) / collapseRange)
visualMorph = 1 - clamp(pull, 0, 1)
```

默认 `range=64dp`、`resistance=0.62`、`collapseRange=48dp`。`visualMorph` 直接跟随手指，`rubberDy` 只用于极轻的内部拉扯反馈；顶部锚点不移动。释放时将当前 `visualMorph` 和计算出的速度写入收起弹簧：超过阈值目标为 0，否则目标为 1。收起弹簧默认 `closeResponse=0.26s`、`closeDamping=0.76`，`closeBounce=0.045`，允许一次短促回弹后稳定。

### 展开后轻微形变

新增独立的 `ElasticDrag` 数学模型，输入经过橡皮筋压缩的 `dragX`、`dragY`，输出 `scaleX`、`scaleY` 和最多 2dp 的顶部微偏移。默认限制如下：

```text
maxDrag = 8dp
maxScaleDelta = 0.016
scaleX = 1 + maxScaleDelta * abs(dragX) / maxDrag
scaleY = 1 - maxScaleDelta * 0.55 * abs(dragY) / maxDrag
```

输出在 `[-maxDrag, maxDrag]` 内连续收敛，释放后使用 `response=0.24s`、`damping=0.68` 的弹簧回到零点。所有形变只进入 `RenderSnapshot` 和 `GlOrbPipeline` 的形状尺寸/位置计算，不修改波形、暗场和玻璃 Shader 公式。

### 收起防闪烁时序

控制器增加独立的 `windowExpanded` 和 `pendingCollapsedResize` 标志。收起弹簧稳定后：

1. 将状态切到 `Collapsed`，锁定 `morph=0`，但继续保留扩展画布。
2. 提交一帧最终纯黑胶囊快照。
3. 下一次 Choreographer 回调才调用 `updateBounds(expanded=false)`，同时清零锚点和拖拽偏移。

展开过程反向执行：先扩窗，再提交黑色胶囊首帧，然后开始弹簧。任何权限撤销、Surface/GL 初始化失败或服务停止都直接移除窗口，不保留透明矩形。

## 代码边界

- `motion/AnalyticSpring.kt`：增加带初速度的重定位接口，保持现有固定时间轨迹兼容。
- `motion/ElasticDrag.kt`：封装橡皮筋压缩、形变上限和释放回弹输入输出。
- `overlay/OverlayWindowController.kt`：拆分窗口模式、顶部锚点、跟手收起和延后一帧缩窗逻辑；单次触摸仍由该控制器接收。
- `overlay/OverlayState.kt`：补充拖拽中形变/收起的显式状态转移，保持多指取消后的下一次点击可用。
- `render/RenderSnapshot.kt`：增加不可变的形变和锚点字段。
- `render/GlOrbPipeline.kt`：使用锚点、形变缩放和收起进度计算 panel origin/size；不修改 `glass.frag`、`container.frag`、`effect.frag` 的视觉公式。
- `model/OrbConfig.kt`、`model/OrbConfigJson.kt`、`ui/ConfigEditor.kt`：为拖拽上限、阻力和回弹参数提供默认值、范围夹紧、JSON 往返和设置项。
- `ui/GlassOrbTheme.kt`、`ui/MainActivity.kt`、`ui/SettingsScreen.kt`：跟随系统深浅色、iOS 调色板、分组卡片、胶囊控件和底部悬浮栏。

## iOS 风格设置页

浅色主题使用 `#F2F2F7` grouped background、白色半透明卡片、`#007AFF` accent 和 `#FF3B30` destructive；深色主题使用黑色背景、`#1C1C1E` 卡片、`#0A84FF` accent。状态栏与导航栏图标根据当前主题同步切换。

底部栏是悬浮圆角胶囊，包含“概览 / 外观 / 动效”三项。面板由半透明底色、细白描边、顶部高光、内阴影和轻微阴影组成；不引入新的液态玻璃依赖，以免影响原生悬浮层体积和 API 26 兼容性。内容分类只改变设置页显示顺序，所有原有预览、预设、JSON 和调参能力保留。

## 测试与验收

- JVM：固定时间点验证带初速度的弹簧轨迹；验证橡皮筋函数单调、有界、连续；验证形变缩放不超过 2%；验证顶部锚点在 0、0.25、0.5、0.75、1.0 进度误差小于 1dp；验证收起状态机的一帧延迟缩窗顺序。
- UI/渲染：确认纯黑胶囊分支和玻璃 Shader 输出保持不变；确认波形/圆点交叉淡入不归零；确认设置页系统深浅色立即跟随，底部悬浮栏不遮挡最后一项内容。
- 本轮不重复执行 APK 仪器测试，以用户已完成的真机/模拟器验证为准；提交前执行 JVM 单元测试、`lintDebug` 和 `assembleDebug`，只把构建结果作为编译回归证据。

## 非目标

- 不读取、录制或折射下层 App 的真实像素。
- 不增加录音、无障碍、开机自启或业务功能。
- 不改变当前玻璃球的颜色、暗场、波形和透明质感实现。
