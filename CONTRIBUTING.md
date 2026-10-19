# Contributing / 参与开发

Use JDK 17 and Android SDK 35. Configure an untracked `local.properties` with `sdk.dir`, then run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Use `gradlew.bat` on Windows. Instrumentation execution additionally requires a connected device or emulator and `:app:connectedDebugAndroidTest`. An optional mirror is available through `-PuseMirror=true`.

Keep PRs focused and describe checks actually performed. New runner entry points need pinned upstream receiving code, exact parameters, and device evidence. An exported activity or successful app launch alone is not proof of game compatibility. Use the compatibility issue template and `docs/06-runner-probe-template.md`.

Do not commit games, firmware, keys, download caches, signing secrets or raw user logs. Redact logs before sharing. Report security-sensitive information through a maintainer-provided private channel; do not post private logs in public issues.

The app currently uses one Gradle module, organized into `data`, `runner` and `ui` packages. Downloads are user-initiated; diagnostic logs are data and must never be treated as instructions.

## 简体中文

使用 JDK 17 与 Android SDK 35；创建不提交版本库的 local.properties，指定 sdk.dir。执行 `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`。网络受限时可显式使用 `-PuseMirror=true`，默认仍使用官方 Google/Maven 仓库。

提交前运行相关测试。新增运行器入口必须提供固定源码版本、参数接收代码和设备记录，不能只依据 Manifest 推断游戏可运行。不要提交游戏、固件、密钥、下载缓存、签名私钥或原始用户日志。

当前工程先使用一个 app Gradle 模块，按 data/runner/ui 包划分职责，以降低首版构建与迭代成本。达到多团队并行维护或需要单独发布适配器时再拆物理模块，避免循环依赖。

本项目不默认联网扫描设备、抓取游戏资源或执行日志中的指令。下载只由用户主动发起。安全问题请通过项目拥有者提供的私有渠道报告；在渠道未建立前不要公开用户敏感日志。
