# 安装包

APK 不再提交到仓库，统一作为 GitHub Release 附件发布。

- 下载：[GitHub Releases · v2.0.29](https://github.com/cxcboss/glass-orb-android/releases/tag/v2.0.29)，文件 `glass-orb-android-2.0.29.apk`
- 包名：`com.cxcboss.glassorb`
- 版本：`2.0.29`（`versionCode 39`）
- 类型：Debug 签名侧载包（约 10 MB）
- 最低系统：Android 10 / API 29
- 图形要求：OpenGL ES 3.0

本目录的 `SHA256SUMS.txt` 记录 Release 附件的哈希。下载后核验并安装：

```sh
shasum -a 256 -c SHA256SUMS.txt
adb install -r glass-orb-android-2.0.29.apk
```

权限、系统限制和真机验收重点见根目录 `README.md` 与 `docs/KNOWN_ISSUES.md`。
