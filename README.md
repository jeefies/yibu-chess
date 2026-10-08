# 弈步 0.3.2 安装包

手机浏览器登录 GitHub 账号 `well49112` 后，点击下面的链接下载。

[下载 ARM64 APK（约 84 MiB）](https://github.com/well49112/yibu-chess/raw/refs/heads/apk-downloads/yibu-0.3.2-arm64.apk) · [打开安装包页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.3.2-arm64.apk)

如果浏览器显示文件页面，点击下载按钮（Download raw file）。最低 Android 8.0，支持 Xiaomi 17 Pro 等 arm64 手机；模型权重包含在安装包内，运行无需联网。

0.3.2 修复执白或执黑时第二步点棋子无响应：棋盘手势读取最新回合、就绪和选中状态，AI 落子后解除锁定即可继续点击，不需要翻转棋盘或重开。

Stockfish 初评目标深度 18、每次搜索最多 1.5 秒；深度复评目标 22、最多 6 秒，均使用双线程和 128 MiB Hash。实际完成深度受局面及时间影响，实战补搜可能需要多次搜索。最强对手的全棋力思考时间从约 1.5 秒提高到约 3 秒/步。

普通分析在玩家回合后台运行，不锁住棋盘。继续走棋会中断分析并优先处理新回合，空闲后补齐缺失结果；最近一回合优先验证，普通评级仍只在复盘显示。对双方经验证的 `!!` 保留弃子原因和后续思路。

匹配对手继续使用 Maia-3 5M 人类棋谱模型，新局默认随机白黑，不弹询问；主动选择颜色后会记住。个人 Elo、最强不计分、棋谱删除和运行诊断导出功能保留。保留 0.3.1 的小米 17 / ONNX Runtime 1.24.3 兼容修复。

直接覆盖旧版安装，签名相同、versionCode 提高到 6，保留棋谱和个人 Elo。

49 项自动化测试通过且无跳过，包含实际 Maia 推理、同源 Stockfish JNI、白黑三回合棋盘触摸、最强对手、后台分析中断恢复及原有存储和评级回归。APK 签名、权重、压缩库提取配置和 16 KB ELF / ZIP 对齐检查通过。Cloud 未在你的 Xiaomi 17 Pro 实测。

[源码、构建说明和开源许可](https://github.com/well49112/yibu-chess/tree/main) · [版本说明](https://github.com/well49112/yibu-chess/releases/tag/v0.3.2) · [SHA-256 校验](SHA256SUMS.txt)

本分支保留新旧安装包及校验文件；签名密钥不在 Git 仓库中。
