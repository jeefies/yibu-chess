# 弈步：中文离线国际象棋 Android App

为个人手机练习制作，优先针对 Xiaomi 17 Pro 等 arm64-v8a 手机。

## 功能

- 匹配对手使用 Maia-3 5M 人类棋谱模型；Stockfish 17.1 用于分析与最强对手。两个模型的权重均包含在 APK 中，首次启动无需联网。
- 两个对手：匹配我的 Elo、最强。“新局”直接开始，默认随机白黑；主动打开“对局设置”可选择执棋颜色，选择会记住。最强使用全棋力、双线程、约 3 秒/步。
- 个人练习 Elo 从 500 起步，匹配局结束后按胜负或和棋结算；最强局不计分。新匹配局的对手分数等于开局时的个人分数。
- 棋谱支持删除，删除不撤销已经结算的 Elo；后台分析不能重新保存已删除的棋谱。
- 点击棋子，再点击合法落点；支持王车易位、吃过路兵及后/车/象/马升变。
- 对弈界面隐藏普通评级、分值与最佳走法。仅对双方经深度验证的 !! 显示弃子原因和后续参考变化；提示保留到玩家下一次落子。
- Stockfish 在后台保存逐步分析，对弃子候选自动追加深入验证。完整评级、最佳走法和曲线在复盘查看。
- 复盘：逐步导航、局势曲线、完整走棋列表、推荐/实战变化跟走、单步/整盘深度复评。
- Room 自动保存对局和分析。切到后台停止当前计算，回前台可继续机器人回合；整盘分析按步保存。
- 标准将杀/逼和、保守死局子力判断、五次重复/75 回合自动和棋；三次重复/50 回合允许申请和棋。
- 导出 PGN、包含完整分析的 JSON、运行诊断与开源许可证；应用无 INTERNET 权限。

Maia-3 从人类棋谱预测走法，用合法走法掩码后按概率抽样。每盘独立随机种子、轻微温度变化（0.95–1.05）与累计概率 97% 的候选池提供变化；前 12 个半回合对最近 12 盘相同局面的 AI 重复走法适度减权（不会把玩家走法算作 AI 重复，也不会凭空加入低概率走法）。同一棋局恢复后保留种子。合理应对少或仅一着合法棋时仍可能重复，不能保证每局都不同。

个人练习分数从 500 开始，没有与 Chess.com、Lichess 或 FIDE 标定。暂以 `模型 Elo = clamp(个人 Elo + 500, 600, 2600)` 映射 Maia 的棋谱训练范围；双方开局分数固定，后续可依据实际对弈反馈调整。中文说明使用实际子力损失和合法主变化证据。

## 在手机上安装

