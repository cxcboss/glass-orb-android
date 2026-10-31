# 审计与改进计划（2026-10-31）

原则：**所有性能优化都不得降低帧率**——活跃动画保持每个 VSync 提交、请求 120 Hz，只减少每帧的冗余工作。

范围：全仓库代码通读（设置页、悬浮窗口控制器、GL 管线、服务、配置层、无障碍桥、模糊窗口）、资源与依赖、文档与仓库状态。方法：静态审阅 + 编译/lint + 模拟器冒烟（API 35、API 29）。**未运行单元/设备测试，未在真机验证**。

状态说明：✅ 本次已修复 · ⏳ 待办（附建议）· ⚠ 需真机验证后才能动。

## 1. 缺陷

| # | 严重度 | 位置 | 问题 | 状态 |
| --- | --- | --- | --- | --- |
| B1 | 高 | `MainViewModel` / `OrbConfigRepository` | 防抖落盘跑在 `viewModelScope`；最后一次拖动后 70 ms 内 ViewModel 被销毁则**丢失配置** | ✅ 落盘改由仓库进程级协程负责 |
| B2 | 中 | `OrbConfigRepository` | `publishGeneration += 1` 在 `@Volatile` 上非原子；DataStore 迟到的旧值可能覆盖实时配置造成一帧回跳 | ✅ 原子计数；DataStore 只读一次 |
| B3 | 中 | 设置页 | “开机自启”后端完整（偏好、广播、策略）但**设置页没有入口**，功能不可达 | ✅ 运行与权限页新增开关 |
| B4 | 中 | 设置页 / 文档 | 文档称许可证“应用内可读”，实际没有入口（合规缺口） | ✅ 新增“开源许可与声明”页 |
| B5 | 中 | `OverlayWindowController.show()` | 窗口已发布后再抛异常，`catch` 只清理局部变量，控制器字段残留半构建状态，下次 `show()` 误判已显示 | ✅ 统一 `removeView()` |
| B6 | 中 | `OrbOverlayService` | `APP_APPEARANCE`/`TOUCH_PREVIEW` 每次都 `startForeground` 重发通知；服务仅为这些命令实例化时留下“空”前台通知 | ✅ 仅启动/显示/隐藏进入前台 |
| B7 | 中 | `NativeSettingsController`（旧） | 运行状态/权限变化重建整页，打断开关动画与按压反馈（交互“不跟手”的根源之一） | ✅ 就地刷新 |
| B8 | 低 | `BackgroundAmbientBlurController` | 窗口创建失败后每帧重试；晚到的窗口会落在 GL 之上，把球体一起模糊 | ✅ 失败即终态 |
| B9 | 低 | 通知 / Toast | 中文硬编码，英文系统显示中文 | ✅ 资源化 |
| B10 | 低 | 设置页 | 英文缺“灵动岛宽度/高度”；`SettingsRefreshRateController.dispose()` 从未调用 | ✅ |
| B11 | 低 | `androidTest/MainActivityTest` | 引用不存在的“预设与备份 / JSON 导入”，必然失败 | ✅ 按新界面重写（未在设备上运行） |
| B12 | 低 | `NOTICE*` / 应用内声明 | 仍写“使用 Material 3 Views” | ✅ 已更正 |
| B13 | 低 | `OrbConfigJson.encode` | `Float` 直接 `put` 为 `double`，产生 `0.7200000286…` 之类噪声；`toString(2)` 带缩进写入 DataStore | ⏳ 先 `toString().toDouble()` 再写，改紧凑输出（需保持旧数据可解码） |
| B14 | 低 | 死代码 | `shouldFreezeCollapseEffects` 恒为 `false`，`RenderWorkPolicy.shouldAdvanceAmbientScene` 未被使用；`PerformanceConfig.collapsedFps/expandedFps` 仅存储不生效 | ⏳ 清理，或恢复“收起期冻结特效”并加测试 |

## 2. 性能

