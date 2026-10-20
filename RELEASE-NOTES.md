# 0.1.0-preview.3

2026-10-20 · versionCode 3 · Android 8.0+

- Fix the official Kirikiroid2 1.3.9 package allowlist; explain its target-API block on modern Android before downloading again.
- Support installed app bindings, Android's file app chooser, and verified local APK imports without Hub network downloads.
- Replace automatic home-screen fallback with explicit launch options and persistent outcome dialogs.
- Accept bounded unknown-length downloads and queue stable publisher URLs instead of short-lived CDN links.
- Chinese/English copy for the new flows. See [fix details](docs/10-runner-fixes.md).

中文：修正官方 Kirikiroid2 包名并提前说明旧 target SDK 限制；支持本机应用绑定与本地 APK 安装；取消无效的自动跳转，改用明确的启动方式；修复未知下载长度及短期 CDN URL 问题。未宣称在 Android 16 真机上完成游戏兼容验证。

# 0.1.0-preview.2

2026-10-19 · versionCode 2 · Android 8.0+。

- 界面精简：只保留功能标题、操作和必要状态；移除宣传标语。
- 新 G / 连接节点矢量图标，包含自适应和主题图标版本。
- 设置中切换中文和 English，即时生效，重启后保留。
- 游戏名称、来源路径和原始日志不会自动翻译。
- 主应用 APK 不捆绑运行器、兼容环境、游戏、固件、密钥或模型；运行器仍由用户主动下载并经系统确认安装。

验证：27 项单元测试和 9 项 Android 11 模拟器设备测试通过，release 构建与 lint 通过；手机和平板尺寸人工检查通过。API 30 模拟器结果不代表其他设备和运行器游戏兼容性。

当前限制：Windows / XP3 提供运行器主页入口；五类游戏尚未完成真机兼容性矩阵；本地诊断为规则引擎，未启用 AI 模型。预览使用开发签名，尚不作为公开稳定版发布。

详见 docs/07-user-guide.md、docs/08-verification-and-release.md 和 docs/09-implementation-status.md。