[下载 ARM64 APK（约 84 MiB）](https://github.com/well49112/yibu-chess/raw/refs/heads/apk-downloads/yibu-0.4.0-arm64.apk) · [打开安装包页面](https://github.com/well49112/yibu-chess/blob/apk-downloads/yibu-0.4.0-arm64.apk)

该仓库为私有仓库，请先在手机浏览器登录 `well49112`。安装包页面右上角的下载按钮会保存 APK；源码 ZIP 用于开发，不能直接安装。

1. 下载 `yibu-0.4.0-arm64.apk`，按系统提示允许下载来源安装。已安装旧版时直接覆盖升级即可，签名保持一致，旧棋谱通过数据库迁移保留。
2. 首次启动会校验 Stockfish 与 Maia 权重并复制到应用私有目录，稍等离线引擎就绪。
3. 首次使用默认匹配 Elo 500、随机执棋，直接进入对局。“新局”每盘重新抽颜色；“对局设置”可主动选匹配或最强及颜色。旧版未完成匹配局会改用 Maia 继续，保留原有计分资格。
4. 断网/飞行模式仍可对弈与复盘。
5. “复盘”中选择一步，可跟走推荐或实战变化。黄色箭头显示分支第一着；“下一步”逐步进入分支，“末尾”退出分支。
6. 同一签名、同一包名且更高版本号的 APK 可以覆盖升级并保留棋谱。卸载会删除本地棋谱和个人分数，请先导出需要保留的 PGN/JSON。

最低 Android 8.0 (API 26)，目标 SDK 35，原生库包含 16 KB ELF 对齐，以压缩形式随 APK 打包并由 Android 安装时解压。首版仅打包 arm64，不适用于 32 位设备。

## 0.4.0 界面改版与设计技能

采用暖白、墨绿与统一线条图标；棋盘周围直接显示双方身份、执棋颜色和回合状态。对弈、复盘、棋谱使用统一间距、卡片和按钮。后台分析状态只在底部以简短文字呈现；完整分析集中在复盘。复盘的逐步操作与曲线放在棋盘下方，推荐与实战变化各有独立入口。棋谱改为按需加载的列表，保留删除确认和 Elo 结算信息。

已安装 [Emil Kowalski 的设计技能](https://github.com/emilkowalski/skills) 到 `.agents/skills/`，固定上游提交 `e8a175de22ae1e49370fc144c1f3bb9aeedf988d`，包含全部 14 项技能及参考文件，保留 MIT 许可。本次应用 `emil-design-eng`：高频走棋和步进即时响应，偶尔点击的主要按钮仅有轻微按压反馈，遵循 Android 系统动画时长设置。设计决策见 [docs/UI-DESIGN.md](docs/UI-DESIGN.md)。

自动渲染检查覆盖白黑棋盘、复盘详情、有棋谱和空棋谱状态、320dp 小屏设置以及长分析文本；同时运行真实 Maia 与同源 Stockfish JNI 的连续棋盘触摸回归。构建环境未连接真机，新版仍需在手机上完成外观与触感确认。

## 0.3.2 连续落子与更深入分析

修复棋盘手势捕获过期回合状态的问题：AI 的棋步显示后，即使棋盘 FEN 没再改变，点击也读取最新的回合、就绪及选中状态。覆盖白黑视角的第二、第三回合与准备完成后的点击；另用实际 Maia、同源 Stockfish JNI 和棋盘触摸验证白黑连续对弈。

匹配对手优先回复；普通分析在轮到玩家后后台运行，不再锁住棋盘。继续走棋会中断后台分析，让新回合优先，之后补齐尚未分析的棋步。切页、切后台、新局与删除会取消旧工作，避免旧结果写入新局。只有本回合经过验证的 !! 进入对弈提示，历史结果仍在复盘查看。

初评为双线程、128 MiB Hash、目标深度 18 / 每次搜索最多 1.5 秒；深度复评目标 22 / 最多 6 秒。指定实战走法补搜仍尽量使用相同已完成深度。时间先到时保留实际完成的深度，不承诺每个局面都达到目标；一着棋可能需要多次搜索。最强对手仍以全棋力搜索，思考时间从约 1.5 秒提高到约 3 秒/步。

## 0.3.1 小米 17 启动闪退修复

将 Android 和宿主测试的 ONNX Runtime 从 1.23.2 升级到 1.24.3。旧运行时在 SM8850 / 小米 17 等只有 SME、没有 SME2 的芯片上会错误调用 SME2 指令，原生层 SIGILL 无法用 Kotlin 异常捕获。1.24.3 包含 [上游修复](https://github.com/microsoft/onnxruntime/pull/27403)，且有[相同芯片的 Android Maven 包验证记录](https://github.com/k2-fsa/sherpa-onnx/issues/3490#issuecomment-4211370944)。Maia 权重、走法选择与棋谱数据库保持兼容。

“关于弈步 → 导出运行诊断”可导出设备、应用和运行库版本，以及 Android 11 及以上保存的最近 5 次本应用退出原因和可用的堆栈（每条最多 64 KiB，原生 protobuf 堆栈以 Base64 保留）。无需 ADB 或新权限，由用户手动分享；Android 8–10 仍可导出设备信息。Cloud 的宿主测试无法验证 SM8850 指令分派，实际 Xiaomi 17 Pro 仍需安装后确认。

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

私有 GitHub 仓库：`https://github.com/well49112/yibu-chess`。手机浏览器登录该账号后，通过本页 APK 链接或 Releases 中提供的 APK 分支链接下载；工作区绝对路径不能直接作为手机的下载附件。

附带的 `.github/workflows/android.yml` 支持手动构建 APK。先配置下文的签名 Secret，再到 Actions → Android APK → Run workflow 启动；每次构建会保存可下载产物。源码推送不会自动触发尚未配置签名的构建。

## Maia 模型与复现

固定 [Maia-3 上游](https://github.com/CSSLab/maia3) 提交 `1e13597c42d4858b7cfd7cfdae01e297263364b2` 和 [5M 检查点](https://huggingface.co/UofTCSSLab/Maia3-5M) 修订 `b6559de2398d7140b985f28fd2c19fb5e47ddabe`。上游源码与原始权重保存在 `vendor/maia3`，ONNX 权重及校验记录在 `app/src/main/assets/models`。Android 使用 ONNX Runtime 1.24.3 CPU，无 Python、网络或服务依赖。

转换只输出走法策略，opset 17；将 RMSNorm 展开成等价运算，按通道动态 int8 量化 MatMul。8 帧历史各自按该帧的执棋方编码，保留官方 4352 项走法词表，包括黑方镜像和四种升变；掩码使用完整规则局面处理易位、吃过路兵与将军限制。模型为约 6.3 MiB。

权重元数据中的 `onnxruntime_version=1.23.2` 记录原始导出验证环境；移动端运行版本另由 Gradle、运行诊断和构建清单记录。

在独立 Python 环境安装 `torch==2.8.0`（CPU）、`onnx==1.19.1`、`onnxruntime==1.23.2`、`python-chess==1.999`、NumPy 后运行 `python tools/export-maia.py` 可重新导出。脚本对 9 个局面 × 4 个 Elo 比较原始 PyTorch 与 ONNX：浮点误差小于 0.0002，量化后的合法走法概率总变差不超过 0.045；本次最大 0.02734，首选走法一致率 97.2%。这些指标验证转换，不代表难度已经与真人平台标定。

核心测试对照官方 Python 生成的棋盘张量与合法走法索引；Android JVM 测试实际加载同一 ONNX 权重，校验双边视角、历史、易位、吃过路兵、白黑升变与多样性。Compose 测试检查普通评级隐藏、双方 !! 的说明、点击新局无询问及手动设置。另附 Android 真机推理测试；Cloud 只编译该测试 APK，未运行真机测试。

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

! 要求接近最佳、与次佳明显拉开差距且更深迭代稳定，排除仅一着合法棋和立即将杀。!! 要求在最佳防守主变化中有真实短期子力损失、后续没有简单立即回吃抵消、局面仍至少可维持、没有弃子以外的轻易获胜候选，以及深度至少 14 的稳定验证。当前检测宁可漏报，安静妙手等暂不识别。对弈时仅显示非初评的 !!；说明按主变化里实际被吃的棋子生成，后续思路引用合法 SAN 变化，双方都可触发。

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

核心测试覆盖初始局面 perft、王车易位、吃过路兵和钉住的吃过路兵、四种升变、将杀、三次申请/五次自动重复和棋、死局子力、完整 MultiPV/WDL 解析、评价视角、公开评级阈值、相同根局面比较、矛盾搜索及 SAN/PGN；另覆盖 WDL 和棋区间误判回归、等值最佳、终局和棋、Elo 公式与边界、模型强度映射、默认随机颜色与明确颜色优先、固定种子恢复、近期开局减权和真实弃子说明。

宿主 JNI 探针使用与 APK 相同的 JNI 包装和 Stockfish 源码，在 Linux JVM 上验证权重、MultiPV/WDL、限定走法、历史、王车易位、吃过路兵、双线程、停止和继续。宿主验证不替代安卓设备测试。

启动回归测试使用 Robolectric 模拟 Android 15，覆盖随机执棋时的界面启动。`tools/test-startup.sh` 进一步使用同源宿主 JNI 验证引擎初始化和第一回合对弈；并验证认输结算、再次打开、删除和最强局不计分。Room 测试覆盖并发一次性结算、胜负和棋、旧库迁移及删除后延迟保存。这仍不能代替 Android ARM64 真机验证。

### 0.1.1 修复

0.1.0 在首次打开及新建对局时，将空棋谱传入 chesslib 的走法解析器，抛出 `MoveConversionException` 导致主界面退出。0.1.1 将空棋谱及空推荐分支直接显示为空列表，并增加启动回归测试。引擎库加载错误现在会显示可读的启动失败信息。版本号和签名支持覆盖升级。

真机验收建议：飞行模式首次启动、两个颜色各下几步、切后台/重启后继续、四种升变、暂停再继续复盘、导出文件、同签名覆盖升级。Cloud 没有连接你的手机，本次不能声称已经通过 Xiaomi 17 Pro 实机验收。

## 源码许可

AGPL-3.0，详见 LICENSE 和 THIRD_PARTY.md。Stockfish 等第三方组件保留各自许可。vendor 中保留完整对应上游源码及许可证，网络权重和版本记录也随源码交付。
