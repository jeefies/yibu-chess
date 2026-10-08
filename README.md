# 弈步 0.6.0 安装包

[打开 APK 下载页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.6.0-arm64.apk)

手机浏览器先登录 GitHub 账号 `well49112`，进入页面后点击 Download raw file 下载。安装包约 84 MiB，支持 Xiaomi 17 Pro 等 arm64 手机，最低 Android 8.0。模型权重包含在 APK 内，运行无需联网。

本版改动：

- 深度复盘使用最多 8 线程、512 MiB Hash，复用搜索缓存及有效的已保存深度结果，避免重复配置引擎。保持目标深度 22、单次最多 6 秒；提速取决于局面和设备。
- “讲解这一步”在同一界面保留棋盘和说明。原因、后续思路和全文集中展示；长文字只在说明区滚动，棋盘及跟走操作始终可见。
- 后续每着都有对应棋盘、箭头、移动说明和解释。可以前后跟走、自动演示和暂停；返回复盘保留原实战棋步。旧版已保存讲解直接可用，不重新搜索。

同包名、同签名，versionCode 为 10，直接覆盖旧版安装，保留棋谱和个人 Elo。模拟等待仍为 1–2 秒，搜索时间计入等待。

仅检查本次改动：12 项相关 JVM / Compose 用例和新增的原生 JNI 缓存与暂停检查通过，签名 release APK 构建完成。未运行全量回归或全量 lint，上传后不重复下载校验。

[新界面预览](https://github.com/well49112/yibu-chess/blob/v0.6.0/docs/UI-DESIGN.md) · [本版本完整源码与构建说明](https://github.com/well49112/yibu-chess/tree/v0.6.0) · [版本说明](https://github.com/well49112/yibu-chess/releases/tag/v0.6.0)

本分支保留新旧安装包及 SHA-256 文件；签名密钥不在 Git 仓库中。
