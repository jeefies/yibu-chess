# 弈步：中文离线国际象棋 Android App

为个人手机练习制作，优先针对 Xiaomi 17 Pro 等 arm64-v8a 手机。

## 功能

- 本地 Stockfish 17.1 对弈；引擎和两份 NNUE 权重均包含在 APK 中，首次启动无需联网。
- 两个对手：匹配我的 Elo、最强。可执白或执黑；最强使用全棋力、双线程、约 1.5 秒/步。
- 个人练习 Elo 从 500 起步，匹配局结束后按胜负或和棋结算；最强局不计分。新匹配局的对手分数等于开局时的个人分数。
- 棋谱支持删除，删除不撤销已经结算的 Elo；后台分析不能重新保存已删除的棋谱。
- 点击棋子，再点击合法落点；支持王车易位、吃过路兵及后/车/象/马升变。
- 每个半回合异步初评，显示评级、刚才的最佳走法、局面评价与一句中文说明。
- 对关键好棋或弃子候选自动追加深入验证，保守判定 ! 和 !!。
- 复盘：逐步导航、局势曲线、完整走棋列表、推荐/实战变化跟走、单步/整盘深度复评。
- Room 自动保存对局和分析。切到后台停止当前计算，回前台可继续机器人回合；整盘分析按步保存。
- 标准将杀/逼和、保守死局子力判断、五次重复/75 回合自动和棋；三次重复/50 回合允许申请和棋。
- 导出 PGN、包含完整分析的 JSON、开源许可证；应用无 INTERNET 权限。

匹配档在较低分数时，从强引擎的候选和额外合法走法中按损失区间抽样；分数越高，主动犯错的概率和幅度越低，较高分数使用 Stockfish 技能等级。个人练习分数没有与 Chess.com 或 FIDE 标定，实际难度仍可根据使用反馈调整。中文说明目前是有棋盘证据的规则说明和变化对比，不是完整的战术教学系统。

## 在手机上安装

