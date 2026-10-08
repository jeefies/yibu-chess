# 弈步远端 Stockfish 服务：朋友对接说明（接口提案 v1）

这是最初的接口提案。0.8.0 已采用朋友 PR 提供的字段和鉴权方式，实际客户端约定见 [0.8.0 远端接入说明](REMOTE-STOCKFISH-INTEGRATION.md)。

目标：将 Android App 的 Stockfish 分析放到台式机计算，缩短逐步复盘、单步讲解与整盘分析的等待。本文定义待实现的服务，不代表现有 App 已具备联网能力。

## 分工与功能

服务端负责 Stockfish 搜索、最佳／实战走法的同深度比较、候选变化、缓存与计算调度。手机负责 Maia 拟人对手、棋盘、棋谱、个人 Elo、棋步评级、中文讲解和界面音效。最强对手可以复用服务端的局面分析接口取得最佳走法。

当前 App 使用 Stockfish 17.1，深度复盘目标为 22，MultiPV 为 2。一次棋步分析可能发生多次搜索，每次搜索最多 6 秒。因此迁移时应把同一步所需的多次搜索合并为一次 HTTP 调用，并为整个调用设计算时间上限。台式机更快，但深度 22 的完成时间仍取决于局面和硬件。

第一版必须支持：

1. 查询服务是否就绪、引擎版本、配置上限和支持的分析档位。
2. 对指定局面分析，返回最佳走法、分值、WDL 和合法主变化；可用于最强对手。
3. 对一个实战棋步同时返回最佳、实战、第二候选、前一深度的最佳结果，以及是否可以在同一深度比较。
4. 请求取消、有限队列、超时、引擎异常恢复、可选的结果缓存。

整盘流式复盘作为第二阶段可选功能。第一版手机按步调用第 3 项，也能完成整盘复盘。

## 无状态的约定

每个请求自带完整输入，任意空闲工作进程都能处理。服务不需要保存棋局、用户分数或复盘游标，下一次请求不依赖上一次请求成功。

统一局面格式：

```json
{
  "initialFen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "moves": ["e2e4", "e7e5", "g1f3", "b8c6"]
}
```

- `initialFen` 必须是完整六字段 FEN。
- `moves` 是从该起始局面逐步执行的 UCI 走法，服务应验证每着合法性。
- 推荐传起始 FEN 和完整历史，而不是只传当前 FEN：当前 FEN 无法恢复重复局面的完整历史。半回合计数也要保留。
- 若客户端只有一个独立局面，可以用它作 `initialFen` 并传空 `moves`；这表示没有提供该局面之前的历史。
- 允许常驻 Stockfish 进程、内部计算队列、Hash 表和结果缓存。这些是计算优化，不构成客户端必须依赖的会话。
- `requestId` 只用于关联响应和排查问题，不用它恢复棋局或任务状态。

## HTTP 约定

固定 HTTPS 地址，例如 `https://chess.example.com`。家用电脑可以通过反向代理／内网穿透提供地址。若采用 Tailscale 私网，则手机和后端联调环境都需要能接入同一私网。

受保护接口使用：

```http
Authorization: Bearer <token>
Content-Type: application/json
```

单人自用可以使用一个可更换的访问 token，不需要账号系统。引擎线程、Hash、并发与时间上限由服务器控制，客户端参数不能突破上限。

## 1. GET /v1/health

就绪时返回 200；引擎不可用时返回 503。示例配置只是起点，应根据电脑性能调整：

```json
{
  "status": "ready",
  "apiVersion": "1",
  "engine": {
    "name": "Stockfish",
    "version": "17.1",
    "fingerprint": "sf17.1-build-and-nnue-config-id"
  },
  "limits": {
    "maxDepth": 26,
    "maxTimeMs": 10000,
    "maxMultiPv": 3,
    "maxPvPlies": 20,
    "maxHistoryPlies": 512,
    "maxQueueWaitMs": 2000,
    "workers": 1
  },
  "profiles": {
    "quick": {"depth": 16, "maxTimeMs": 1000, "multiPv": 2},
    "deep": {"depth": 22, "maxTimeMs": 4000, "multiPv": 2},
    "play": {"depth": null, "maxTimeMs": 1500, "multiPv": 1}
  }
}
```

`fingerprint` 必须反映引擎构建、NNUE 权重和影响评价的配置，改变后应使旧缓存失效。初版建议固定官方 Stockfish 17.1，减少与现有记录对接的变化；以后升级引擎时同步处理手机缓存。

## 2. POST /v1/evaluate

用途：分析局面、取得最强对手的走法。

