# 弈步 0.4.0 安装包

手机浏览器登录 GitHub 账号 `well49112` 后，点击下面的链接下载。

[下载 ARM64 APK（约 84 MiB）](https://github.com/well49112/yibu-chess/raw/refs/heads/apk-downloads/yibu-0.4.0-arm64.apk) · [打开安装包页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.4.0-arm64.apk)

如果浏览器显示文件页面，点击下载按钮（Download raw file）。最低 Android 8.0，支持 Xiaomi 17 Pro 等 arm64 手机；模型权重包含在安装包内，运行无需联网。

本版重新设计对弈、复盘和棋谱界面：暖白底色、墨绿重点色、统一矢量图标，棋盘周围直接显示双方身份、执棋颜色和回合。复盘集中呈现步进操作、曲线、评级及推荐变化；棋谱列表支持查看和删除。

已安装 Emil Kowalski 仓库的全部 14 项设计技能，按 emil-design-eng 改善布局和交互。常用走棋、复盘步进即时响应；主要按钮仅有轻微按压反馈，并遵循系统动画设置。

[查看界面预览](https://github.com/well49112/yibu-chess/blob/main/docs/UI-DESIGN.md) · [技能安装记录](https://github.com/well49112/yibu-chess/blob/main/.agents/skills/INSTALLATION.md)

匹配对手为 Maia-3 5M，Stockfish 用于分析与最强对手；默认随机白黑，主动选择后记住。个人 Elo、最强不计分、仅展示双方经验证的 !!、赛后完整复盘及棋谱删除功能均可使用。

直接覆盖旧版安装，签名相同、versionCode 为 7，保留棋谱和个人 Elo。

51 项自动化测试通过且无跳过，包含实际 Maia 推理、同源 Stockfish JNI、白黑连续棋盘触摸与 320dp / 412dp 原生界面渲染。Android lint 无错误；APK 签名、权重和 16 KB ELF / ZIP 对齐检查通过。新 UI 尚未在 Xiaomi 17 Pro 真机验证。

[源码、构建说明和开源许可](https://github.com/well49112/yibu-chess/tree/main) · [版本说明](https://github.com/well49112/yibu-chess/releases/tag/v0.4.0) · [SHA-256 校验](SHA256SUMS.txt)

本分支保留新旧安装包及校验文件；签名密钥不在 Git 仓库中。
