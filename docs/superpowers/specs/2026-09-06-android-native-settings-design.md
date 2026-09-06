# Android 原生设置界面与预测返回设计

## 目标

将 App 内设置页完整改成 Android 原生 Material 3 风格，移除上一版 iOS 视觉语言、Liquid Glass 设置控件和自绘导航结构。保留悬浮球服务、七组参数、预设、重置、JSON 导入导出、权限操作和关于说明；玻璃球 shader、渲染管线、悬浮窗布局与动画不在本次范围内。

## 视觉与组件

- 使用 Material 3 `Scaffold`、`TopAppBar`/`CenterAlignedTopAppBar`、`ListItem`、`Switch`、`Slider`、`Button`、`OutlinedButton`、`TextField`、`AlertDialog`、`SnackbarHost` 和 `HorizontalDivider`。
- 页面使用 `MaterialTheme` 的动态系统配色；设备不支持动态色时回退到 Material 3 默认 light/dark color scheme。背景、surface、primary、error 和内容色均由主题提供，不写 iOS 固定色值。
- 首页为 `Scaffold` + 大标题 `LargeTopAppBar` + `LazyColumn` 分组列表。二级页为标准 `TopAppBar` 返回按钮和标题，参数使用标准列表行与标准 `Slider`。
- 不使用 Liquid Glass、Backdrop、渐变背景、装饰阴影、彩色光晕或自绘控件；仅保留必要的 Material 形状与系统 elevation。
- 行高、触摸范围、无障碍语义交给 Material 组件默认实现；长页面只用 `LazyColumn`。

## 导航与预测返回

- 引入 AndroidX Navigation Compose，使用单一 `NavHost` 与 `NavController` 管理 `Home`、`OverlayDetails`、七个 `ConfigGroupDetail`、`Presets`、`DataManagement`、`About` 路由。
- 使用官方支持预测返回的 Navigation Compose 版本与现有 `activity-compose`；Manifest/Activity 开启 `android:enableOnBackInvokedCallback="true"`。
- 页面过渡使用 Navigation Compose 的 `popEnterTransition`/`popExitTransition`。二级页返回由导航 back stack 处理，系统返回键、返回按钮和边缘返回共用同一 pop 行为；手势取消不改变 back stack。
- 不在根页拦截返回，确保 Android 15+ 返回主页系统动画不被破坏。根页由系统默认退出。

## 功能映射

- 首页总开关保持原行为：无悬浮权限时跳转授权页，已停止时启动，已隐藏时显示，关闭时隐藏但不停止服务。
- `OverlayDetails` 保留运行状态、权限、显示/隐藏/停止和确认提示。
- 七组配置页沿用 `OrbConfig` 与 `OrbConfigRepository`，每项参数使用标准 Slider/Checkbox/Switch/RadioButton 等原生控件；默认值标记、单项还原、触控区域红框和自动收起设置保留。
- 预设页面使用标准单选列表；JSON 页面使用标准 `OutlinedTextField` 多行输入、导入按钮、复制按钮和 Snackbar 错误/成功反馈。
- 重置、停止和替换预设使用 Material `AlertDialog`；关于页保留版本、来源、许可证和效果边界。

## 数据与性能边界

- 不迁移或改变配置 schema、默认参数和 DataStore 键；不读取下层 App 内容、不录屏、不增加网络/录音/无障碍权限。
- 设置页不启动 GLES 预览或持续帧循环；只组合当前导航目的地，参数页使用稳定 key 的 LazyColumn。
- 事件回调使用最新状态，避免 remember 捕获旧配置；设置控件由 Material 组件处理拖动和按压，避免重复自定义 pointerInput。

## 验证

- JVM：路由 back stack、总开关动作映射、配置重置、JSON 错误保持现值。
- Compose/Android 源码测试：主页到二级页、返回按钮和系统返回、预测返回取消/完成后的栈状态、标准 Slider 连续拖动和 Switch 状态。
- 静态与构建：`testDebugUnitTest`、`lintDebug`、`assembleDebug`、`compileDebugAndroidTestKotlin`。
- 按用户要求不运行模拟器或实体机人工测试；记录预测返回需要 Android 13/14 开发者选项或 Android 15+ 设备验证。

## 官方依据

- [Predictive back setup](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)
- [PredictiveBackHandler API](https://developer.android.com/reference/kotlin/androidx/activity/compose/PredictiveBackHandler.composable)
- [Material 3 Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