```json
{
  "requestId": "eval-001",
  "position": {
    "initialFen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "moves": ["e2e4", "e7e5", "g1f3", "b8c6"]
  },
  "profile": "deep",
  "limits": {"depth": 22, "maxTimeMs": 4000},
  "multiPv": 2,
  "maxPvPlies": 12
}
```

`profile` 必填；其他搜索选项省略时使用该档默认值。覆盖值超出公布上限返回 422。`depth: null` 表示只按时间搜索，适合 `play` 档。

响应：

```json
{
  "requestId": "eval-001",
  "engine": {"name": "Stockfish", "version": "17.1", "fingerprint": "sf17.1-build-and-nnue-config-id"},
  "rootSide": "white",
  "scorePerspective": "root_side_to_move",
  "rootOutcome": null,
  "bestMove": "f1b5",
  "lines": [
    {
      "rank": 1,
      "depth": 22,
      "score": {"cp": 34, "mate": null, "bound": "exact"},
      "wdl": {"win": 110, "draw": 830, "loss": 60},
      "pv": ["f1b5", "a7a6", "b5a4", "g8f6"]
    },
    {
      "rank": 2,
      "depth": 22,
      "score": {"cp": 30, "mate": null, "bound": "exact"},
      "wdl": {"win": 100, "draw": 835, "loss": 65},
      "pv": ["d2d4", "e5d4", "f3d4"]
    }
  ],
  "effectiveLimits": {"depth": 22, "maxTimeMs": 4000, "multiPv": 2},
  "stats": {"searchTimeMs": 1260, "queueWaitMs": 0, "responseTimeMs": 1290, "cached": false},
  "termination": "depth_reached",
  "targetDepthReached": true
}
```

上面的分值和走法仅展示字段结构，实际响应应返回本次有效候选数对应的所有 `lines`。`effectiveLimits.multiPv = min(请求值, 合法走法数)`；每一条最终候选必须来自同一个完整深度。`bestMove` 应与返回的第一条 PV 首着一致。

若局面已经自动终局：返回 200，`rootOutcome` 写明类型、结果及获胜方，`bestMove: null`，`lines: []`，`termination: "terminal"`，`targetDepthReached: null`。例如：

```json
{"kind": "checkmate", "result": "0-1", "winner": "black"}
```

逼和、子力不足、五次重复与七十五回合规则也应返回实际终局。可申请的三次重复／五十回合和棋不应自动当成已结束；客户端决定是否申请。

## 3. POST /v1/analyze-move（最关键）

用途：一次完成一着棋的最佳／实战比较，供手机评级、讲解和关键点选择使用。

`position` 必须是实战这一步尚未走出的局面；`playedMove` 是在该局面准备评估的实际走法。

```json
{
  "requestId": "move-005",
  "position": {
    "initialFen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "moves": ["e2e4", "e7e5", "g1f3", "b8c6"]
  },
  "playedMove": "f1c4",
  "profile": "deep",
  "limits": {"depth": 22, "maxTimeMs": 4000},
  "multiPv": 2,
  "maxPvPlies": 12
}
```

完整响应示例，数值同样仅展示结构：

```json
{
  "requestId": "move-005",
  "engine": {"name": "Stockfish", "version": "17.1", "fingerprint": "sf17.1-build-and-nnue-config-id"},
  "rootSide": "white",
  "scorePerspective": "root_side_to_move",
  "playedMove": "f1c4",
  "best": {
    "rank": 1, "depth": 22,
    "score": {"cp": 34, "mate": null, "bound": "exact"},
    "wdl": {"win": 110, "draw": 830, "loss": 60},
    "pv": ["f1b5", "a7a6", "b5a4", "g8f6"]
  },
  "played": {
    "rank": null, "depth": 22,
    "score": {"cp": 28, "mate": null, "bound": "exact"},
    "wdl": {"win": 95, "draw": 840, "loss": 65},
    "pv": ["f1c4", "g8f6", "d2d3", "f8c5"]
  },
  "second": {
    "rank": 2, "depth": 22,
    "score": {"cp": 30, "mate": null, "bound": "exact"},
    "wdl": {"win": 100, "draw": 835, "loss": 65},
    "pv": ["d2d4", "e5d4", "f3d4"]
  },
  "previousBest": {
    "rank": 1, "depth": 21,
    "score": {"cp": 33, "mate": null, "bound": "exact"},
    "wdl": {"win": 108, "draw": 831, "loss": 61},
    "pv": ["f1b5", "a7a6"]
  },
  "playedOutcome": null,
  "comparison": {"comparable": true, "matchedDepth": 22, "reason": null},
  "effectiveLimits": {"depth": 22, "maxTimeMs": 4000, "multiPv": 2},
  "targetDepthReached": true,
  "termination": "depth_reached",
  "stats": {"searchTimeMs": 2300, "queueWaitMs": 0, "responseTimeMs": 2330, "cached": false}
}
```

