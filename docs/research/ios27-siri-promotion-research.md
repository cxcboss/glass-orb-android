# iOS 27 Siri 安卓视觉体验推广研究

## 研究结论

这款应用的传播重点不应是“又一个灵动岛工具”，而应是：安卓用户也可以先体验 iOS 27 新 Siri 的顶部发光药丸、玻璃球和动态展开感，不需要为了一个视觉效果立刻换成 iPhone。

目标用户的主要动机有三层：

1. 对 iOS 27 新 Siri 的新鲜感和好奇心，尤其是从灵动岛展开的发光药丸/光球效果。
2. 安卓用户对“我也想要这个效果”的补偿心理，希望保留自己的安卓手机，又获得一眼可见的新鲜感。
3. 喜欢折腾手机外观和交互的用户，希望安装门槛低、效果马上可见、还能自己调整颜色和光影。

## 官方与一手资料

- Apple 在 WWDC26 新闻稿中把 iOS 27 的 Siri AI 定义为全新的系统级 Siri，并强调其与 Apple Intelligence、系统和应用能力的结合：<https://www.apple.com/newsroom/2026/06/apple-unveils-next-generation-of-apple-intelligence-siri-ai-and-more/>
- Apple 的 Siri 人机界面指南提到，用户可以通过灵动岛等系统入口与 Siri 交互，并将 Siri AI 与 Apple Intelligence 的上下文能力连接起来：<https://developer.apple.com/design/human-interface-guidelines/siri>
- Apple 的 iOS 27 页面将 Siri AI 描述为具有全新能力的对话式助手，同时把新版系统的重点放在新的设计语言与系统级智能上：<https://www.apple.com/os/ios/>

## 视觉关键词与市场表达

- MacRumors 对 iOS 27 测试版的描述集中在“从 Dynamic Island 展开”“发光的药丸形光球”“取代此前沿屏幕边缘游走的光效”：<https://www.macrumors.com/2026/06/16/iphone-18-could-make-siri-a-circle/>
- cnBeta 的中文标题和正文使用了“发光药丸”“光球动画”“融入灵动岛”“遮蔽开孔”等高感知词汇，适合用来理解中文用户如何快速识别这个视觉卖点：<https://www.cnbeta.com.tw/articles/tech/1565966.htm>
- dynamicSpot 的应用介绍先用“让安卓也拥有动态通知岛”建立想象，再写“点击展开、动态动画、个性化颜色、音乐可视化”等可见动作，最后补充无障碍服务和隐私说明：<https://play.google.com/store/apps/details?id=com.jamworks.dynamicspot>
- 同类安卓灵动岛应用的中文介绍普遍强调“安装后马上体验”“免费”“自定义”“通知/音乐/充电”等结果型表达，而不是先讲实现原理。此次文案应借鉴这种顺序，但把核心卖点收窄到 iOS 27 Siri 的视觉体验。

## 本项目可对外承诺的内容

- 应用名：灵动玻璃球。
- 当前版本：2.0.28，支持 Android 10 及以上，要求 OpenGL ES 3.0。
- 可见效果：顶部黑色胶囊、展开为玻璃球、拖动形变、上滑收起、自动收起、全屏隐藏/恢复、动态高光、颜色、光带、粒子和背景效果。
- 交互方向：可选系统助手窗口联动，让支持的设备在唤起系统助手时触发玻璃球展开；这是视觉联动，不应宣传成安卓获得了 Siri AI 本体。
- 安装与兼容：侧载应用，需要悬浮窗权限；Android 12 及以上当前还需要无障碍服务，Android 13 及以上可能请求通知权限。不同厂商对悬浮窗、后台和刷新率的限制会造成体验差异。
- 发布边界：当前是免费、非商业侧载项目，不应写成已经上架应用商店的正式商业软件。

## 文案策略

开头先提出用户看到 iOS 27 新 Siri 后的“想要但不想换机”心理；中段展示安装后的第一眼效果和几个动作；再说明这是视觉与动效体验，不是 Siri AI 移植；最后用“微信公众号回复：Siri下载”作为唯一明确转化动作。权限说明放在后段，先建立兴趣再处理用户对悬浮窗/无障碍权限的顾虑。
