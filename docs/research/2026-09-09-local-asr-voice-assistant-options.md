# 本地语音助手实现方案调研

> 调研日期：2026-09-09  
> 目标：展开玻璃小球后录音，用响度驱动波纹；用户短暂停止说话后结束本次录音；本地识别内容并匹配简单意图。  
> 约束：免费、低资源、尽可能本地、覆盖尽可能多的 Android 手机，不引入剪贴板或扩大无障碍能力。

## 结论

最适合当前项目的不是接入小米/OPPO/vivo 的系统助手，也不是引入大模型，而是做一个“本地语音命令执行器”：

1. 展开玻璃小球即进入一次短语音会话。
2. 录音期间根据麦克风响度驱动玻璃球波纹。
3. 通过 VAD/静音计时判断用户已经说完。
4. 将这一小段语音转成文本。
5. 用本地关键词、同义词和参数解析匹配有限的 App 意图。
6. 展示执行结果；暂不需要云端大模型，也不需要系统助手回调。

“识别用户说话的声音”在第一版应理解为检测是否在说话、响度和说话结束，而不是识别说话人身份。说话人识别是另一套模型和权限边界，当前需求没有必要引入。

## 方案比较

| 方案 | 本地能力 | 适配面 | 资源/包体 | 结论 |
| --- | --- | --- | --- | --- |
| Android `SpeechRecognizer` | API 31+ 可优先使用设备端识别；旧设备是否真正离线由识别服务决定 | 最好，系统已适配设备 | App 集成最轻 | 作为首选入口，但不能承诺所有手机完全离线 |
| sherpa-onnx 流式 ASR + VAD | 完全本地，支持流式识别、VAD、中文和 Android；模型独立于系统服务 | 较好，官方提供 Android 及多种 ABI | 需要 native 库和模型，包体/内存较大 | 严格本地时的首选后端 |
| Vosk 小型中文模型 | 完全本地，Android 支持成熟，代码 Apache-2.0 | 较好 | 小模型相对轻量，但中文识别能力依赖模型 | 更轻量的离线备选 |
| whisper.cpp | 完全本地，Android 和 VAD 支持，识别泛化能力较强 | 较好 | CPU、内存和模型体积更高，实时低功耗更难 | 暂不作为第一版，后续可做高准确率模式 |

Android 官方的 `SpeechRecognizer` 要求 `RECORD_AUDIO`，并明确说明具体实现可能把音频流式传给远程服务；API 31 起可以通过 `isOnDeviceRecognitionAvailable()` 检查设备端识别，并使用 `createOnDeviceSpeechRecognizer()`，但设备没有本地识别服务时会失败。[SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer)

