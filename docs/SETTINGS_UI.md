# 设置页设计说明（iOS 26 设置 + 液态玻璃）

设置页完全由 Android 平台 `View` 手写，不依赖 Material Components 或 Compose。结构、版式和交互按 iOS 26 的“设置” App 复刻，浮在内容上的元素做成液态玻璃。

## 1. 页面结构（与 iOS 26 设置一一对应）

| iOS 26 设置 | 本项目 |
| --- | --- |
| 根页大标题“设置”，滚动后收进导航栏 | `Screen.Home`：34 sp 大标题，内联标题随滚动淡入 |
| 顶部 Apple 账户卡片 | `IosRows.profileCard`：60 dp 圆形应用图标、名称、副标题，点进“关于” |
| 根列表：彩色图标块 + 标题 + `>`，无副标题；开关项（如飞行模式）与值项（如 Wi‑Fi） | “显示灵动岛”为开关行；“运行与权限”右侧显示运行状态；其余为分组导航行 |
| 底部浮动搜索框（iOS 26 把搜索移到底部） | `GlassSearchBar`：玻璃胶囊，激活后出现圆形关闭按钮，键盘弹出时整体上移 |
| 二级页顶部“图标 + 标题 + 说明”头卡，导航栏只在其滚走后显示标题 | `IosRows.headerCard` + 内联标题（阈值按头卡位置） |
| 圆形玻璃返回键 | `GlassNavBar` 内的 `LiquidGlassView`（按压放大 + 触点高光） |
| 无横条的“滚动边缘”：内容滑到顶部时模糊并淡出 | `GlassNavBar` 的 `edgeFade = EDGE_TOP`，标题区实色、下沿渐隐，无分隔线 |
| 分组圆角卡片、发丝分隔线缩进到文字起点 | `IosRows.group`：26 dp 圆角、16 dp 外边距 |
| 开关 / 滑杆 / 分段控件 | `IosSwitch` / `IosSlider` / `IosSegmentedControl` |
| 长按弹出的玻璃上下文菜单 | `GlassContextMenu`：长按滑杆行 → “恢复默认值”（显示默认值） |
| 警告框：左对齐文字 + 胶囊按钮 | `GlassAlert`：34 dp 圆角玻璃窗口，取消（灰）/恢复（红） |

背景为纯色 `systemGroupedBackground`（浅 `#F2F2F7` / 深 `#000000`），卡片为 `secondarySystemGroupedBackground`（白 / `#1C1C1E`）。字体使用系统 `sans-serif`（SF Pro 受限，未打包）；字号：大标题 34、行标题 17、副标题与分组标题 13、头卡标题 22。

## 2. 分层

```text
root FrameLayout
├─ BackdropHostLayout        ← 玻璃的“背景源”（只放页面）
│   └─ 页面（每页一个 ScrollView）
├─ GlassNavBar               ← 滚动边缘 + 内联标题 + 玻璃返回键
├─ bottomEdge                ← 根页底部的滚动边缘（衬在搜索框后）
├─ GlassSearchBar            ← 仅根页显示
└─ GlassContextMenu 浮层     ← 临时
```

- **背景源里绝不能放玻璃视图**，否则捕获背景会无限递归。
- 外壳只有一份，由 `NativeSettingsController.syncChrome` 按顶层页面同步；推入页面时旧页 28% 视差后设为 `INVISIBLE`，不参与绘制与玻璃捕获。
- 运行状态、权限、开机自启通过 `Page.refreshers` 就地更新；只有“恢复本组默认值”才重建当前页并保持滚动位置。

## 3. 搜索

首次搜索时把所有二级页离屏构建一次，借 `IosRows.titleSink` 收集每一行的标题，形成索引（页面标题 + 行标题）。中英文都能匹配。点结果会跳到对应页面并平滑滚到那一行。新增的行自动进入索引，无需手工维护。

## 4. 液态玻璃实现

`LiquidGlassView` 每次绘制：

1. 形状外侧画软阴影（`clipOutPath` + `setShadowLayer`，半径受 `glassInsetPx` 约束，避免被裁）。
2. 向 `RenderNode` 录制“不透明底色 + `source.draw()`”，四周外扩 `2×模糊半径`，挂 `RenderEffect` 后画出。
3. 叠色调（默认 `glassTint`，滚动边缘用 `tintOverride` = 页面底色）、顶部高光、按压光晕；子视图之后画高光描边。
4. `edgeFade` 不为空时用 `saveLayer` + `DST_IN` 渐变遮罩，只在 `edgeFadeLengthPx` 范围内渐隐。

三档效果按 API 自动选择：

| API | 效果 |
| --- | --- |
| 33+ | 模糊 + 饱和度 1.55 + AGSL 边缘镜头（圆角矩形 SDF，`bezel` 带内向内折射，轻微色散） |
| 31–32 | 模糊 + 饱和度 |
| 29–30 | 不捕获背景，接近不透明的半透明纯色 |

镜头在 `LiquidGlassLens`（独立 `object`，低版本不会加载 `RuntimeShader`）；创建或编译失败会回落到模糊。

### 4.1 刷新与性能（帧率不降）

- `BackdropHostLayout.onDescendantInvalidated` 同帧标脏玻璃，没有“慢一帧”。
- **按区域过滤**：只有与失效视图重叠的玻璃才重新捕获。拖动页面中部的滑杆时，顶部与底部玻璃不会每帧重新模糊；滚动时整页失效，玻璃照常同帧刷新。
- 滚动边缘在内容未滑到其下时 `glassAlpha = 0`，整条跳过绘制。
- 同屏玻璃最多：顶部边缘、返回键、底部边缘、搜索框（菜单短暂出现）。不要给列表行加玻璃。
- 滚动时 `SettingsRefreshRateController` 请求面板最高刷新率，松手后恢复系统偏好。

## 5. 组件速查

| 组件 | 文件 | 要点 |
| --- | --- | --- |
| `IosRows` | `ui/ios/IosRows.kt` | `largeTitle`、`profileCard`、`headerCard`、`sectionHeader/Footer`、`group { switchRow / navRow / valueRow / actionRow / segmentedRow / sliderRow / customRow }` |
| `RowHandle` | 同上 | `setValue`、`setSubtitle`、`setChecked`（不触发回调）、`row.setRowEnabled(...)` |
| `IosSlider` | `ui/ios/IosSlider.kt` | 点按轨道跳转，横向拖动接管手势；`onUserChange` 只在用户操作时触发；`RangeInfo` + `ACTION_SET_PROGRESS` |
| 滑杆行 | `IosRows.sliderRow` | 值与默认不同时数值变蓝；长按行 → 玻璃菜单恢复默认 |
| `IosSwitch` | `ui/ios/IosSwitch.kt` | 继承 `CompoundButton`，保留可选中语义与 `Switch` 无障碍类名 |
| `UiSpring` | `ui/ios/UiSpring.kt` | 基于 `AnalyticSpring` 的帧驱动弹簧，稳定后自动停；系统关闭动画时直接到位 |

## 6. 无障碍与本地化

- 开关行：开关本体带描述与选中状态，行容器只扩大触控面积。
- 中文为源文案，`SettingsText` 提供英文映射；行构建器自动翻译，新增文案记得补映射。
- 系统关闭动画时所有弹簧直接落到目标值。

## 7. 已知取舍

- 没有 SF Pro / SF Symbols，图标用现有矢量图。
- 未复刻 iOS 回弹式过度滚动，使用系统拉伸效果。
- 边缘镜头是 SDF 近似；中低端 API 33+ 真机上的观感与耗时需要实测。