| # | 项目 | 收益 | 状态 |
| --- | --- | --- | --- |
| P1 | 移除 Material 依赖 | APK 17.7 → 10.4 MB（−41%），少约 1000 个文件，设置页启动少一整套主题/资源解析 | ✅ |
| P2 | `BackgroundAmbientBlurController.update` 每帧分配 5 个 `IntRect` + `List` | 活跃动画期间每帧少一批垃圾对象，降低 GC 抖动 | ✅ |
| P3 | `updateBackgroundBlur` 在模糊关闭时仍每帧构造光圈/请求 | 同上 | ✅ |
| P4 | 设置页：去掉逐行 Material 控件树，自绘开关/滑杆；玻璃表面最多 3 个同屏 | 滚动掉帧风险更低 | ✅ |
| P5 | `displayGeometry()` 在 Android 11+ 每帧读取 `currentWindowMetrics` 与 insets（可能跨进程查询 + 临时对象） | 120 Hz 下每秒少约 116 次查询 | ✅ 250 ms 缓存；`onDisplayChanged`、配置变化、显示/亮屏时开启 1.5 s“易变窗口”逐帧读取。⚠ 仍需 0/90/180/270° 真机验证 |
| P6 | 无障碍服务订阅 `typeWindowContentChanged`（并 `canRetrieveWindowContent`）：所有应用的内容变化都会派发到本进程 | 降低系统侧事件开销与功耗 | ⚠ 不能简单加 `packageNames` 过滤——可能同时丢掉包名为空的 `TYPE_WINDOWS_CHANGED`。建议先用 `dumpsys accessibility` / Perfetto 量化，再按 ROM 决定是否去掉该事件类型 |
| P7 | `statusBarVisibilityCheck` 每 200 ms 主线程轮询 | 降低空闲唤醒 | ⚠ 改为 `WindowInsets` 监听需验证各 ROM 对覆盖窗口是否回调 |
| P8 | `GlProgram.float/vec2` 每帧约 50 次 `HashMap<String,Int>` 查表 | 微小，但在中低端机的 CPU 侧可见 | ⏳ 初始化时预取 uniform location |
| P12 | 每帧 `glGetError()`（部分驱动会形成 CPU/GPU 同步点） | 去掉每帧同步 | ✅ 前两帧 + 每 120 帧检查一次，持续错误 1 秒内仍会暴露 |
| P13 | 设置页任意后代失效都让所有玻璃重新捕获并模糊 | 拖动滑杆时少 2–3 次逐帧模糊 | ✅ 按失效视图与玻璃的窗口区域求交，只刷新重叠的玻璃 |
| P9 | `effect.frag` 含 4/6/6×8/12 次循环 | 中低端机 GPU 占用高 | ⏳ 在**不降帧率**的前提下优化：循环展开/提前退出、把与像素无关的计算移到 CPU 作 uniform；分辨率自适应只能作为用户可选项 |
| P10 | GL 离屏目标未使用 `glInvalidateFramebuffer` | 分块渲染 GPU 可省带宽 | ⏳ 需在目标 GPU 上对比 |
| P11 | `release` 构建未启用 R8 / 资源压缩 | 进一步缩体积、加快启动 | ⏳ 见 `BUILD_AND_RELEASE.md` 的发布边界 |

## 3. 改进计划（按优先级）

**P0 — 发布前必做**
1. 真机验收悬浮层：四向旋转、全屏进出、三种背景效果开关、系统助手联动（OPPO/ColorOS、小米/HyperOS）。
2. 在 API 33+ 真机看新设置页的玻璃观感与每帧耗时（`dumpsys gfxinfo`）；超预算时优先减小 `blurDp` / 关闭 `lensDp`，而不是降低刷新率。
3. 为 `OrbConfigRepository` 补单测（防抖只写最新值、启动读取不覆盖用户值、`reset`）。

**P1 — 近期**
4. 处理 P5/P6/P7（先量化，再改）。
5. 清理 B13/B14，并为 JSON 往返补“旧数据可解码”测试。
6. 设置文案迁移到 `strings.xml`（当前为 `SettingsText` 映射）。
7. 引入 `release` 构建类型 + R8 + 独立签名（不要把 Debug 包当正式发行）。

**P2 — 长期**
8. 升级 `targetSdk`：需重做精确触控区域（目前依赖 `OnComputeInternalInsetsListener` 这类隐藏 API 与 `targetSdk 30` 行为），先在 Android 14/15/16 上逐项验证。
9. 自适应画质（P9）与低电量模式降级。
10. 仓库体积：`.git` 约 600 MB，历史里有旧 APK、ASR 模型与 `.aar`。如需瘦身需要 `git filter-repo` 重写历史，**会改变所有提交 ID，需用户决定后再做**。

## 4. 仓库与分支状态（需要你决定）

- 当前分支 `codex/independent-glass-orb` 与 `origin/main` 已分叉：`origin/main` 停在 2.0.11 的 README，并在 10 月把 APK 迁出源码树、改为 GitHub Release 下载。本分支仍把 APK 放在 `dist/`。
- 建议：确定统一做法（APK 放 Release 还是 `dist/`），再决定是否把本分支合并回 `main`。本次**没有**提交、推送或合并任何内容。
