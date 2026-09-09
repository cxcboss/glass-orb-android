# 系统语音助手集成可行性调研

> 调研日期：2026-09-09  
> 范围：仅核对小米 `dev.mi.com`、OPPO `open.oppomobile.com`、vivo `dev.vivo.com.cn` 与 Android 官方资料。  
> 判定标准：只有同时存在“第三方原生 App 可调用的公开入口”“可向系统助手传入 App 自有录音”“有公开定义的结果返回契约”才算满足目标。

## 结论先行

截至本次公开资料核对，未找到三家厂商提供上述完整链路的公开 API。能够确认的是：

- 小米公开资料中有“启用小爱智能语音服务”的开放平台权限项，也有通过小爱唤起快应用的场景说明；但没有找到面向任意第三方原生 App 的录音输入、助手处理、结果回调 API 定义。
- OPPO 公开资料中有小布助手技能平台，文档描述的是意图、槽位、多轮、卡片/快应用等“让小布调用开发者能力”的技能接入；没有找到任意原生 App 将 PCM/录音交给小布并取回助手结果的客户端 API。
- vivo 官方站点和官方大会资料能确认 Jovi 服务平台/开放平台方向；没有找到可核验的第三方原生 App 语音助手调用、录音上传、结果回调公开 API。
- Android 官方 API 也没有定义“普通 App 调当前 OEM 助手、传入任意录音、同步拿回助手结果”的通用接口。`ACTION_ASSIST` 和 `ACTION_VOICE_COMMAND` 都没有返回值契约；`VoiceInteractionService` 是实现系统级助手的服务入口，不是调用别人的助手的客户端接口。

“未找到”是对截至调研日期可公开访问资料的结论，不等于厂商不存在仅向签约合作伙伴、系统应用或内部组件开放的接口。

## 厂商官方资料核对

### 小米 / 小爱

**已确认**

