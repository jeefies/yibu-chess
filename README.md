# 弈步 0.3.0 安装包

手机浏览器登录 GitHub 账号 `well49112` 后，点击下面的链接下载。

[下载 ARM64 APK（约 94 MiB）](https://github.com/well49112/yibu-chess/raw/refs/heads/apk-downloads/yibu-0.3.0-arm64.apk) · [打开安装包页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.3.0-arm64.apk)

如果浏览器显示文件页面，点击下载按钮（Download raw file）。最低 Android 8.0，支持 Xiaomi 17 Pro 等 arm64 手机。Maia 和 Stockfish 的模型权重都在安装包里，运行无需联网。

0.3.0 使用 Maia-3 5M 人类棋谱模型作为匹配对手，以概率抽样、每盘随机种子与近期开局减权提供丰富走法。Stockfish 用于后台分析和最强对手。对弈只显示双方经过深度验证的 `!!`，附弃子原因和后续中文思路／参考变化；普通评级和推荐走法在复盘查看。

首次使用默认匹配对手、随机白黑。点击“新局”直接开始，每盘重新抽取颜色；主动打开“对局设置”可以选白方、黑方或最强对手，选择会记住。个人 Elo、最强不计分、棋谱删除功能继续保留。

可直接覆盖旧版安装，签名相同，保留棋谱和个人 Elo。首次启动请稍等离线模型准备完成；如果随机到黑方，AI 会自动先走。模型强度与个人 Elo 是近似对应，尚未按真人平台标定。

40 项自动化测试通过；实际加载 ONNX 权重、同源 Stockfish 宿主 JNI、界面和存储回归均通过。APK 签名及 16 KB ZIP／ELF 对齐检查通过。Android 真机测试 APK 已构建，仍需手机实测。

[源码、构建说明和开源许可](https://github.com/well49112/yibu-chess/tree/main) · [版本说明](https://github.com/well49112/yibu-chess/releases/tag/v0.3.0) · [SHA-256 校验](SHA256SUMS.txt)

本分支保存新旧安装包和校验文件；应用源码在 `main` 分支。签名密钥不在 Git 仓库中。
