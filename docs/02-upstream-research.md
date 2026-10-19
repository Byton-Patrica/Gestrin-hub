# 上游研究与接入证据

核查日期：2026-10-18。只以项目源码、官方仓库和官网作为结论依据。网页/分支会变化，以下是研究快照，不是锁定依赖；实测前必须固定完整提交、APK 哈希、签名证书与版本。未下载 APK，未进行 Android 设备测试。

## 结论矩阵

| 类别 | 候选与 Android 状态 | 维护信号 / 研究版本 | 许可证观察 | 接入决定 |
| --- | --- | --- | --- | --- |
| Windows | brunodev85/winlator；官方描述 Android + Wine + Box86/Box64 | Releases 可见 11.2 / v11.2.0，标签提交 fb66541；有持续发行信号，精确最近提交日期未取到 | 根仓库标识 LGPL-2.1，Wine、Box、Mesa、DXVK 等须分别审计 | 外部安装和容器初始化；指定游戏快捷方式契约待查，不推断可直接 VIEW EXE |
| XP3 | zeas2/Kirikiroid2；有 Android 项目与 Manifest | Releases 可见 1.3.9 / 384e22f；源码 Manifest 却写 1.3.4，不能把源码直接视作发布 APK；近期维护未证实 | LICENSE 汇集多个作者和依赖条款，未见可直接套用的单一 GPL 标签 | 高风险探针；核验 ABI、现代存储和启动入口，失败后评估公开 fork |
| PSP | hrydgard/ppsspp；明确 Android 支持 | Releases 与 README 可见 1.20.4 / fa50bb1；有持续开发和发布信号 | README 为 GPL 2.0 或以后；LICENSE.TXT 含第三方声明 | 首个纵向切片；VIEW/content 有源码证据，发布版仍需验证 |
| Vita | Vita3K/Vita3K 主仓库直接支持 Android | continuous Build 4194 / b4e35ed，页面记录 2026-10-18；自动构建 APK 可见 | README 标识 GPLv2；COPYING 与依赖仍需锁版审计 | 以主仓库为准，旧 Vita3K-Android 用于历史参考；安装后的 title 关联与启动参数待查 |
| Switch | Eden 官方官网与自建 Forgejo，明确 Android 支持 | 稳定 v0.2.1 / 58c1e20ee5（2026-06-01）；master 可见 de7e6e01d4（2026-10-18） | 根 LICENSE.txt 为 GPLv3 文本，需核各文件与依赖 | 优先验证；Manifest 有 VIEW/content，读取路径与具体版本行为仍待核实 |
| Switch 备选 | citron-neo/emulator，官网互链 | 官网列出 2026-04-27 版本，但明确 Android 维护延续不确定 | 仓库显示 GPL-3.0，依赖另核 | 不选默认运行器；不能由 Android 构建历史推断当前维护质量 |

“有发布信号”只说明可见活动，不能证明漏洞修复能力、构建可复现或所有 APK 安全。Kirikiroid2 未证实近期维护不等于已确认废弃。GitHub 公共 API 对当前出口限流，部分请求还出现传输失败；精确最新 commit、归档字段等未取得，不补写推测日期。

## 可复核来源

### Windows

