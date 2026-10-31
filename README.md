# 灵动玻璃球

一个原生 Android 悬浮灵动岛与 OpenGL ES 3.0 玻璃球效果实验。项目当前以免费、非商业侧载应用的形式维护。

## 当前版本

- 版本：`2.0.29`（`versionCode 39`）
- 包名：`com.cxcboss.glassorb`
- 系统：Android 10（API 29）及以上
- 图形：OpenGL ES 3.0
- 安装包：[dist/灵动玻璃球-2.0.29.apk](dist/灵动玻璃球-2.0.29.apk)（Debug 签名，约 10 MB）

当前实现包含：

- 固定在真实手机顶部中央语义的灵动岛，支持四个物理旋转方向。
- 点击展开、拖动形变、上滑收起、自动收起和全屏隐藏/恢复动画。
- 活跃动画期间请求 120 Hz；稳定灵动岛状态停止持续渲染以降低功耗。
- “下层模糊”“下层变暗”“玻璃球模糊”三套独立背景效果；两种模糊互斥。
- 复刻 iOS 26“设置”的设置界面：分组列表、二级页头卡、底部玻璃搜索（可搜到每一项设置）、长按恢复默认的玻璃菜单；液态玻璃在 API 33+ 带边缘镜头，31–32 为模糊，更低自动降级；纯平台 View，无 Material/Compose；跟随系统切换中文或英文。
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
- [对接文档](docs/HANDOFF.md)：**先读这个**——基线、代码地图、对内接口、修改配方、约束与交付检查表。
- [设置页设计说明](docs/SETTINGS_UI.md)：iOS 26 风格与液态玻璃的实现、组件与取舍。
- [审计与改进计划](docs/AUDIT.md)：缺陷、性能项和路线图。
- [更新日志](docs/CHANGELOG.md)。
- [构建与发布](docs/BUILD_AND_RELEASE.md)：构建、签名、版本与 APK 核验流程。

## 许可与来源

玻璃效果与动画参考仓库受非商业学习许可约束，本项目不能据此直接用于商业发行。请保留 [LICENSE](LICENSE)、[NOTICE.md](NOTICE.md)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 及应用内声明；具体固定参考提交和第三方来源见这些文件。