`EXTRA_PREFER_OFFLINE` 只能表达“优先离线”的意愿，官方也说明识别服务可能忽略它。因此它适合覆盖多数手机，不适合单独作为“绝不联网”的保证。[RecognizerIntent API](https://developer.android.com/reference/android/speech/RecognizerIntent)

sherpa-onnx 官方仓库和 Android 文档公开了本地流式 ASR、VAD、中文模型以及 Android 的 arm64-v8a、armeabi-v7a 等构建方向。[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)、[Android APK 文档](https://k2-fsa.github.io/sherpa/onnx/android/apk.html)

Vosk 官方仓库明确提供离线语音识别 API、Android 支持和中文支持，代码许可证为 Apache-2.0。[Vosk API](https://github.com/alphacep/vosk-api)

whisper.cpp 官方仓库提供 Android 示例、C/C++ API 和 VAD 支持，代码许可证为 MIT，但模型的许可证和运行成本仍需逐个确认。[whisper.cpp](https://github.com/ggml-org/whisper.cpp)

## 推荐落地架构

### 第一层：统一的语音引擎接口

先定义一个 App 内部接口，不让 UI 和具体识别引擎耦合：

```text
VoiceEngine
  start(onLevel, onPartialText, onFinished, onError)
  stop()
  release()
```

后端顺序建议如下：

1. Android 12/API 31 及以上，且 `isOnDeviceRecognitionAvailable()` 为真：使用设备端 `SpeechRecognizer`。
2. 设备端不可用时：使用 sherpa-onnx 流式中文模型 + VAD；如果希望进一步压缩资源，可改用 Vosk 小型中文模型。
3. 如果项目最终要求“任何情况下音频都不能离开手机”，则跳过系统识别服务，直接从第一天使用 sherpa-onnx 或 Vosk。

这里的两条路线需要提前选定隐私承诺：第一条是“优先本地、必要时兼容系统识别服务”，第二条才是“严格全本地”。不能把 Android 系统识别服务在所有厂商设备上都描述成绝对离线。

### 第二层：一次性短语音会话

建议状态机：

```text
收起
  -> 玻璃小球展开
  -> 正在聆听
  -> 检测到说话
  -> 短暂静音，结束录音
  -> 识别中
  -> 执行意图并展示结果
  -> 自动收起或等待下一次点击
```

如果采用自有 ASR 后端，使用单一路 `AudioRecord`：同一段 PCM 同时用于响度、VAD 和识别，避免两个组件同时争抢麦克风。Android 官方 `AudioRecord` 提供应用主动读取 PCM 的能力。[AudioRecord API](https://developer.android.com/reference/android/media/AudioRecord)

建议初始参数以真实设备体验为准，而不是写死成协议：

- 16 kHz、单声道、16-bit PCM；
- 开始时用约 200–300 ms 建立噪声基线；
- 连续低于“噪声基线 + 语音阈值”约 600–900 ms，判定用户说完；
- 保留少量前置和后置音频，避免吞掉第一个字或最后一个字；
- 单次会话设置 10–15 秒上限，避免异常情况下长期占用麦克风；
- 没检测到有效语音时直接提示“没有听清”，不进入意图解析。

如果采用系统 `SpeechRecognizer`，可使用 `RecognitionListener` 的 `onRmsChanged()` 驱动波纹，使用 `onPartialResults()` 展示中间文本，使用 `onResults()` 取得最终文本；但端点和回调时序由系统识别服务控制，机型之间会有差异。[RecognitionListener API](https://developer.android.com/reference/android/speech/RecognitionListener)

### 第三层：响度驱动玻璃球波纹

当前项目已有 `FrequencyBands` 和渲染快照输入，后续可以把录音时的实时响度接到现有波纹管线：

1. 对 PCM 计算短窗口 RMS/峰值。
2. 用噪声基线扣除环境底噪。
3. 通过 EMA 平滑、限幅和轻微衰减得到 0–1 的视觉响度值。
4. 映射到低频/中频/高频三个波段：低频负责主体起伏，中频负责波纹扩散，高频保留较小比例的细节。
5. 录音停止后让响度值快速但平滑地归零，再恢复待机动画或进入收起动画。

这样“说得越大声波纹越大”，但不会因为瞬时爆音导致波纹跳变，也不会让环境底噪持续撑大图形。响度只用于展示，不应该直接作为识别结果或权限判断。

### 第四层：本地有限意图解析

第一版不需要 NLU 模型。把可执行能力定义成注册表，每个意图包含触发词、参数提取和执行器，例如：

```text
展开/收起玻璃球
打开/关闭扩大触控区域
调整玻璃球大小
调整胶囊宽度或高度
打开/关闭向下展开
打开设置/打开关于
停止/取消本次语音
```

解析顺序建议为：文本规范化 → 同义词替换 → 意图匹配 → 参数提取 → 执行前确认高风险操作 → 展示结果。没有匹配时给出“暂不支持这个指令”，不要把不确定文本强行当成命令。

对本 App 而言，这种有限意图表比引入云端 LLM 更快、更稳定、更省电，也更容易在国产 ROM 上说明权限和隐私边界。后续如果要支持问答，再单独增加可选网络能力，不与基础控制功能绑定。

## 权限与国产 ROM 适配边界

本功能真正新增的敏感权限只有 `RECORD_AUDIO`，并且应在用户第一次展开语音球、明确看到用途说明时请求；不需要剪贴板权限，也不需要扩大现有无障碍服务的能力。

如果录音在前台服务中持续，Android 14 起需要为服务声明 `microphone` 类型和 `FOREGROUND_SERVICE_MICROPHONE`，同时用户必须已经授予 `RECORD_AUDIO`。Android 官方还规定麦克风前台服务受“仅在使用中”限制，不能从后台或开机广播直接启动麦克风型前台服务。[前台服务类型官方文档](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)

因此实现时应遵守：

- 只在用户主动展开玻璃小球时开始录音；
- 录音完成、超时或取消后立即释放 `AudioRecord`/识别器；
- 不在开机自启动流程中申请或打开麦克风；
- 保持现有无障碍服务最小能力，不用它读取其他 App 内容或监听按键；
- 在权限被拒绝、麦克风被占用、系统识别服务不可用时，明确显示失败原因并让玻璃小球恢复正常状态。

## 最终建议

推荐采用“系统设备端识别优先 + sherpa-onnx/Vosk 本地兜底 + 本地规则意图解析”的混合架构。它能用最少的首版代码覆盖较多设备，同时保留严格全本地的升级路径。

如果用户把“尽可能本地”理解为“允许系统识别服务在个别设备上联网”，选混合架构；如果理解为“任何音频都不能出设备”，则直接选 sherpa-onnx/Vosk，不接 `SpeechRecognizer` 的通用回退。

本调研不建议把音频伪装、注入或转交给小爱、小布、Jovi，也不建议依赖未公开的 OEM 包名、Intent、反射接口或系统权限。那条链路没有公开、稳定、可验证的结果回调契约。

## 性能要求补充

sherpa-onnx 没有一个统一的“最低手机配置”：运行速度和识别效果主要由 ASR 模型、是否 int8 量化、线程数、输入音频质量和手机 CPU 决定。官方的小模型列表中，中文 `zipformer-zh-14M` 标注为适合 Cortex-A7 CPU；官方 Android 文档也提供了本地实时识别示例。因此，采用轻量流式模型时，Android 10 时代的中低端 ARM 手机也有机会实时运行，但不能用大模型的体验推断小模型，反之亦然。[小型流式模型列表](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/small-online-models.html)、[Android 实时识别文档](https://k2-fsa.github.io/sherpa/onnx/android/index.html)

官方构建示例同时展示了数百 MB 级的双语模型文件，说明包体、首次加载内存和模型大小可能远高于 14M 小模型；高准确率模型不应直接塞进首版 APK。[Android 构建示例](https://github.com/k2-fsa/sherpa/blob/master/docs/source/onnx/android/build-sherpa-onnx.rst)

针对当前玻璃球 App 的工程分级建议：

- **最低尝试档**：Android 10/API 29、ARMv7 或 ARM64、约 2 GB RAM、四核入门 CPU；只使用小型 int8 流式模型，识别时降低线程数并停止复杂待机动画。可以尝试运行，但不能承诺所有机型不卡顿。
- **推荐档**：ARM64、4 GB RAM 或以上、中端四核/八核 CPU；小型 int8 模型应能在玻璃球动画同时运行时保持更稳定的交互。
- **高准确率档**：使用更大的中文模型，准确率和方言覆盖可能更好，但会显著增加 APK/模型体积、内存占用、首帧加载时间和发热，不适合第一版默认配置。

QNN/NPU 不是覆盖多数手机的前提。官方 QNN Android 示例要求 Qualcomm SM8350 或更强芯片，因此第一版应以 CPU + ARM64/ARMv7 通用路径为主，把 NPU 作为特定设备优化，而不是基础依赖。[官方 QNN Android 文档](https://k2-fsa.github.io/sherpa/onnx/android/apk-qnn-simulate-streaming-asr.html)

最终必须以目标手机实测确认。编译成功或在开发机上识别很快，都不能证明玻璃球渲染、录音、VAD 和 ASR 并发时在国产 ROM 上仍然稳定；应记录端到端延迟、持续 CPU、峰值内存、温升和掉帧。

## 面向当前需求的设备操作可行性

### 手电筒

可以通过 `CameraManager.setTorchMode(cameraId, enabled)` 直接开关闪光灯，不需要打开相机预览；官方 API 从 Android 6.0/API 23 起提供该能力。实现时需要选择支持闪光灯的后置摄像头，并处理摄像头被其他应用占用、摄像头离线或设备不具备闪光灯等失败情况。[CameraManager API](https://developer.android.com/reference/android/hardware/camera2/CameraManager)

该接口属于相机能力，当前 Android API 源码在 `setTorchMode` 上声明了 `CAMERA` 权限要求。因此，只有用户第一次使用“打开手电筒”时才申请 `CAMERA`，不能在启动悬浮球或开机自启动时提前申请；拒绝后应让玻璃球显示“需要相机权限才能控制手电筒”，而不是反复弹权限框。[Android CameraManager 源码](https://android.googlesource.com/platform/frameworks/base/+/ee540c32457f6228a6eef47ec1fb6828a9d84126/core/java/android/hardware/camera2/CameraManager.java)、[CAMERA 权限说明](https://developer.android.com/reference/android/Manifest.permission#CAMERA)

### 音量

第一版只控制媒体音量，使用 `AudioManager` 的 `adjustStreamVolume` 或 `setStreamVolume` 实现“调大、调小、静音、恢复”等动作。通常不需要新的危险运行时权限，但要检查设备是否是固定音量设备，并避免把“音量”不明确地解释成铃声、通知音量或勿扰模式。官方文档还提醒，这类全局音量 API 原本主要面向平台级音频管理者，因此要限制为明确、少量的媒体音量操作，并对国产 ROM 的拒绝或无效结果做失败提示。[AudioManager API](https://developer.android.com/reference/android/media/AudioManager)

建议的第一版语义是：

- “声音大一点/音量调高” = 媒体音量增加一步；
- “声音小一点/音量调低” = 媒体音量减少一步；
- “静音/把声音关掉” = 媒体音量设为 0；
- “恢复声音/取消静音” = 恢复本次会话记录的音量；
- “铃声、通知、勿扰”只有出现明确对象词时才进入后续版本，不从模糊语句推断。

### 打开指定 App

不建议申请 `QUERY_ALL_PACKAGES`，也不应为了模糊匹配扫描用户手机上的全部应用。Android 11/API 30 起会过滤应用可见性；设置页应由用户主动选择常用 App，保存“用户说法/别名 + 明确 packageName”，执行时通过 `PackageManager` 获取该包的启动 Intent。这样既能覆盖“打开微信/启动抖音”等常见说法，也不会扩大应用可见范围。[Android 应用可见性](https://developer.android.com/training/package-visibility)、[PackageManager 启动 Intent](https://developer.android.com/reference/android/content/pm/PackageManager#getLaunchIntentForPackage(java.lang.String))

找不到已配置的 App、包被卸载或没有可启动入口时，应该停留在完成/失败反馈状态，显示“没有找到这个应用”，不自动尝试相似 App，避免误打开其他应用。以后若允许用户从系统分享/选择器中添加应用，也只记录用户明确选择的包名。

## 语音交互状态机

用户补充的交互可以明确成以下状态，不与现有玻璃球的待机波纹混用：

```text
收起
  -> 展开并开始录音（Listening）
  -> 检测到有效说话（Recording）
  -> 用户点击“开始分析” 或 检测到短暂静音（Analyzing）
  -> 玻璃球显示转动的分析动画
  -> 得到文本并匹配指令（Completed / Failed）
  -> 显示完成或失败
  -> 执行明确动作
  -> 收起
```

具体行为建议如下：

1. 只有从收起状态点击展开才申请录音权限并开始本次会话；未展开时不监听麦克风。
2. 展开后先建立约 200–300 ms 的环境噪声基线。没有检测到有效语音时，不因为短暂静音立即分析；需要用户点击或达到单次会话上限后提示“没有听清”。
3. 检测到说话后，连续约 700–900 ms 低于动态阈值，自动结束录音并进入分析。这个计时要允许用户在句子间自然停顿，阈值和超时应做成可调常量，后续按真机反馈调整。
4. 用户点击“开始分析”时先停止 `AudioRecord`/识别器，再进入分析态；分析态不再读取麦克风，避免录音线程与转动动画争抢资源。
5. 分析态沿用现有 `Thinking` 渲染通道：冻结或淡出语音波纹，切换为低成本旋转球/点阵动画；不要启动第二套重型渲染循环。
6. 分析完成后先显示一个短暂的完成态，再执行动作并收起。执行失败时保持玻璃球展开，显示失败原因和“重试/取消”，不要在动作未确认成功时直接收起。

首版建议支持“点击玻璃球主体开始分析”的明确手势，同时在球内提供轻量的“开始分析”提示；录音期间只展示波纹和可选的部分识别文字，不根据部分文字执行动作。自动分析只能使用最终文本，避免 ASR 中间结果把半句话误判成命令。

## 模糊语句的安全解析策略

有限功能不需要通用 NLU 模型，但需要一个有置信度和冲突处理的规则解析器：

1. **文本规范化**：去除标点、空格和口头填充词；统一简繁、阿拉伯数字与中文数字；修正常见 ASR 近音词，例如“手电/手电筒/闪光灯/照明”。
2. **槽位识别**：分别识别动作词（打开、关闭、调高、调低）、对象词（手电筒、音量、微信）和参数（一步、两格、百分之五十）。不能只用整句相似度。
3. **候选打分**：动作词、对象词、参数完整度加分；互相冲突的词扣分。高置信度才执行，中间置信度在玻璃球内显示确认问题，低置信度要求重说。
4. **上下文承接**：同一次会话保留上一次目标，允许“再大一点”“再调小些”等短句沿用目标；会话结束后清除，避免下一次误继承。
5. **别名白名单**：打开 App 只匹配用户在设置中选过的 App 及其别名；不能因为“像某个应用”就自行搜索并启动。
6. **安全执行**：最终文本为空、没有唯一候选、权限不可用、目标状态未知或执行器返回失败时，停留在失败态并给出原因，不执行猜测结果。

例如，“帮我把灯打开一下”可以在“打开 + 灯/手电/照明”词典内判为手电筒；“声音弄大点”可以判为媒体音量加一步；“打开那个聊天软件”如果没有唯一的用户别名，就必须追问或提示用户在设置中绑定，而不能直接启动微信或其他 App。

## 首版范围与权限清单

建议首版只实现三个执行器：`TorchAction`、`MediaVolumeAction`、`LaunchAppAction`。所需敏感能力为：

- `RECORD_AUDIO`：仅在用户点击展开语音球时请求并使用；
- `CAMERA`：仅在用户第一次使用手电筒指令时按需请求；
- 不添加剪贴板权限；
- 不添加 `QUERY_ALL_PACKAGES`；
- 不扩展现有无障碍服务的读取、手势或截图能力。

后续接入 sherpa-onnx 时，建议把识别模型作为独立可替换的本地后端：首版用轻量中文流式模型和单路 PCM，先保证短命令的端点、耗电和误执行控制，再逐步增加模型大小或更复杂的自然语言能力。这样即使模型初始化失败，设置页和玻璃球本身仍可正常使用。

## 本轮需求的实施决策

结合“尽可能本地”和“直接用一个小模型”的要求，实施时默认采用严格本地路线：`AudioRecord` + VAD + sherpa-onnx 小型中文流式模型 + 本地规则意图解析，不把 `SpeechRecognizer` 作为隐式联网回退。这样用户可以明确知道音频不会为了识别而交给系统或云端服务；代价是需要随 App 提供或下载模型，并针对目标国产 ROM 做一次端到端性能验证。

首版只承诺短句命令，不承诺开放式聊天或复杂问答。能稳定覆盖的范围是：手电筒开关、媒体音量增减/静音/恢复、用户绑定的 App 启动，以及后续少量明确的设置项。模糊语句采用“高置信度执行、中置信度确认、低置信度重说”的策略；任何无法唯一确定目标的文本都不执行。
