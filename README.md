# 弈步 0.8.3 安装包

[打开 APK 下载页面](https://github.com/jeefies/yibu-chess/blob/apk-downloads/yibu-0.8.3-arm64.apk) · [直接下载 APK 直链](https://raw.githubusercontent.com/jeefies/yibu-chess/apk-downloads/yibu-0.8.3-arm64.apk)

约 22.3 MiB，支持 Xiaomi 17 Pro 等 ARM64 手机，最低 Android 8.0。Maia 模型与音效内置；Stockfish 分析和最强对手需要联网。

本版改动：

- 整合 PR #2 与上游 v0.8.3：对弈时持续在后台缓存复盘，人类和 Maia 落子不再取消上一着分析。
- “对局设置”新增后台搜索预算，默认“极速（lightning）”：明确请求 lightning、目标深度 22、500ms 搜索预算，保留两个候选供评级。可选择“深入（deep）”使用服务器较长预算。
- 修正后台缓存引擎版本检查、重复分析循环、AI 并发更新覆盖复盘及 Room 延迟打开单例问题。切到深入会刷新极速结果。
- 新增分段耗时导出（v0.8.2 特性），记录实际 profile 与极速预算；可在手机分享 JSON，不包含 Access Token 或完整棋谱。
- 保留详细失误讲解、最多 16 着手动关键点复盘、内置离线 Maia 匹配对弈和原有音效。

安装后直接开始新局，后台使用极速档逐步缓存。赛后复盘复用已完成的有效结果，缺少的步骤才请求深入档。未缓存的旧棋谱和手动“复评本步”仍采用 deep。

同包名、同个人签名，versionCode 为 16，可直接覆盖旧版安装，保留棋谱、个人 Elo、口令与设置。

仅验证本次相关功能：各项检查与构建通过，个人签名 release APK 构建完成。未运行全量回归或全量 lint。

本分支保留新旧安装包及 SHA-256 文件；签名私钥不得进入 Git 仓库。
