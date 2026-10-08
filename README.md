# 弈步 0.7.1 安装包

[打开 APK 下载页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.7.1-arm64.apk)

手机浏览器先登录 GitHub 账号 `well49112`，进入页面后点击 **Download raw file** 下载。约 84 MiB，支持 Xiaomi 17 Pro 等 ARM64 手机，最低 Android 8.0。模型与所有音效包含在 APK 内，运行无需联网。

本版改动：

- 全局关键点复盘改为手动点击“上个点、上一步、下一步、下个点”，停在当前画面等待。最后点击“完成”，之后可“重看”。
- 单步讲解同样手动跟走，保留起始、上一步、下一步、末尾与逐着选择，棋盘和说明保持同屏。
- 加入 19 类原创离线音效，覆盖落子、吃子、吃过路兵、易位、升变、将军、将杀、认输、王碎裂、胜负、和棋、新局、!!、复盘与界面反馈。王碎裂声与动画同步，胜负声随后播放。
- 音效始终开启，直接用手机媒体音量控制，按用户要求不增加静音开关。切页、新局或后台取消待播放声音；返回不补播，打开旧棋谱不重播胜负声。

同包名、同签名，versionCode 为 12，可直接覆盖旧版安装，保留棋谱和个人 Elo。AI 思考等待仍为 1–2 秒。

仅验证本次相关功能：13 项 JVM／Compose 检查通过，签名 release APK 构建完成并包含全部 19 类音效。未运行全量回归或全量 lint；上传后直接给下载页面，不重复下载验证。

[界面与声音说明](https://github.com/well49112/yibu-chess/blob/v0.7.1/docs/UI-DESIGN.md) · [完整源码与音频生成脚本](https://github.com/well49112/yibu-chess/tree/v0.7.1) · [版本说明](https://github.com/well49112/yibu-chess/releases/tag/v0.7.1)

本分支保留新旧安装包及 SHA-256 文件；签名密钥不在 Git 仓库中。
