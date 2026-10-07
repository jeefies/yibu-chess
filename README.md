# 弈步：中文离线国际象棋 Android App

为个人手机练习制作的首版，优先针对 Xiaomi 17 Pro 等 arm64-v8a 手机。

## 首版功能

- 本地 Stockfish 17.1 对弈；引擎和两份 NNUE 权重均包含在 APK 中，首次启动无需联网。
- 轻松练习、接近挑战、进阶挑战、最强四档，可执白或执黑。
- 点击棋子，再点击合法落点；支持王车易位、吃过路兵及后/车/象/马升变。
- 每个半回合异步初评，显示评级、刚才的最佳走法、局面评价与一句中文说明。
- 对关键好棋或弃子候选自动追加深入验证，保守判定 ! 和 !!。
- 复盘：逐步导航、局势曲线、完整走棋列表、推荐/实战变化跟走、单步/整盘深度复评。
- Room 自动保存对局和分析。切到后台停止当前计算，回前台可继续机器人回合；整盘分析按步保存。
- 标准将杀/逼和、保守死局子力判断、五次重复/75 回合自动和棋；三次重复/50 回合允许申请和棋。
- 导出 PGN、包含完整分析的 JSON、开源许可证；应用无 INTERNET 权限。

轻松与接近档以强引擎的多个候选和额外合法走法为基础，按损失区间抽样。它们没有与 Chess.com 等级分标定，实际难度需要使用者反馈后调整。中文说明目前是有棋盘证据的规则说明和变化对比，不是完整的战术教学系统。

## 在手机上安装

