# 弈步 0.5.0 安装包

手机浏览器登录 GitHub 账号 `well49112` 后，点击下面的链接下载。

[下载 ARM64 APK（约 84 MiB）](https://github.com/well49112/yibu-chess/raw/refs/heads/apk-downloads/yibu-0.5.0-arm64.apk) · [打开安装包页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.5.0-arm64.apk)

如果浏览器显示文件页面，点击下载按钮（Download raw file）。最低 Android 8.0，支持 Xiaomi 17 Pro 等 arm64 手机；模型权重包含在安装包内，运行无需联网。

本版加入：

- AI 落子前随机思考约 2–4 秒，计算时间计入等待；搜索更久时不额外等待。切页、切后台、新局会取消旧回合。
- 复盘每步有“讲解这一步”：只为点击的这一步生成原因、对手关键应对和后续思路，保存到棋谱，之后无需重复计算。“跟走这条思路”可逐着查看参考变化。
- 棋子采用 250ms 原生图层平移，吃子淡出，快速倒放从当前位置继续；支持易位、吃过路兵与升变，系统关闭动画时立即定位。

讲解全程离线，基于 Stockfish 深入搜索及可核对的局面事实，不调用联网聊天模型。首次讲解需要等待搜索，参考变化随对手实际选择而改变。

匹配对手为 Maia-3 5M，Stockfish 用于分析与最强对手。默认随机白黑、个人 Elo、最强不计分、仅在对弈中展示经验证的 !!、完整赛后复盘及棋谱删除功能继续可用。

直接覆盖旧版安装，签名相同、versionCode 为 8，保留棋谱和个人 Elo。

68 项自动化测试通过且无跳过，包含实际 Maia 推理、同源 Stockfish JNI、白黑连续触摸、等待中断、单步缓存保存、动画中间帧和 320dp / 412dp 原生界面渲染。Android lint 无错误；APK 签名、权重和 16 KB ELF / ZIP 对齐检查通过。本版尚未在 Xiaomi 17 Pro 真机验证。

[界面预览](https://github.com/well49112/yibu-chess/blob/main/docs/UI-DESIGN.md) · [源码及构建说明](https://github.com/well49112/yibu-chess/tree/main) · [版本说明](https://github.com/well49112/yibu-chess/releases/tag/v0.5.0) · [SHA-256 校验](SHA256SUMS.txt)

本分支保留新旧安装包及校验文件；签名密钥不在 Git 仓库中。
