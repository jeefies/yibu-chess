# 弈步 0.8.0 安装包

[直接下载 APK](https://github.com/jeefies/yibu-chess/raw/refs/heads/apk-downloads/yibu-0.8.0-arm64.apk) · [打开 APK 页面](https://github.com/jeefies/yibu-chess/blob/apk-downloads/yibu-0.8.0-arm64.apk)

约 22.5 MiB，支持 Xiaomi 17 Pro 等 ARM64 手机，最低 Android 8.0。Maia-3 离线拟人模型与所有 19 类音效包含在 APK 内，对弈可断网运行。

本版改动：

- 对弈时后台持续执行 22 层深度复盘缓存，对局结束即可瞬间打开深度复盘（0 延迟直接复用缓存）。
- 对局设置支持配置后台搜索预算：默认 lightning（约 0.5s · 22 层极速分析），亦可选择 deep（固定 4s 充分深度）。
- 切换为远端 Stockfish 19 API 服务，包体积从 84 MiB 精简至 22.5 MiB，彻底移除本地庞大 NNUE 权重与 C++ 桥接。
- 对局设置支持配置与一键测试云端 Access Token，本地安全持久化。
- 修复后台计算与用户走子时的中断与缓存丢失问题，AI 思考阶段平滑后台分析。

同包名、同签名，versionCode 为 13，可直接覆盖旧版安装，保留棋谱和个人 Elo。

仅验证本次相关功能：JVM / Compose 检查通过，签名 release APK 构建完成。未运行全量回归或全量 lint。

本分支保留新旧安装包及 SHA-256 文件；签名密钥不在 Git 仓库中。
