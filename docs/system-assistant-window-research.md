# 系统语音助手窗口联动调研

日期：2026-09-10

## 结论

上一版没有稳定展开的主要原因不是悬浮层动画，而是无障碍事件配置不满足 Android 对真实窗口变化的要求：服务监听了 `TYPE_WINDOWS_CHANGED`，却没有声明 `flagRetrieveInteractiveWindows`，同时 XML 只允许一个 OPPO 包名。Android 官方文档明确说明，未设置该 flag 时不会收到 `TYPE_WINDOWS_CHANGED`，`AccessibilityService.getWindows()` 也会返回空列表；该 flag 还要求 XML 声明 `canRetrieveWindowContent=true`。

修复后的链路是：

1. 无障碍服务声明交互窗口 flag，并不在 XML 用单一包名过滤。
2. 收到 `TYPE_WINDOWS_CHANGED` 后查询当前交互窗口集合。
3. 仅调用每个窗口根节点的 `packageName`，与本地白名单比对；不读取文字、不遍历节点、不截图、不录音。
4. 通过窗口出现/移除状态通知悬浮层；服务重连和悬浮层晚启动时重放当前可见状态。

## Android 官方依据

- [AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo.html#FLAG_RETRIEVE_INTERACTIVE_WINDOWS)：未声明 flag 时不会收到 `TYPE_WINDOWS_CHANGED`，`getWindows()` 返回空列表；同时要求 `canRetrieveWindowContent=true`。
- [AccessibilityEvent](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent.html)：`TYPE_WINDOWS_CHANGED` 是系统管理的真实窗口变化事件，窗口信息应通过 `AccessibilityService.getWindows()` 获取；事件的 `packageName` 是 source 包名，在厂商实现中不应作为唯一判断依据。
- [AccessibilityService.getWindows()](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService.html#getWindows())：返回当前用户可交互窗口，按层级从上到下排列；同样要求窗口内容能力和交互窗口 flag。

## 国产 ROM 能力边界

- [小米小爱开放平台](https://developers.xiaoai.mi.com/documents/Home?type=/api/doc/render_markdown/VoiceserviceAccess/Device/develop/ProtocolDocument/Endpoint) 的公开资料主要面向接入小爱语音服务的设备/技能协议，包含第三方唤醒上下文，但没有给普通第三方 Android App 提供“订阅系统小爱唤醒窗口”的通用回调。
- [vivo Jovi 技能平台](https://developers.vivo.com/product/ai/joviskill) 面向技能、意图和业务逻辑接入，没有发现普通第三方 App 监听系统 Jovi 唤醒界面的公开 Android 回调。
- [OPPO 开放平台](https://open.oppomobile.com/wiki/index) 的公开索引未提供普通第三方 App 订阅系统语音助手窗口的通用接口。当前实现因此使用 Android 无障碍窗口生命周期作为兼容层，不调用 OPPO/小米/vivo 私有签名权限或隐藏 Intent。

## 当前候选包名

代码内保留以下候选，用于不同系统版本的实际真机验证：

- OPPO：`com.heytap.speechassist`
- 小米：`com.miui.voiceassist`、`com.xiaomi.voiceassistant`
- vivo：`com.vivo.assistant`、`com.vivo.vassistant`、`com.vivo.jovi`

这些候选是兼容尝试，不代表每一款 ROM 都使用相同包名。若某台设备仍不触发，需要根据该设备实际无障碍事件或窗口包名补充白名单；不应通过扩大到所有包名来规避误触发。

## 隐私与 OEM 稳定性取舍

Android 官方 API 要求 `canRetrieveWindowContent=true` 才能获取交互窗口列表，因此本版的无障碍配置能力范围会比上一版更大；代码只使用根节点包名，仍不读取页面内容。没有增加录音、剪贴板、录屏、存储、按键过滤、手势、触控探索或电池优化豁免能力。服务不调用 `setServiceInfo` 动态覆盖 XML，减少国产 ROM 将服务判定为无效并停止的风险。
