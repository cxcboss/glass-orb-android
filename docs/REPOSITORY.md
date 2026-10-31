# 仓库与目录管理

本文说明仓库里每个目录放什么、哪些文件不入库、测试/调试会在哪里留下垃圾、如何清理，以及分支、远程与发版的约定。

## 1. 目录结构

```text
.
├─ AGENTS.md                 AI 接手入口（指向 docs/HANDOFF.md）
├─ README.md                 项目简介、当前版本、文档索引
├─ LICENSE / NOTICE.md / THIRD_PARTY_NOTICES.md   许可与来源声明（不可删改为更宽松）
├─ build.gradle.kts          根构建：把构建目录重定向到系统临时目录
├─ settings.gradle.kts / gradle.properties / gradle/ / gradlew*   Gradle Wrapper（9.6.0）
├─ app/
│  ├─ build.gradle.kts       版本号、SDK、依赖
│  └─ src/
│     ├─ main/               应用代码、资源、着色器（assets/shaders）、应用内声明（assets/NOTICE.txt）
│     ├─ test/               JVM 单元测试
│     └─ androidTest/        设备/模拟器测试（文案按中文写，需在 zh-CN 环境运行）
├─ licenses/                 第三方许可证原文，构建时打包进 APK assets
├─ dist/                     发布说明 + SHA256SUMS.txt（APK 只作为 GitHub Release 附件，不入库）
├─ docs/                     全部文档，见下方索引
│  └─ research/              推广/调研类资料（不影响构建）
└─ scripts/
   └─ clean.sh               清理本地构建产物、缓存与测试垃圾
```

文档索引：[对接文档](HANDOFF.md) · [项目架构](ARCHITECTURE.md) · [设置页设计说明](SETTINGS_UI.md) · [构建与发布](BUILD_AND_RELEASE.md) · [已知问题](KNOWN_ISSUES.md) · [审计与改进计划](AUDIT.md) · [更新日志](CHANGELOG.md) · 本文。

## 2. 不入库的本地文件

`.gitignore` 已覆盖以下内容，任何时候都可以删除，下次构建会重新生成：

| 路径 | 来源 |
| --- | --- |
| `local.properties` | Android SDK 路径（本机专属，**不要删**，只是不入库） |
| `$TMPDIR/glass-orb-android-build/` | 所有构建输出（APK、中间产物、lint 报告、测试结果） |
| `/tmp/glass-orb-gradle-project-cache/` | 按文档用 `--project-cache-dir` 时的 Gradle 项目缓存 |
| `.gradle/`、`.kotlin/`、`build/`、`app/build/` | 未带 `--project-cache-dir` 构建或 IDE 同步时生成 |
| `.idea/`、`*.iml`、`captures/` | Android Studio |
| `.DS_Store`、`*.hprof`、`*.log`、`*.apk.zip` | Finder、内存转储、日志、临时压缩包 |
| `.omx/`、`.claude/`、`.codex/`、`.cursor/`、`.superpowers/` | AI 工具的本地状态 |

## 3. 清理

```sh
scripts/clean.sh --dry-run   # 先看会删什么
scripts/clean.sh             # 删除上表中的产物（不碰已跟踪文件、dist/、local.properties）
```

`--stop-daemons` 会额外执行 `./gradlew --stop`。注意它会停掉本机**所有**使用 Gradle 9.6 的守护进程，同时在跑别的项目构建时不要加。

`~/.gradle/caches` 是全机共享缓存，不属于本项目，不在清理范围内。

### 3.1 测试与调试会在仓库外留下的东西

| 位置 | 内容 | 清理方式 |
| --- | --- | --- |
| 模拟器 / 真机 | 安装的 `com.cxcboss.glassorb`、`wm size`/`wm density` 覆盖、`cmd uimode night`、`cmd locale set-app-locales` | `adb -s <序列号> uninstall com.cxcboss.glassorb`；`adb shell wm size reset`；`adb shell wm density reset`（或恢复原值）；`adb shell cmd uimode night no` |
| 运行中的模拟器 | 无窗口启动的 `emulator` 进程 | `adb -s emulator-<端口> emu kill` |
| Gradle 守护进程 | 构建后常驻内存 | `./gradlew --stop`（见上文注意事项） |

操作 adb 时始终带 `-s <序列号>`，避免误操作连接着的个人手机。

## 4. 分支与远程

远程：`origin = https://github.com/cxcboss/glass-orb-android`（**公开仓库**，默认分支 `main`）。

| 分支 | 状态 |
| --- | --- |
| `main`（远程默认） | 发布线，当前 2.0.29（Release `v2.0.29`）。所有发版从这里打标签 |
| `codex/independent-glass-orb` | 2.0.29 的开发线，已通过合并提交并入 `main`，与 `main` 指向同一提交 |

两条线曾在 2026-09 初分叉（`main` 期间被重写过），2.0.29 用普通合并（`--no-ff`）把开发线并回 `main`，没有强推、没有改写已发布历史。之后建议直接在 `main` 上开短期功能分支，合并后删除。**不要强推 `main`**，它是公开仓库的默认分支。

标签：`v2.0.11`、`v2.0.29`，均对应 GitHub Release。

日常同步：

```sh
git fetch --prune origin
git status -sb                       # 确认 ahead/behind
git push origin main
```

## 5. 版本与发布

1. 改 `app/build.gradle.kts` 的 `versionCode`（每次 +1）与 `versionName`。
2. 构建并核验 APK（见 [构建与发布](BUILD_AND_RELEASE.md)）。
3. APK 复制为 `dist/glass-orb-android-X.Y.Z.apk`（被忽略，不入库），更新 `dist/SHA256SUMS.txt`、`dist/README.md`、根 `README.md`、`docs/CHANGELOG.md`，提交并推送 `main`。
4. 打标签 `vX.Y.Z` 并推送，用 `gh release create vX.Y.Z dist/glass-orb-android-X.Y.Z.apk --title "灵动玻璃球 X.Y.Z" --notes-file <说明>` 发布。Release 是公开内容，发布前确认许可声明齐全。

## 6. 提交约定

- 提交信息用约定式前缀：`feat:` / `fix:` / `perf:` / `docs:` / `chore:` / `build:`，正文写清楚“为什么”。
- 不要提交任何 APK（`.gitignore` 已忽略 `dist/*.apk`），也不要提交 `.apk.zip`、构建目录、日志或截图。
- 二进制体积：`.git` 约 300 MB，主要来自历史里的旧 APK、ASR 模型与 `.aar`。瘦身只能重写历史（`git filter-repo`），会改变全部提交 ID 并需要强推，必须先征得仓库所有者同意。
- `gradlew.bat` 按 `.gitattributes` 以 LF 入库、检出为 CRLF；不要手动改它的换行。

## 7. 例行整理清单

- [ ] `scripts/clean.sh`
- [ ] `git status --short` 为空，或只剩有意保留的改动
- [ ] `git fetch --prune origin`，本地分支与远程 ahead/behind 符合预期
- [ ] 本地 `dist/` 里的 APK 与 Release 附件一致：`(cd dist && shasum -a 256 -c SHA256SUMS.txt)` 通过
- [ ] 测试用过的模拟器/设备已卸载测试包、还原显示与语言设置
- [ ] 文档中的版本号、APK 文件名与 `app/build.gradle.kts` 一致
