# AGENTS.md

Android 悬浮灵动岛 + OpenGL ES 3.0 玻璃球（包名 `com.cxcboss.glassorb`）。**开始工作前先读 [docs/HANDOFF.md](docs/HANDOFF.md)。**

- 构建：`./gradlew assembleDebug lintDebug --project-cache-dir /tmp/glass-orb-gradle-project-cache`（JDK 17+）。构建目录被重定向到系统临时目录，别在 `app/build` 找 APK。
- 不要删除或覆盖用户未提交的改动；不要提交、推送或合并，除非用户明确要求。
- 悬浮层的窗口创建顺序、同帧几何快照、物理竖屏语义是硬约束（见 HANDOFF 第 6 节）；改动画/旋转/模糊必须说明哪些真机验收项未完成。
- 设置页是平台 View 手写的 iOS 26 风格 + 液态玻璃（`ui/ios`、`ui/glass`），**玻璃背景源子树里不能放玻璃视图**。
- 新增设置文案必须补英文映射（`ui/SettingsText.kt`）；新增配置字段要同步 `OrbConfig` 归一化与 `OrbConfigJson`。
- 许可、固定参考提交与应用内声明必须保留。