[下载 ARM64 APK（约 83 MiB）](https://github.com/well49112/yibu-chess/raw/refs/heads/apk-downloads/yibu-0.2.0-arm64.apk) · [打开安装包页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.2.0-arm64.apk)

该仓库为私有仓库，请先在手机浏览器登录 `well49112`。安装包页面右上角的下载按钮会保存 APK；源码 ZIP 用于开发，不能直接安装。

1. 下载 `yibu-0.2.0-arm64.apk`，按系统提示允许下载来源安装。已安装 0.1.x 时直接覆盖升级即可，签名保持一致，旧棋谱通过数据库迁移保留。
2. 首次启动会校验约 75 MiB 权重并复制到应用私有目录，稍等离线引擎就绪。
3. 默认匹配 Elo 500、你执白；“新局”可以选匹配或最强，以及执棋颜色。旧版未完成对局可继续，但不补计分。
4. 断网/飞行模式仍可对弈与复盘。
5. “复盘”中选择一步，可跟走推荐或实战变化。黄色箭头显示分支第一着；“下一步”逐步进入分支，“末尾”退出分支。
6. 同一签名、同一包名且更高版本号的 APK 可以覆盖升级并保留棋谱。卸载会删除本地棋谱和个人分数，请先导出需要保留的 PGN/JSON。

最低 Android 8.0 (API 26)，目标 SDK 35，原生库包含 16 KB ELF 对齐。首版仅打包 arm64，不适用于 32 位设备。

## Cloud 构建

固定工具链：JDK 17.0.20.1、Gradle 8.9、AGP 8.7.3、Kotlin 2.0.21、Android SDK 35 / Build Tools 35.0.0、NDK 28.0.13004108、CMake 3.22.1。

在 Linux x86_64 Cloud 中：

```bash
chmod +x gradlew tools/*.sh
tools/setup-cloud.sh
tools/with-env.sh ./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease
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

采用 [Chess.com 官方公开定义与阈值](https://support.chess.com/en/articles/8572705-how-are-moves-classified-what-is-a-blunder-or-brilliant-etc)。官网完整的棋力相关预期得分模型未公开，因此不能逐着一比一复制。0.2.0 改用自主的棋力相关厘兵模型，避免 Stockfish 自我对弈 WDL 在几乎全是和棋的区间隐藏明显失误。

`E = 1 / (1 + exp(-cp / scale))`，`scale = clamp(330 - 0.07 × (Elo - 500), 170, 360)`，Elo 限定在 100–2800；强制将杀直接取 1 或 0，已发生的和棋取 0.5。损失为最佳 E 减实际 E。这是练习用近似值，不是个人真实胜率。每步保存公式版本、评分棋力和两项预期得分，旧版分析显示“旧版评级”，整盘复评会改用新模型。

| 损失 | 评级 |
|---|---|
| 引擎最佳或等值走法 | 最佳 |
| 非最佳，< 2 个百分点 | 优秀 |
| 2–5 | 不错 |
| 5–10 | ?! |
| 10–20 | ? |
| ≥20 | ?? |

! 要求接近最佳、与次佳明显拉开差距且更深迭代稳定，排除仅一着合法棋和立即将杀。!! 要求在最佳防守主变化中有真实短期子力损失、后续没有简单立即回吃抵消、局面仍至少可维持、没有弃子以外的轻易获胜候选，以及深度至少 14 的稳定验证。当前检测宁可漏报，安静妙手等暂不识别。

将杀使用独立分值类型；没有将 M3→M5 自动判为失误。强制终局结果覆盖统计评价。搜索结果明显矛盾时显示“待复评”，不把噪声当精彩。实时为初评；深度复评在搜索较浅、阈值附近或不稳定时仍保留初评标记。

图表固定白方视角。为在手机上显示大优势和将杀，纵坐标使用 tanh 压缩；卡片仍显示原始厘兵/将杀评价。断点表示该步尚未分析。

## 个人 Elo 与对局存储

胜=1、和=0.5、负=0，按玩家执棋颜色确定。预期结果 `1 / (1 + 10^((对手 Elo - 当前个人 Elo)/400))`，新分数为 `旧分数 + round(K × (结果 - 预期结果))`，范围 100–2800。前 10 盘 K=64，接着 20 盘 K=32，之后 K=24。与同分对手的首盘胜负分别 ±32，和棋分数不变。新局按最新分数提高或降低机器人强度，对局中对手分数固定。

对局结果、个人分数和一次性结算记录在同一 Room 事务中保存；重复保存、复盘和重开不会再次结算。删除仅移除棋谱与分析，分数和结算记录保留。数据库版本 1→2 保留旧棋谱，旧局没有 rated 标记，因此不会影响新的个人分数。

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

核心测试覆盖初始局面 perft、王车易位、吃过路兵和钉住的吃过路兵、四种升变、将杀、三次申请/五次自动重复和棋、死局子力、完整 MultiPV/WDL 解析、评价视角、公开评级阈值、相同根局面比较、矛盾搜索及 SAN/PGN；另覆盖 WDL 和棋区间误判回归、等值最佳、终局和棋、Elo 公式与边界、动态难度方向。

宿主 JNI 探针使用与 APK 相同的 JNI 包装和 Stockfish 源码，在 Linux JVM 上验证权重、MultiPV/WDL、限定走法、历史、王车易位、吃过路兵、双线程、停止和继续。宿主验证不替代安卓设备测试。

启动回归测试使用 Robolectric 模拟 Android 15，覆盖空棋谱时的界面启动。`tools/test-startup.sh` 进一步使用同源宿主 JNI 验证引擎初始化和第一回合对弈；并验证认输结算、再次打开、删除和最强局不计分。Room 测试覆盖并发一次性结算、胜负和棋、旧库迁移及删除后延迟保存。这仍不能代替 Android ARM64 真机验证。

### 0.1.1 修复

0.1.0 在首次打开及新建对局时，将空棋谱传入 chesslib 的走法解析器，抛出 `MoveConversionException` 导致主界面退出。0.1.1 将空棋谱及空推荐分支直接显示为空列表，并增加启动回归测试。引擎库加载错误现在会显示可读的启动失败信息。版本号和签名支持覆盖升级。

真机验收建议：飞行模式首次启动、两个颜色各下几步、切后台/重启后继续、四种升变、暂停再继续复盘、导出文件、同签名覆盖升级。Cloud 没有连接你的手机，本次不能声称已经通过 Xiaomi 17 Pro 实机验收。

## 源码许可

GPL-3.0-or-later，详见 LICENSE 和 THIRD_PARTY.md。vendor 中保留完整对应上游源码及许可证，网络权重和版本记录也随源码交付。