[下载 ARM64 APK（约 83 MiB）](https://github.com/well49112/yibu-chess/raw/refs/heads/apk-downloads/yibu-0.1.1-arm64.apk) · [打开安装包页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.1.1-arm64.apk)

该仓库为私有仓库，请先在手机浏览器登录 `well49112`。安装包页面右上角的下载按钮会保存 APK；源码 ZIP 用于开发，不能直接安装。

1. 下载 `yibu-0.1.1-arm64.apk`，按系统提示允许下载来源安装。已安装 0.1.0 时直接覆盖升级即可，签名保持一致。
2. 首次启动会校验约 75 MiB 权重并复制到应用私有目录，稍等离线引擎就绪。
3. 默认轻松练习、你执白；“新局”可以改难度和执棋颜色。
4. 断网/飞行模式仍可对弈与复盘。
5. “复盘”中选择一步，可跟走推荐或实战变化。黄色箭头显示分支第一着；“下一步”逐步进入分支，“末尾”退出分支。
6. 同一签名、同一包名且更高版本号的 APK 可以覆盖升级并保留棋谱。卸载会删除本地棋谱，请先导出需要保留的 PGN/JSON。

最低 Android 8.0 (API 26)，目标 SDK 35，原生库包含 16 KB ELF 对齐。首版仅打包 arm64，不适用于 32 位设备。

## Cloud 构建

固定工具链：JDK 17.0.20.1、Gradle 8.9、AGP 8.7.3、Kotlin 2.0.21、Android SDK 35 / Build Tools 35.0.0、NDK 28.0.13004108、CMake 3.22.1。

在 Linux x86_64 Cloud 中：

```bash
chmod +x gradlew tools/*.sh
tools/setup-cloud.sh
tools/with-env.sh ./gradlew :core:test :app:assembleRelease
```

默认安装在 `/workspace/android-toolchain`，可用 `ANDROID_TOOLCHAIN_DIR` 指定其他目录。初始化脚本重复运行会复用已校验文件。JDK/Gradle/命令行工具下载均锁定 SHA-256。

`tools/with-env.sh` 为每次命令配置 JDK、SDK 和 Gradle 缓存路径，并从已有 HTTP/HTTPS 代理设置 Java 代理。保留平台的 CA 信任；脚本不会关闭 TLS 校验或绕过网络策略。使用自有 JDK/SDK 时可传入 `JAVA_HOME` 和 `ANDROID_HOME`。

Cloud setup 的一次 `export` 不一定持续到任务阶段，因此构建推荐始终经过 `tools/with-env.sh`，或在环境设置中配置变量。初始化阶段需要下载 SDK、NDK；构建依赖需要访问 Google Maven、Maven Central、Gradle 插件仓库及其下载重定向。构建离线与应用运行离线是两回事。

产物：`app/build/outputs/apk/release/app-release.apk`。

私有 GitHub 仓库：`https://github.com/well49112/yibu-chess`。手机浏览器登录该账号后，在 Releases 中下载已构建的 APK；工作区绝对路径不能直接作为手机的下载附件。

附带的 `.github/workflows/android.yml` 支持手动构建 APK。先配置下文的签名 Secret，再到 Actions → Android APK → Run workflow 启动；每次构建会保存可下载产物。源码推送不会自动触发尚未配置签名的构建。

## 固定测试签名

本次 APK 使用专门生成的个人测试密钥，位于 `signing/personal.jks`，不进入 Git 或源码压缩包。请保存随交付提供的签名备份。

- alias：`yibu`
- 测试 store/key 密码：`yibu-personal-test`
- 后续 Cloud 中恢复备份到同一路径，再构建，才能直接覆盖安装本次版本。
- `tools/create-test-key.sh` 仅在文件不存在时生成。重新生成会得到不同签名；不要在更新旧安装时使用新密钥。
- 版本更新时提高 `app/build.gradle.kts` 中的 `versionCode` / `versionName`。
- GitHub Actions 需要 Secret `YIBU_KEYSTORE_BASE64`，内容为这份测试密钥的 Base64。工作流缺失 Secret 时明确停止，避免生成无法覆盖升级的新签名。
- 需要不同密码的密钥时，Gradle 支持 `YIBU_KEYSTORE`、`YIBU_STORE_PASSWORD`、`YIBU_KEY_PASSWORD`；alias 固定为 yibu。

该密钥只用于本项目个人测试。正式发布请另行确定签名与分发安排。

## 分析与评级

落子前同一个完整历史局面先 MultiPV 搜索。实际走法若不在候选中，用 `searchmoves` 限定该着进行搜索；尽量选择相同已完成深度的结果。分析固定全棋力，不受机器人难度影响。

`E = (W + 0.5 D) / 1000`，WDL 来自 Stockfish 的引擎自我对弈模型，不表示用户真实胜率。损失为最佳 E 减实际 E。

| 损失 | 评级 |
|---|---|
| < 2 个百分点 | 最佳 / 优秀 |
| 2–5 | 不错 |
| 5–10 | ?! |
| 10–20 | ? |
| ≥20 | ?? |

! 要求接近最佳、与次佳明显拉开差距且更深迭代稳定，排除仅一着合法棋和立即将杀。!! 要求在最佳防守主变化中有真实短期子力损失、后续没有简单立即回吃抵消、局面仍至少可维持，以及深度至少 14 的稳定验证。首版检测宁可漏报，安静妙手等暂不识别。

将杀使用独立分值类型；没有将 M3→M5 自动判为失误。强制终局结果覆盖统计 WDL。搜索结果明显矛盾时显示“待复评”，不把噪声当精彩。实时为初评；深度复评在搜索较浅、阈值附近或不稳定时仍保留初评标记。

图表固定白方视角。为在手机上显示大优势和将杀，纵坐标使用 tanh 压缩；卡片仍显示原始厘兵/将杀评价。断点表示该步尚未分析。

## 验证

```bash
tools/with-env.sh ./gradlew :core:test
tools/with-env.sh ./gradlew :app:testDebugUnitTest
tools/test-startup.sh
tools/with-env.sh python3 tools/test-native.py
tools/with-env.sh ./gradlew :app:assembleDebugAndroidTest
# 连接支持 arm64 的设备后：
tools/with-env.sh ./gradlew :app:connectedDebugAndroidTest
```

核心测试覆盖初始局面 perft、王车易位、吃过路兵和钉住的吃过路兵、四种升变、将杀、三次申请/五次自动重复和棋、死局子力、完整 MultiPV/WDL 解析、评价视角、阈值、相同根局面比较、矛盾搜索及 SAN/PGN。

宿主 JNI 探针使用与 APK 相同的 JNI 包装和 Stockfish 源码，在 Linux JVM 上验证权重、MultiPV/WDL、限定走法、历史、王车易位、吃过路兵、双线程、停止和继续。宿主验证不替代安卓设备测试。

启动回归测试使用 Robolectric 模拟 Android 15，覆盖空棋谱时的界面启动。`tools/test-startup.sh` 进一步使用同源宿主 JNI 验证引擎初始化和第一回合对弈；这仍不能代替 Android ARM64 真机验证。

### 0.1.1 修复

0.1.0 在首次打开及新建对局时，将空棋谱传入 chesslib 的走法解析器，抛出 `MoveConversionException` 导致主界面退出。0.1.1 将空棋谱及空推荐分支直接显示为空列表，并增加启动回归测试。引擎库加载错误现在会显示可读的启动失败信息。版本号和签名支持覆盖升级。

真机验收建议：飞行模式首次启动、两个颜色各下几步、切后台/重启后继续、四种升变、暂停再继续复盘、导出文件、同签名覆盖升级。Cloud 没有连接你的手机，本次不能声称已经通过 Xiaomi 17 Pro 实机验收。

## 源码许可

GPL-3.0-or-later，详见 LICENSE 和 THIRD_PARTY.md。vendor 中保留完整对应上游源码及许可证，网络权重和版本记录也随源码交付。
