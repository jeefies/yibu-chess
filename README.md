# 弈步 0.7.0 安装包

[打开 APK 下载页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.7.0-arm64.apk)

手机浏览器先登录 GitHub 账号 `well49112`，进入页面后点击 **Download raw file** 下载。安装包约 84 MiB，支持 Xiaomi 17 Pro 等 arm64 手机，最低 Android 8.0。Stockfish 与 Maia 模型权重包含在 APK 内，运行无需联网。

本版改动：

- 将杀和认输时，落败方的王分成 8 块，弹开、旋转并坠落。将杀先等最后一步落位，再播放特效；系统关闭动画时直接显示结果。特效只在本局结束时触发，打开旧棋谱不会重播。
- 赛后“关键点复盘”及复盘页“全局复盘”挑选约 3–5 个节点（短局可能更少），按时间自动展示实战与推荐路线，每着附说明。可暂停、切换节点、重播或返回逐步复盘，进入后台自动暂停。
- 关键点优先精彩弃子、关键好棋、失误与错失将杀；安静阶段标为回顾，未经确认的评级不作为关键结论。有效的深度分析直接复用，说明由引擎变化与棋盘事实生成，无需额外搜索。

同包名、同签名，versionCode 为 11，直接覆盖旧版安装，保留棋谱和个人 Elo。单步讲解、深度复评、导出与其他原有功能继续可用，模拟思考仍为 1–2 秒。

仅验证本次相关功能：8 项 JVM／Compose 检查通过，签名 release APK 构建完成。未运行全量回归或全量 lint；上传后直接提供下载页面，不重复下载验证。

[新界面与动画说明](https://github.com/well49112/yibu-chess/blob/v0.7.0/docs/UI-DESIGN.md) · [本版本完整源码与构建说明](https://github.com/well49112/yibu-chess/tree/v0.7.0) · [版本说明](https://github.com/well49112/yibu-chess/releases/tag/v0.7.0)

本分支保留新旧安装包及 SHA-256 文件；签名密钥不在 Git 仓库中。
