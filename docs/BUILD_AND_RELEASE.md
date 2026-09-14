# 构建与发布

## 环境

- JDK 17
- Android SDK Platform 37
- Gradle Wrapper（使用仓库中的 `./gradlew`）
- Android 设备最低 API 29，且必须支持 OpenGL ES 3.0

`local.properties` 是本机文件，不提交到 Git。示例：

```properties
sdk.dir=/path/to/Android/sdk
```

## 完整验证

```sh
./gradlew testDebugUnitTest lintDebug compileDebugAndroidTestKotlin assembleDebug \
  --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

这条命令验证 JVM 单元测试、Lint、设备测试源码编译和 Debug APK 构建。它不会自动执行 `androidTest`；设备测试需要另外连接设备或启动模拟器。

## 查找真实 APK

根 `build.gradle.kts` 把所有构建输出放到 Java 临时目录，先查询：

```sh
./gradlew properties --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache \
  | rg 'buildDir|buildDirectory'
```

当前默认位置通常类似：

```text
/var/folders/.../T/glass-orb-android-build/app/outputs/apk/debug/app-debug.apk
```

不要使用仓库内可能残留的 `app/build/outputs/apk/debug/app-debug.apk`。

## APK 核验

使用当前 Android SDK 中最新的 Build Tools：

```sh
aapt2 dump badging app-debug.apk | head -n 3
apksigner verify --verbose --print-certs app-debug.apk
shasum -a 256 app-debug.apk
unzip -t app-debug.apk
```

至少确认：

- package 为 `com.cxcboss.glassorb`。
- `versionName`、`versionCode` 与 `app/build.gradle.kts` 一致。
- APK 压缩结构完整。
- 签名校验成功，并清楚记录它是 Debug 还是 Release 签名。
- 文件修改时间来自本次构建。

## 更新交付目录

`dist` 只保留一个 APK，统一命名为：

```text
灵动玻璃球-<versionName>.apk
```

替换 APK 后同步更新：

- `dist/SHA256SUMS.txt`
- `dist/README.md`
- 根 `README.md` 中的版本和链接
- `app/build.gradle.kts` 的 `versionCode` / `versionName`

提交前运行：

```sh
find dist -maxdepth 1 -type f -name '*.apk' -print
(cd dist && shasum -a 256 -c SHA256SUMS.txt)
git diff --check
git status --short
```

## 安装

```sh
adb install -r dist/灵动玻璃球-2.0.28.apk
```

Android 12+ 当前需要同时开启悬浮窗权限和应用的无障碍服务。Android 13+ 还会请求通知权限。厂商系统可能需要额外允许后台运行或开机自启。

## 发布边界

当前仓库只配置 Debug 侧载流程，没有正式 Release 签名。由于参考代码受非商业许可约束，即使技术上增加 Release 构建，也不能在未取得授权时用于商业发行。发布前必须保留并复核 `LICENSE`、`NOTICE.md`、`THIRD_PARTY_NOTICES.md` 和 APK 内 `assets/NOTICE.txt`。
