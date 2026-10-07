# 弈步 0.3.1 安装包

手机浏览器登录 GitHub 账号 `well49112` 后，点击下面的链接下载。

[下载 ARM64 APK（约 84 MiB）](https://github.com/well49112/yibu-chess/raw/refs/heads/apk-downloads/yibu-0.3.1-arm64.apk) · [打开安装包页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.3.1-arm64.apk)

如果浏览器显示文件页面，点击下载按钮（Download raw file）。最低 Android 8.0，支持 Xiaomi 17 Pro 等 arm64 手机。Maia 和 Stockfish 的模型权重都在安装包里，运行无需联网。

0.3.1 针对小米 17 / SM8850 的已知原生推理闪退：将 ONNX Runtime 从 1.23.2 升级到 1.24.3，包含 SME / SME2 指令分派修复。旧 APK 的原生库 Build ID 与上游故障报告一致；修复版 Android Maven 包有相同芯片的上游验证记录。原生库改为压缩打包，由 Android 安装时解压，保留 16 KB ELF 对齐和所有离线权重。

新增“关于弈步 → 导出运行诊断”，无需 ADB 即可导出设备与运行库版本、本应用退出原因和可用的堆栈。Android 11 及以上支持退出历史；由你手动分享，不会联网上传。

Maia-3 5M 人类棋谱模型继续作为匹配对手，Stockfish 用于后台分析和最强对手。对弈只显示双方经过深度验证的 `!!`，附弃子原因与后续思路；完整评级和推荐走法在复盘查看。新局默认随机白黑，不弹询问；主动选择颜色后会记住。个人 Elo、最强不计分、棋谱删除功能保留。

直接覆盖旧版安装，签名相同、版本号更高，保留棋谱和个人 Elo。首次启动请稍等模型准备完成；随机到黑方时 AI 自动先走。

43 项自动化测试通过，包括实际模型推理、棋盘启动、界面与存储回归、原生退出诊断和 Android 8 兼容性。APK 签名、压缩库提取配置及 16 KB ELF / ZIP 检查通过。Android 真机测试 APK 已构建；Cloud 无法实测 SM8850 指令分派，仍需在你的 Xiaomi 17 Pro 安装确认。

[源码、构建说明和开源许可](https://github.com/well49112/yibu-chess/tree/main) · [版本说明](https://github.com/well49112/yibu-chess/releases/tag/v0.3.1) · [SHA-256 校验](SHA256SUMS.txt)

本分支保存新旧安装包和校验文件；应用源码在 `main` 分支。签名密钥不在 Git 仓库中。