`second` 是无限制根搜索的第二候选，合法着只有一着时为 null。`previousBest` 是低于比较深度的最近一个完整根搜索深度的最佳结果，没有时为 null。它们用于手机判断关键好棋和精彩弃子所需的差距、稳定性，不能随意省掉。

### 服务器搜索流程

1. 从起始 FEN 重放全部历史，验证 `playedMove` 合法。
2. 在这同一个根局面运行无限制 MultiPV 搜索，并保留各完整深度的结果。
3. 如果实战着已经在候选中，直接复用它，省去第二次搜索。
4. 否则仍在同一个根局面，用 `searchmoves <playedMove>` 和 MultiPV=1 单独评估，保留深度快照。
5. 从两次搜索中选最大的共同完整深度，返回该深度的 `best`、`played` 和 `second`，以及更早一层的 `previousBest`。

不得直接拿“落子前最佳分数”和“落子后对手视角的分数”相减。推荐使用根局面的指定着搜索，保证视角和深度定义一致。

`maxTimeMs` 是整个棋步分析的总搜索预算，包含最佳、指定着及补充搜索，不是每一轮各有这么多时间。排队时间另外计入 `queueWaitMs`，并受排队上限约束。可先把预算约 60% 分配给根搜索、其余给指定着；实战着命中候选时可全部用于根搜索。它是尽力而为的计算预算，另设强制停止／异常回收的短宽限时间。

若限时内只达到共同深度 18，应如实返回 `matchedDepth: 18`、`targetDepthReached: false`、`termination: "time_limit"`。若没有共同的精确完整深度，`comparable: false`、`matchedDepth: null`，`reason` 写明原因；能取得的结果保留实际深度，缺失结果为 null。手机将它显示为待复评，不能假称已完成深度 22。

实战走法后若直接将杀或自动和棋，`playedOutcome` 返回实际结果。手机按终局结果处理预期得分，避免统计 WDL 与已结束棋局冲突。

### 所有评价的字段约定

- `cp` 为整数厘兵，100 约为一个兵；`mate` 为整数将杀距离，单位为 Stockfish 的着数语义，不当作半回合数。两字段恰有一个非 null。
- 所有分值和 WDL 都以本请求根局面的行棋方为视角：正 cp／正 mate 对它有利，负值对它不利。`rootSide` 明确写 white 或 black。
- `wdl` 为胜／和／负的千分值，非 null 时总和为 1000。启用 `UCI_ShowWDL`。若无法取得 WDL 则返回 null，不能用默认“100% 和棋”代替；mate 结果可以不带 WDL。
- `bound` 为 `exact`、`lower` 或 `upper`。用于确认棋步评级的配对必须都是 exact，不能把上下界当精确分值。
- `depth` 写实际完成深度。MultiPV 必须集齐同一深度的全部有效候选才算完整；刚开始但未完成的更深一层不算。
- 每条 PV 都从请求根局面开始，是合法 UCI 序列；`played.pv[0]` 必须是 `playedMove`。遇到将杀／自动终局截断，没有后续证据时允许短 PV，不编造走法。
- `comparison.comparable` 说明可以同深度比较，不保证评级一定稳定。手机继续使用个人 Elo、预期得分模型和阈值附近的待确认规则。
- 服务不用返回“!!、!、?、??”、个人 Elo 或中文讲解。它们由手机根据这些结果计算。

## 4. 可选 POST /v1/review：整盘流式返回

请求包含完整 `position`，此处 `position.moves` 表示整盘实战，而不是某一步的根历史；同时给出需要分析的 `plies`：

```json
{
  "requestId": "review-001",
  "position": {
    "initialFen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "moves": ["e2e4", "e7e5", "g1f3", "b8c6", "f1c4"]
  },
  "plies": [1, 3, 5],
  "profile": "deep",
  "limits": {"depth": 22, "maxTimeMs": 4000},
  "multiPv": 2,
  "maxPvPlies": 12
}
```

`ply` 从 1 开始，代表一个半回合。每一步相当于：`position.moves.take(ply - 1)` 作根历史，`position.moves[ply - 1]` 作 `playedMove`。`plies` 省略则分析全部；传入时必须去重并验证范围。

推荐 `application/x-ndjson`，每行一个完整 JSON：

```text
{"type":"move","requestId":"review-001","ply":1,"analysis":{...单步响应...}}
{"type":"move","requestId":"review-001","ply":3,"analysis":{...单步响应...}}
{"type":"heartbeat","requestId":"review-001"}
{"type":"done","requestId":"review-001","completedPlies":[1,3,5]}
```