- [小米澎湃 OS 文档中心](https://dev.mi.com/xiaomihyperos/documentation) 的应用开发目录包含系统适配、服务能力和开放服务等公开文档入口。
- [开放数据接口权限列表](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1518) 列出 `20000`，描述为“启用小爱智能语音服务”，API 说明栏为“小爱”。这证明公开平台上存在与小爱相关的权限/服务标识。
- [开放服务接入操作指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1963) 描述了开放服务的开发者账号、应用上架、服务选择、启用和申请流程，说明此类能力可能受应用资格和审核约束。
- [快应用常见问题](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1932) 描述“小爱同学”通过语音指令打开快应用；这是助手到快应用的生态/分发场景，不是原生 App 向小爱提交录音并获取识别或对话结果的接口。

**未找到**

在上述小米公开文档和文档中心可见的应用开放能力范围内，未找到同时具备以下内容的公开 SDK、Intent、Binder/AIDL 或 HTTP API：

1. 任意第三方原生 App 的调用方式；
2. 将该 App 已录制的音频或 PCM/音频文件传给小爱；
3. 小爱语音识别、意图处理或对话结果的回调/返回数据结构。

因此，`20000` 权限项不能单独证明存在目标 API；在没有接口文档、参数、权限、结果模型和接入示例前，应按“未找到完整公开接口”处理。

**替代方案**

- 若目标是让语音助手触发应用能力：研究小米公开的开放服务/快应用/技能合作流程，并按其申请和审核要求接入；这属于“助手调用应用能力”。
- 若目标是 App 自己拿到语音文本：使用 Android `SpeechRecognizer` 或自己的语音服务，不依赖小爱。
- 若目标仅是打开系统辅助入口：尝试 Android `ACTION_ASSIST`，但不能依赖它传入录音或返回助手结果，见下文 Android 边界。

### OPPO / 小布助手

**已确认**

- [OPPO 开放平台开发者服务中心](https://open.oppomobile.com/new/wiki) 当前公开目录包含“小布建议”、大模型、端侧 AI 的“AI 能力开放服务”等方向。
- OPPO 官方开发者社区的[小布助手开放平台——礼物技能自建流程](https://open.oppomobile.com/bbs/forum.php?mod=viewthread&tid=4815) 描述了技能名称/调用名、意图、实体槽位、多轮设计、文字卡片回复、对话测试和真机测试。它的公开模型是“用户通过小布说出调用语句，小布进入开发者配置的技能并返回技能结果”。
- [小布技能平台《个人开发者开发文档》](https://open.oppomobile.com/bbs/forum.php?mod=viewthread&tid=4828) 是官方社区公布的开发文档入口；[OPPO 官方案例说明](https://open.oppomobile.com/bbs/forum.php?mod=viewthread&tid=4948) 也将开放方向描述为通过小布技能和快应用融合，让用户一句话进入应用内部操作。
- [AI 能力开放服务](https://open.oppomobile.com/newservice/capability?pagename=ai) 是 OPPO 开放平台的 AI 能力页面，公开内容偏向算法/能力服务；页面没有给出“调用系统小布助手并取回其对话结果”的通用原生 App 合约。

**未找到**

未找到面向任意第三方原生 App 的公开接口，能够让 App：

- 指定调用当前系统小布助手；
- 把 App 自己录好的音频传给小布；
- 以公开回调、Result Intent 或 SDK 对象取得小布的识别/意图/对话结果。

小布技能平台文档证明的是技能生态存在，不证明存在本题所需的“App 作为客户端调用小布”的音频桥接 API。现有公开资料还显示，技能通常需要平台配置、测试和平台侧审核/运营流程。

**替代方案**

- 若业务是“让小布响应后操作自己的应用”：按小布技能平台的意图、槽位、回复、快应用融合路径申请接入。
- 若业务是“在 App 内录音并返回文本”：使用 Android `SpeechRecognizer` 或自有语音识别服务。
- 若业务只是打开语音命令入口：只能把 `ACTION_VOICE_COMMAND` 作为兼容性尝试，不能把它当作小布结果 API。

### vivo / Jovi

**已确认**

- [vivo 开放平台首页](https://dev.vivo.com.cn/) 是 vivo 官方开发者入口。
- vivo 官方[2019 开发者大会资料](https://dev.vivo.com.cn/vdc2019/index.html?s=2) 明确列出“Jovi，一种生活方式”“新服务、新生态 Jovi 服务平台”“vivo 开放平台全线升级”等议题，证明 Jovi 服务平台/开放生态曾作为官方开发者方向公开介绍。

**未找到**

在 vivo 官方开发者站点公开可访问/可检索资料中，未找到可核验的第三方原生 App 语音助手调用 API 文档，尤其未找到以下完整契约：录音输入格式、调用入口/权限、Jovi 处理方式、结果回调或返回数据结构。

因此不能仅凭 Jovi 服务平台或大会议题推断存在“向 Jovi 传录音并拿结果”的公开原生 API。该结论也不能排除商务合作、特定系统版本或登录后控制台中的非公开/受限能力。

**替代方案**

- 若 vivo 后续提供服务技能/应用能力合作，应以官方控制台中的具体接入文档、SDK 和审核流程为准。
- 当前可落地的通用方案仍是 App 自己使用 Android `SpeechRecognizer` 或自有 ASR 服务。
- 需要打开系统辅助入口时，可评估 `ACTION_ASSIST`，但不能把它当作 Jovi 的录音/结果通道。

## Android 官方 API 边界

### `VoiceInteractionService`

官方 [VoiceInteractionService API 参考](https://developer.android.com/reference/android/service/voice/VoiceInteractionService) 将其定义为“当前全局语音交互器”的顶层服务；由用户选定的当前服务会被系统保持运行，以支持热词等系统级交互。服务必须声明 `android.service.voice.VoiceInteractionService`，并要求 `Manifest.permission.BIND_VOICE_INTERACTION`，以避免被其他应用滥用。

边界：

- 它是“实现一个系统级助手”的服务扩展点，不是普通 App 调用当前系统助手的客户端 API。
- 选择的助手可以实现 `VoiceInteractionSessionService`/`VoiceInteractionSession` 来处理实际会话，但官方公开契约没有定义任意第三方 App 将自有 PCM 直接注入另一个厂商助手并取得其 NLU/对话结果。
- `BIND_VOICE_INTERACTION` 是组件绑定保护条件；普通 App 不能通过声明它来获得对 OEM 助手的调用权限。

### `Intent.ACTION_ASSIST`

官方 [Intent.ACTION_ASSIST](https://developer.android.com/reference/android/content/Intent#ACTION_ASSIST) 的定义是“Perform assist action”。输入是可选的 `EXTRA_ASSIST_PACKAGE`、`EXTRA_ASSIST_CONTEXT` 和 referrer 等辅助上下文；官方明确写的是 **Output: nothing**。

边界：

- 可用于请求系统的 assist 行为/入口，具体是否有处理 Activity 也必须防护；它不是音频上传接口。
- `EXTRA_ASSIST_CONTEXT` 是前台应用提供的上下文 `Bundle`，不能据此推断可以放入任意录音并交给助手识别。
- 没有标准返回 Intent、文本结果或对话结果回调；不能作为“触发助手并同步拿结果”的实现依据。

### `Intent.ACTION_VOICE_COMMAND`

官方 [Intent.ACTION_VOICE_COMMAND](https://developer.android.com/reference/android/content/Intent#ACTION_VOICE_COMMAND) 的定义是“Start Voice Command”。当前 API 参考重点描述蓝牙耳机/LE Audio 发起的语音会话：根据 `BluetoothDevice.EXTRA_DEVICE` 和 `BluetoothProfile.EXTRA_PROFILE`，应用可配合 `BluetoothHeadset.startVoiceRecognition()`、`AudioRecord.setPreferredDevice()` 和 `AudioRecord.startRecording()` 开始录音；官方同样明确 **Output: nothing**，并提示匹配的 Activity 可能不存在。

边界：

- 它不是“调用当前 OEM 系统助手”的通用标准协议。
- 它不定义向助手传递一段已经录好的音频，也不定义助手结果返回。
- 即使某个设备把它解析到语音组件，也不能把这种厂商/系统行为当作跨设备公开契约。

### `SpeechRecognizer`

官方 [SpeechRecognizer API 参考](https://developer.android.com/reference/android/speech/SpeechRecognizer) 提供的是 App 使用语音识别服务的标准能力：通过 `createSpeechRecognizer()`（可选定具体 `RecognitionService`）、`startListening(Intent)` 开始识别，并通过 [RecognitionListener](https://developer.android.com/reference/android/speech/RecognitionListener) 回调 `onResults`、`onPartialResults` 或 `onError`。

边界和前置条件：

- 调用方必须拥有 `Manifest.permission.RECORD_AUDIO`；API 参考还警告实现可能把音频流式传到远程服务器，因此不适合持续识别。
- `startListening()` 接收的是识别参数 `Intent`，不是“把任意已录音文件作为输入交给系统助手”的接口；标准用法由识别服务管理录音过程。
- 这是本题“录音后获得文本结果”最接近的 Android 官方替代方案，但返回的是识别服务结果，不是小爱/小布/Jovi 的助手对话结果。
- `SpeechRecognizer.createSpeechRecognizer(context, serviceComponent)` 可指定实现 `RecognitionService` 的识别服务；这仍然是 ASR 服务选择，不等于选择 OEM 系统助手。

### `ROLE_ASSISTANT`

官方 [RoleManager API 参考](https://developer.android.com/reference/android/app/role/RoleManager#ROLE_ASSISTANT) 定义了 `ROLE_ASSISTANT`（API 29）这一“assistant app role”。[RoleManager 总说明](https://developer.android.com/reference/android/app/role/RoleManager) 规定：角色可用性需要查询；即使应用符合资格，也需要用户同意，通过 `createRequestRoleIntent()` 发起请求；失去角色后，角色特权也会撤销。

官方 AndroidX 的 [RoleManagerCompat 说明](https://developer.android.com/reference/androidx/core/role/RoleManagerCompat#ROLE_ASSISTANT) 补充了资格方向：应用需要实现 `VoiceInteractionService` 或处理 `ACTION_ASSIST` 才可能符合助手角色要求。

边界：

- `ROLE_ASSISTANT` 是让自己的应用申请成为用户选择的助手角色，不是让自己的应用调用小米、OPPO 或 vivo 助手。
- 它需要用户授权，不是可静默取得的系统权限。
- 即便成为角色持有者，也意味着应用承担助手实现职责；不会自动获得其他 OEM 助手的录音注入和结果回调能力。

## 可行性判定与建议

| 目标 | 官方公开 API 可行性 | 说明 |
| --- | --- | --- |
| 第三方原生 App 唤起当前 OEM 助手 | 未确认有跨厂商公开 API | `ACTION_ASSIST` 可尝试 assist 入口，但无结果契约；厂商私有 Intent 需要官方文档和版本约束 |
| 把 App 自有录音传给小爱/小布/Jovi | 未找到 | 三家公开资料没有找到音频输入格式、权限和调用/回调协议 |
| 获取 OEM 助手识别/对话结果 | 未找到 | `ACTION_ASSIST`、`ACTION_VOICE_COMMAND` 都是无输出；厂商技能平台是另一种助手调用应用能力的方向 |
| App 自己录音并获得语音文本 | 可行 | Android `SpeechRecognizer` + `RECORD_AUDIO` + `RecognitionListener` |
| App 成为系统助手 | 条件可行 | 实现 `VoiceInteractionService`，满足资格并经用户同意取得 `ROLE_ASSISTANT`；这不是调用 OEM 助手 |
| 让系统助手调用 App 能力 | 可能需要厂商合作 | 小米开放服务/快应用、OPPO 小布技能等方向可继续申请，但需按各厂商具体审核和公开接入文档实现 |

对当前项目的建议：若需求是“按住胶囊录音并在 App 内显示识别结果”，优先采用 `SpeechRecognizer` 或自有 ASR；若需求是“显示/打开系统助手”，仅把 `ACTION_ASSIST` 作为可选入口并做好无处理 Activity、无返回结果和 ROM 差异的失败兜底。不要基于未公开的包名、组件名、反射接口或猜测性的厂商 Intent 实现生产链路。

## 文字推送给系统助手的补充核对

本次进一步核对“App 已经得到文字后，能否把文字交给系统助手并拿到回答/执行结果”。结论仍然是：没有找到跨厂商、面向普通第三方 App 的公开稳定接口。

### Android 标准入口

- `Intent.ACTION_ASSIST` 是请求系统执行 assist 行为，官方 API 没有定义文本查询参数，也没有返回结果契约；不能把它当作“提交一段文字给当前助手”的接口。[ACTION_ASSIST](https://developer.android.com/reference/android/content/Intent#ACTION_ASSIST)
- `Intent.ACTION_VOICE_COMMAND` 是启动语音命令会话的入口，官方同样没有定义文字输入和回答回调，且设备上可能没有匹配的 Activity。[ACTION_VOICE_COMMAND](https://developer.android.com/reference/android/content/Intent#ACTION_VOICE_COMMAND)
- `RoleManager.ROLE_ASSISTANT` 能让自己的应用在用户同意后申请成为默认助手，但这不是调用小米、OPPO 或 vivo 助手；要成为助手需要实现 `VoiceInteractionService` 等完整系统组件。[RoleManager](https://developer.android.com/reference/android/app/role/RoleManager)、[VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)

### 类似但方向相反的实现：App Actions

Android App Actions 允许 App 在 `shortcuts.xml` 中声明能力，Google Assistant 根据用户语句匹配能力，然后通过 Android Intent 将解析出的参数传给 App。也就是说，它是“系统助手 -> App”，不是“App -> 系统助手”；它不能让灵动岛把任意识别文字塞进当前 OEM 助手并同步获取回答。[App Actions](https://developer.android.com/develop/devices/assistant/action-schema)

### 厂商平台的类似路径

- 小米当前公开的 Agent/Skill 平台支持在 Miclaw 中分发 Agent 或 Skill，并要求按平台流程申请、调试和审核。它可以让小米的 AI Agent 调用开发者能力，但公开文档没有给出普通 APK 将文字提交给 Miclaw、等待回答并接收回调的客户端 SDK。[小米 Agent 发布指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2305)、[小米 Skill 发布指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2307)
- OPPO 小布公开的技能平台支持技能、意图、槽位和回复配置，调用方式是用户在小布中说出技能调用语句；资料没有给出普通 APK 将文字注入小布并取回最终回答的接口。[OPPO 小布技能平台说明](https://open.oppomobile.com/bbs/forum.php?mod=viewthread&tid=4815)
- vivo 官方公开资料可以确认 Jovi 服务平台方向，但没有找到当前可核验的第三方原生 App 文字提交、助手执行和结果回调 SDK/Intent 文档。[vivo 开发者大会资料](https://dev.vivo.com.cn/vdc2019/index.html?s=2)

### 对本项目的落地判断

可以做的只有两种：

1. 灵动岛自己完成 ASR 和本地意图解析，直接控制本 App；这是最稳定、最容易适配国产 ROM 的方式。
2. 让灵动岛尝试打开系统助手，但不能可靠传入已经识别的文字，也不能依赖系统助手返回结果；只能作为“打开助手”的兼容性尝试，不能作为核心流程。

如果一定要使用系统助手的自然语言能力，正式路径是申请对应厂商的 Skill/Agent 合作能力，或者让本 App 自己申请成为默认助手。这两条都不是简单的第三方 App 文本转发，而且都会引入平台审核、用户授权和更高的系统集成风险。