- [Winlator 主仓库与 README](https://github.com/brunodev85/winlator)
- [发行版](https://github.com/brunodev85/winlator/releases)
- [根 LICENSE](https://github.com/brunodev85/winlator/blob/main/LICENSE)
- [子模块清单](https://github.com/brunodev85/winlator/blob/main/.gitmodules)

根目录 app 是子模块，不能假设旧版单体目录仍适用。当前没有完成子模块源码启动链追踪。下一步固定主仓库及 app 子模块 SHA，查 Manifest、导出 Activity、快捷方式创建与读取代码；核 APK 是否一致。容器 ID、Wine prefix 和 Linux guest 路径只能由运行器维护，Hub 不读取其私有目录。不把 Windows Wine 运行路径扩展为原生 Linux / SteamOS 支持。

### XP3

- [Kirikiroid2 主仓库](https://github.com/zeas2/Kirikiroid2)
- [发行版](https://github.com/zeas2/Kirikiroid2/releases)
- [LICENSE 原文](https://raw.githubusercontent.com/zeas2/Kirikiroid2/master/LICENSE)
- [Android Manifest](https://raw.githubusercontent.com/zeas2/Kirikiroid2/master/project/android/AndroidManifest.xml)

Manifest 有 MAIN/LAUNCHER，检查文件未见 VIEW 文件接入声明。这不能证明完全无法显式启动，但不足以设计外部文件参数。需继续追踪 Java/native 入口和安装 APK。旧 Manifest 的包名、32/64 位能力、实际 SDK 行为均不得直接作为发布适配表。先测试完整自制 KiriKiri 项目，再测经授权目标游戏；脚本/插件差异单独记录，不自动下载补丁。若需研究 YuriSizuku/Kirikiroid2Yuri，另做完整身份、许可证与发行证据核验；目前未选定该 fork。

### PSP

- [主仓库 / 许可证声明](https://github.com/hrydgard/ppsspp)
- [发行版](https://github.com/hrydgard/ppsspp/releases)
- [LICENSE.TXT](https://raw.githubusercontent.com/hrydgard/ppsspp/master/LICENSE.TXT)
- [Manifest](https://raw.githubusercontent.com/hrydgard/ppsspp/master/android/AndroidManifest.xml)
- [PpssppActivity.java](https://raw.githubusercontent.com/hrydgard/ppsspp/master/android/src/org/ppsspp/ppsspp/PpssppActivity.java)

已读源码：PpssppActivity exported；VIEW filter 接受 content/file 与多种扩展名；parseIntent 读取 intent.getData()。据此可做 `ACTION_VIEW + content URI + 临时读取授权` 探针。只采用 content URI 路线；验证 DocumentProvider URI、FileProvider URI、冷/热启动及权限撤销。源码存在其他 extras 不等于本项目应采用它们。未固定发布版前不宣称这是稳定 SDK。

### Vita

- [当前主仓库](https://github.com/Vita3K/Vita3K)
- [当前发布入口](https://github.com/Vita3K/Vita3K/releases)
- [旧 Android 仓库](https://github.com/Vita3K/Vita3K-Android)
- [当前 Android Manifest](https://raw.githubusercontent.com/Vita3K/Vita3K/master/android/app/src/main/AndroidManifest.xml)

发布页可见 android-latest.apk，Build 4194，页面 SHA-256 为 `dcd4fe47d1b5f3284e8e0bf5dddf18b11ee99dbcb7abb8b1924080f1ed428a2a`；这是页面提供值，尚未下载复算。continuous 为可变标签，不能仅保存该 URL。Manifest 有导出的 MainActivity 与 Emulator，不足以证明某个 title extra 或 URI 已获支持。需追踪接收代码、安装标题 ID 和初始化流程。旧仓库 README 提到临时 Android 开发与可能 force push，不宜作为默认长期固定上游。

### Switch

- [Eden 官网](https://eden-emu.dev/)
- [官网链接的源码](https://git.eden-emu.dev/eden-emu/eden/)
- [发行版与 Android 资产](https://git.eden-emu.dev/eden-emu/eden/releases)
- [许可证](https://git.eden-emu.dev/eden-emu/eden/raw/branch/master/LICENSE.txt)
- [Android Manifest](https://git.eden-emu.dev/eden-emu/eden/raw/branch/master/src/android/app/src/main/AndroidManifest.xml)
- [Citron Neo 官网](https://citron-neo.org/)
- [官网链接的 GitHub 仓库](https://github.com/citron-neo/emulator)

Eden Manifest 的 EmulationActivity 导出，声明 VIEW/content 与 application/octet-stream；也有自定义配置 action，但本轮未追踪其接收契约，不使用未核参数。master 中的入口不能直接推定 v0.2.1 一致。先验证稳定 APK，必要时独立记录某个固定开发构建。Nintendo Switch 并非必须使用 GitHub 托管的项目；官网 → 源码 → 发布资产的身份链比搜索到同名仓库更重要。

## 接入和再分发边界

首版提供外部运行器选择与官方安装入口，默认不镜像 APK、不打包核心、不下载固件/密钥/商业游戏。外部进程分离减少耦合，但不能据此宣称免除许可证义务；任何复制代码、二进制再分发、修改 fork 或内嵌均须按锁定版本逐项处理源代码、NOTICE、许可证及依赖要求。本轮只做工程审计事项识别，不作法律结论。

每个候选需补齐：完整提交与日期、APK 来源和 SHA-256、签名证书、ABI/minSdk/GPU 前置条件、导出组件、参数接收源码永久链接、存储模型、日志导出能力、升级回归结果。当前五类均没有达到实机验证级别。

## 实现阶段补充核查

2026-10-18 后续源码检查更新了部分“待核实”项，以上初步研究保留为过程快照：

- Winlator app 固定提交 `a030f552f452158a2db64fdb32b490fa19c0b48d`，包名 `com.winlator`。其 MainActivity 导出，但 XServerDisplayActivity 明确不导出。内部快捷方式参数不能从 Hub 直接调用，因此当前只打开主页。[Manifest](https://github.com/brunodev85/winlator-app/blob/a030f552f452158a2db64fdb32b490fa19c0b48d/app/src/main/AndroidManifest.xml)、[内部启动代码](https://github.com/brunodev85/winlator-app/blob/a030f552f452158a2db64fdb32b490fa19c0b48d/app/src/main/java/com/winlator/ShortcutsFragment.java)。另发现 11.2 发行标签与 app 子模块版本标识存在差异，不能仅据标签推断 APK 和源码完全一致。
- Vita3K 固定提交 `b4e35ed3b71b9781add974308d83002a80e9f869`，包名 `org.vita3k.emulator`，导出的 Emulator 接收 `title_id` 字符串和可选 `game_title`，转换为已安装标题启动参数。Hub 已按该证据实现可选标题关联和启动尝试，尚未跑真实 Vita 游戏。[接收代码](https://github.com/Vita3K/Vita3K/blob/b4e35ed3b71b9781add974308d83002a80e9f869/android/app/src/main/java/org/vita3k/emulator/Emulator.java)、[Manifest](https://github.com/Vita3K/Vita3K/blob/b4e35ed3b71b9781add974308d83002a80e9f869/android/app/src/main/AndroidManifest.xml)。
- Kirikiroid2 原版源码固定提交 `d1c2b1259423542c893e0b65eaeb46c848848f2b`，包名 `org.tvp.kirikiri2`；未找到可信的文件路径或 content URI 指定游戏契约。[入口](https://github.com/zeas2/Kirikiroid2/blob/d1c2b1259423542c893e0b65eaeb46c848848f2b/project/android/src/org/tvp/kirikiri2/Kirikiroid2.java)。
- PPSSPP 官网下载页实际链接标准 APK，例如 `files/1_20_4/ppsspp.apk`；这与同页 VR 包及历史包需要区分。下载解析只选实际发布的标准版本链接，不构造猜测地址。[官方页面](https://www.ppsspp.org/download/)。

这些是源码/发行入口证据，仍不替代发布 APK 的设备兼容性实测。