单步完成即返回，手机按步保存。建议每 10–15 秒发心跳，代理关闭响应缓冲。请求断开时取消尚未完成的工作；手机重连时重新传全部输入和未完成的 `plies`。已完成结果可由内部内容缓存复用，不要求持久化任务或通过任务 ID 恢复。开始输出之后的错误也通过 NDJSON `type: "error"` 返回，而不是试图修改已经发送的 HTTP 状态码。

单步讲解／对弈请求应优先于整盘后台复盘；整盘每步之间参与公平调度，避免一盘棋独占引擎数分钟。

## 服务端运行要求

推荐 Python + FastAPI + python-chess，通过 UCI 调用 Stockfish。Stockfish 主要吃 CPU，不需要 GPU。Windows 或 Linux 都可运行，Docker 可作为可选交付方式。

- 按 CPU 指令集选择可运行的官方构建，初版锁定版本和 NNUE；使用 Skill Level=20、关闭 UCI_LimitStrength，启用 UCI_ShowWDL。
- 预启动并复用引擎，避免每步重新加载进程、NNUE 或修改相同的线程／Hash 配置。
- 一整个单步比较任务独占一个引擎实例；它内部的根搜索和指定着搜索不能与别的请求交错。
- 每次搜索都明确设置起始 FEN 和完整历史，即使复用进程／Hash，也不依赖上次的 position。
- 先以 1 个工作进程、4 线程、512 MiB Hash 作为保守起点，测量后调整。并发进程 × 每进程线程数要按可用 CPU 预算规划，给朋友的日常使用留资源。
- 有限等待队列；等待超过例如 2 秒时返回 429。计算结束深度不足可正常返回部分有效结果；停止失败或引擎卡住时销毁该实例、重建并返回错误。
- HTTP 断开／取消应发送 stop，读完本次 bestmove／确认就绪后再复用实例；回收失败就重建，防止上一任务输出混进下一任务。
- 缓存键必须包括起始 FEN、全部走法、实战着、引擎 fingerprint 和搜索选项，不能只按最终 FEN 缓存。缓存响应要换成当前 requestId。只有满足本次结果与深度要求的缓存才能命中。
- 计算可设置内存缓存或磁盘缓存；删除缓存后服务仍能独立处理相同请求。无需棋局数据库。

## 错误与超时

统一错误体：

```json
{
  "requestId": "move-005",
  "error": {
    "code": "ILLEGAL_MOVE",
    "message": "playedMove 在该局面非法",
    "path": "playedMove",
    "retryable": false
  }
}
```

| HTTP | 用途 |
|---|---|
| 400 | 无效 JSON、FEN 或非法棋步，指出字段及历史中的位置 |
| 401 | token 缺失或无效 |
| 422 | 搜索选项、档位或范围不支持 |
| 429 | 排队超时／限流；附 Retry-After |
| 503 | 引擎未就绪或进程异常，恢复后可重试 |
| 504 | 引擎超过计算预算及停止宽限，且没有可用结果 |

限时到但已有有效结果应返回 200，并明确实际深度与限时结束，不算传输错误。手机请求超时需要覆盖搜索预算、允许的排队时间及网络／停止宽限；限次数退避重试，取消的操作不重试。校验错误不重试。

## 交付与联调验收

朋友交付：服务源码／启动方式、固定地址、token、引擎版本与 fingerprint、默认档位和并发上限、以上接口的实际请求响应示例。若使用私网，也提供手机与联调环境的访问方法。

用固定棋谱报告单步冷缓存／热缓存耗时、整盘总耗时、实际完成深度与排队时间；以实测结果决定时间预算，不承诺任何局面都能在固定秒数达到深度 22。

验收重点：

1. 白黑视角分值一致，mate 与 cp 不混用，返回的 PV 合法。
2. 走法前历史与 `playedMove` 没有重复执行／错一半回合；实战 PV 的第一着正确。
3. 最佳／实战深度不同、根 MultiPV 未完成或只取得分值界限时，不报告可精确比较。
4. 完整历史正确处理重复与自动和棋，同一最终 FEN 的不同历史不会错误共用缓存。
5. 取消请求后引擎可正常处理下一请求；多个请求不互相修改局面或串输出。
6. 超时、进程崩溃、断网与流式中断给出可处理的结果，缓存清空仍能正常计算。

后续手机端工作：增加联网权限和地址／token 配置；将远端结果适配进现有分析记录；按请求对应的棋局和半回合保存，离开页面时取消请求；保留已有棋谱、Elo、手动复盘和音效。远端不可用时可继续 Maia 对弈，并显示远端分析不可用；是否启用慢速本地 Stockfish 作为备用，由手机设置明确决定。
