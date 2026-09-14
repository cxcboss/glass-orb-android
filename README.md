# 灵动玻璃球

一个原生 Android 悬浮灵动岛与 OpenGL ES 3.0 玻璃球效果实验。项目当前以免费、非商业侧载应用的形式维护。

## 当前版本

- 版本：`2.0.28`（`versionCode 38`）
- 包名：`com.cxcboss.glassorb`
- 系统：Android 10（API 29）及以上
- 图形：OpenGL ES 3.0
- 安装包：[dist/灵动玻璃球-2.0.28.apk](dist/灵动玻璃球-2.0.28.apk)

当前实现包含：

- 固定在真实手机顶部中央语义的灵动岛，支持四个物理旋转方向。
- 点击展开、拖动形变、上滑收起、自动收起和全屏隐藏/恢复动画。
- 活跃动画期间请求 120 Hz；稳定灵动岛状态停止持续渲染以降低功耗。
- “下层模糊”“下层变暗”“玻璃球模糊”三套独立背景效果；两种模糊互斥。
- 原生 Material 3 Views 设置界面，跟随系统切换中文或英文。
- 可选无障碍服务，用于精确触控区域和白名单系统助手窗口联动。

## 构建

需要 JDK 17 和 Android SDK 37。首次使用请由 Android Studio 生成 `local.properties`，或写入本机 SDK 路径。

```sh
./gradlew testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug \
  --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

本项目把 Gradle 构建目录重定向到了系统临时目录，不能以 `app/build/outputs` 判断最新产物。完整构建、核验和发包方法见 [构建与发布](docs/BUILD_AND_RELEASE.md)。

## 文档

- [项目架构](docs/ARCHITECTURE.md)：模块、窗口层级、渲染与配置数据流。
- [已知问题](docs/KNOWN_ISSUES.md)：厂商高刷新、系统模糊、权限和发布限制。
- [AI 接手说明](docs/AI_HANDOFF.md)：关键约束、常见修改入口和交付检查表。
- [构建与发布](docs/BUILD_AND_RELEASE.md)：构建、签名、版本与 APK 核验流程。

## 许可与来源

玻璃效果与动画参考仓库受非商业学习许可约束，本项目不能据此直接用于商业发行。请保留 [LICENSE](LICENSE)、[NOTICE.md](NOTICE.md)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 及应用内声明；具体固定参考提交和第三方来源见这些文件。
