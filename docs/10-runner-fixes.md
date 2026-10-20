# Preview.3：下载与启动修复 / Download and launch fixes

2026-10-20 · versionCode 3 · 0.1.0-preview.3

## 已确认的原因

1. Kirikiroid2 的白名单仅写了源码中的 `org.tvp.kirikiri2`，而[官方 1.3.9 APK](https://github.com/zeas2/Kirikiroid2/releases/tag/1.3.9)实际包名为 `org.tvp.kirikiri2_free_10309`。已增加这个精确包名，未使用通配符。
2. 对同一官方 APK 实际下载并用 Android SDK aapt 检查：versionCode 68、versionName 1.3.9、minSdk 11，未声明 targetSdk（因此默认等于 minSdk 11），原生库包含 arm64-v8a 与 armeabi-v7a。SHA-256：`7df08d36726e8640cf1f78b0154d19f8a24ccb090be97b9ec7b9de2e55acba5a`。包名放行后，旧 target SDK 仍使其不能在 Android 14+ 新安装。下载器对这个确切旧版本提前说明原因，避免反复下载。
3. 文件预检查强制要求正 Content-Length，上游合法的未知长度/分块响应也会被拒绝。已允许未知长度，但传输期间与完成后仍执行 512 MiB 上限、实际长度、哈希和 APK 校验。已知长度不符仍拒绝。
4. 原先将预检查得到的短期 CDN 签名 URL 交给系统下载器，延迟或重试时可能过期。现在保留经检查的稳定上游 URL；HEAD 被 403/405/501 拒绝时尝试受限 Range GET。
5. Windows、XP3、未关联 Vita 标题或目录项目曾退化为运行器主页启动。这不能完成游戏启动。现移除这条回退，先显示启动选项；手动打开运行器是单独的用户操作，不再记为游戏交接。
6. PPSSPP 的 Manifest 文件过滤器要求路径后缀，但 SAF 文档 URI 不一定含扩展名。对于标准 PPSSPP，核查导出 Activity 后向其发送 content URI，不再被路径过滤器误拒绝。其他应用仍需声明可接收文件，未编造私有路径参数。

## 本地运行器流程

- 运行器页“选择本机应用”：从当前用户可见的 Launcher 应用中选择，按平台保存；选中仅建立关联，不会立即启动或联网。绑定的包被移除后不静默改绑其他应用。
- “本地 APK”：系统选择器选择独立 APK，复制到应用私有区后核查大小、可读签名、min/target SDK、原生 ABI、已安装同包签名及版本。用户本地选择的分支包可使用不同包名；此例外不适用于上游网络下载。APK 安装仍由 Android 系统确认。安装分支后由用户绑定该应用。
- 游戏详情“启动方式”：文件交接、系统接收应用选择器和“仅打开本地运行器”明确分开。未找到接收应用或无已验证的接口时留在 Hub 说明原因。结果用对话框保留，返回 Hub 后可读。
- Hub 不包含 Windows/XP3/主机兼容核心。选择本地应用可绕过 Hub 的下载步骤，但不消除游戏所需兼容核心。没有兼容运行器时，Hub 仍不能独立执行 EXE、XP3 或主机镜像。
- 本机应用绑定保存在本机偏好设置，不包含在游戏库元数据备份中。

## 上游与平台证据

- [Android 14 最低 target 安装限制](https://developer.android.com/about/versions/14/behavior-changes-all#minimum-target-api-level)
- [Android 15 将最低 target 提高到 24](https://developer.android.com/about/versions/15/behavior-changes-all#minimum-target-api-level)
- [uses-sdk 的 targetSdkVersion 默认值](https://developer.android.com/guide/topics/manifest/uses-sdk-element#target)
- [PPSSPP Manifest](https://github.com/hrydgard/ppsspp/blob/master/android/AndroidManifest.xml)

## English

Preview.3 fixes the exact official Kirikiroid2 1.3.9 package ID and distinguishes this bug from the legacy package's target-API installation block. It supports installed app bindings and user-selected local APKs without a network download. Network APKs keep the upstream package allowlist; locally selected APKs may belong to forks, but still require signature, SDK, ABI, size and update checks plus system confirmation.

Game launch no longer falls back to runner home screens. Explicit launch options distinguish file handoff, Android's app chooser, and manually opening a runner. A chooser cancellation or manual home launch is not recorded as a game launch. Unknown transfer sizes are accepted with bounded completion checks, and stable publisher URLs replace temporary CDN URLs in the system download queue.

Physical-device compatibility on the reported Redmi K90 / Redmi Turbo 5 and Android 16 is not established by emulator regression tests. The Hub still needs a compatible external execution engine; these changes remove mandatory Hub downloads rather than introduce an embedded emulator.
