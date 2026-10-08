# 弈步 0.5.1 安装包

[打开 APK 下载页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.5.1-arm64.apk)

手机浏览器先登录 GitHub 账号 `well49112`，进入页面后点击 Download raw file 下载。安装包约 84 MiB，支持 Xiaomi 17 Pro 等 arm64 手机，最低 Android 8.0。模型权重包含在 APK 内，运行无需联网。

本版调整：

- 模拟思考改为随机 1–2 秒，计算时间计入等待，搜索更久时不再额外等待。最强对手保留原有约 3 秒的实际搜索。
- 深度复评和“讲解这一步”使用最多 6 线程、256 MiB Hash；线程数不超过设备可用处理器数。保持目标深度 22、单次最多 6 秒，提升计算资源以缩短深入搜索耗时。普通后台分析继续使用 2 线程、128 MiB。
- 今后仅检查新功能和本次直接影响的行为，上传后直接提供下载页面，不重复下载或校验远端。

同包名、同签名，versionCode 为 9，直接覆盖旧版安装，保留棋谱和个人 Elo。

本次 3 项相关 JVM 测试及新增的高资源原生 JNI 搜索检查通过，签名 release APK 构建完成。未运行全量回归或全量 lint，未在 Cloud 中进行 Xiaomi 17 Pro 真机测试。

[本版本源码及构建说明](https://github.com/well49112/yibu-chess/tree/v0.5.1) · [版本说明](https://github.com/well49112/yibu-chess/releases/tag/v0.5.1) · [SHA-256 文件](SHA256SUMS.txt)

本分支保留新旧安装包及校验文件；签名密钥不在 Git 仓库中。
