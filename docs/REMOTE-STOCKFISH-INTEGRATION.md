# 0.8.0 远端 Stockfish 对接

本版采用 PR #1 提供的接口格式，与最初的 REMOTE-STOCKFISH-API.md 提案存在字段差异。手机不要求后端改成提案格式。

- 服务地址：`https://chess.jeefy.top`。
- 前缀：`/sf/v1`；鉴权头：`X-Access-Token: <朋友提供的口令>`。
- Token 由用户在对局设置输入，仅保存在应用本地，不包含在 APK 或对局导出中。
- 每次请求携带 `position.initialFen`（标准起始六字段 FEN）和完整 UCI `position.moves`。不依赖服务端棋局会话。
- Stockfish 19 在服务端运行；手机保留 Maia-3、棋谱数据库、Elo、评级和基于 PV 的中文讲解。

## 实际请求与响应

`GET /sf/v1/health` 返回 `status` 和可选的 `engine.name`、`engine.version`。ready / ok / healthy 表示就绪；失败或未就绪不能显示连接成功。

`POST /sf/v1/evaluate` 请求：

```json
{
  "position": {
    "initialFen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "moves": ["e2e4"]
  },
  "profile": "standard",
  "multiPv": 1
}
```

响应包含 `completedDepth`、`best`、可选 `candidates` 与 `engine`。最强对手从合法的 `best.pv[0]` 取得走法。

`POST /sf/v1/analyze-move` 使用相同的 `position`，增加 `playedMove`；`profile` 为 fast 或 deep，`multiPv` 为 2。position 是该实战着尚未走出的完整历史。响应示例中的分值只用于说明结构：

```json
{
  "best": {
    "move": "e7e5", "depth": 22,
    "score": {"type": "cp", "value": 34},
    "wdl": {"win": 110, "draw": 830, "loss": 60},
    "pv": ["e7e5", "g1f3", "b8c6"]
  },
  "played": {
    "move": "c7c5", "depth": 22,
    "score": {"type": "cp", "value": 28},
    "pv": ["c7c5", "g1f3", "d7d6"]
  },
  "second": null,
  "previousBest": null,
  "comparison": {"canCompare": true, "commonDepth": 22},
  "engine": {"name": "Stockfish", "version": "19"}
}
```

## 结果约束

- 分数固定为请求根局面的行棋方视角，黑方行棋时也不改变这一约定。type 为 cp（厘兵）或 mate（Stockfish 将杀着数）。
- best 和 played 都必须有真实分值及合法变化。played 缺失不能复制 best，played.pv 第一着必须匹配 playedMove。
- 仅当 canCompare 为 true、best.depth = played.depth = commonDepth，且都是精确分值时确认评级。若 score.bound 给出 lower / upper，保持待复评。
- second 必须与最佳同深度；previousBest 必须来自更早的完整深度。缺少这些证据时，保守处理 ! / !!。
- WDL 若存在必须每项为 0–1000 且总和为 1000；缺失不会伪造成“100% 和棋”。
- actual depth 低于目标时如实显示，原有个人 Elo 评分模型仍由手机执行。

## 网络与缓存

- 手机总请求超时 35 秒，读超时 30 秒。离开计算页面、新局、切后台或修改口令时取消旧请求；旧结果不会写入新棋局。
- 整盘复盘第一版按步调用 analyze-move，完成一步就保存一步；暂停后保留已完成结果。
- 本地深度分析按引擎版本及评分 Elo 复用；17.1 的结果仍可查看，不冒充 19 的新分析。
- 未配置口令或服务断开时，Maia 匹配对弈仍可使用；远端复盘给出明确错误。最强对手不会自动变成 Maia。
- 401 / 403 提示更新口令，429 提示服务器忙，503 / 504 提示稍后重试。请求取消向协程正常传播。

## 本次验证范围

使用本地 MockWebServer 验证真实 HTTP 请求、JSON、白黑视角、缺失字段、非法 PV、比较标记、错误响应、取消后再次请求，以及设置保存和离线 Maia 对局。沿用签名，构建 arm64 release。

Cloud 请求朋友服务地址被网络层拒绝，因此未完成带真实口令的服务联调，也未在 Xiaomi 真机上运行。安装后可在对局设置中用朋友提供的口令点击“测试连接”。
