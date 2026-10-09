# 在手机上记录分析耗时（0.8.2 起）

0.8.3 保留本功能。对弈后台默认为 `lightning`，日志同时记录显式 `target_depth=22`、`search_budget_ms=500`；手动“复评本步”和未缓存的整盘复盘仍为 `deep`。比较时按实际 profile 分类，不把这两种模式混在一起。

## 手机操作

1. 覆盖安装 0.8.2，打开原来分析较慢的棋谱，进入逐步复盘。
2. 选择不同的几步，分别点“复评本步”；也可以对尚未完成深度复评的棋局做整盘复盘。已经全部缓存的整盘复盘不会产生新的请求记录。
3. 测试时保持 App 在前台，记下使用的是 Wi-Fi、移动数据，以及是否开着 VPN。可切换网络对同一局面再测，注意第二次可能命中服务器缓存。
4. 点顶部信息按钮 → “导出分析耗时”，分享 `yibu-analysis-timings.json`。记录只在本次进程中保留，最多 120 次分析；导出前不要重启 App 或清除数据。

记录自动开启，无需连接电脑或使用 ADB。记录不含 Access Token、请求/响应正文、完整棋谱、SSID 或 IP 地址。文件包含设备、网络类型、棋局本地编号、半步编号和随机请求编号。“导出运行诊断”也包含同一份计时，但还带崩溃记录。

## 手机日志中的时间

所有 `*_ms` 都以毫秒为单位，使用单调时钟计算。`started_at_ms` 是用于对照服务器日志的墙上时钟，不用于计算耗时。

| 字段 | 范围 |
| --- | --- |
| `request_id` | 本次随机 UUID，与请求头 `X-Request-ID` 相同 |
| `profile` / `multi_pv` / `best_depth` / `played_depth` | 实际请求档位、候选数、完成的深度 |
| `analysis_ms` | 请求准备、HTTP、解析/变化校验、手机评级及协程恢复的整个分析阶段 |
| `request_preparation_ms` | 手机检查输入、构造并序列化 HTTP 请求 |
| `http_ms` | 手机发起 HTTP 到完整响应正文交给分析协程，包括服务器、连接、传输和协程恢复等待 |
| `http_call_ms` | OkHttp 从 callStart 到 callEnd/callFailed，可与 `http_ms` 对照协程恢复开销 |
| `dns_ms` | 本次 DNS 查询累计耗时；未发生查询时为 null |
| `connect_ms` | 建立连接累计耗时，包含代理隧道和 TLS |
| `tls_ms` | TLS 握手累计耗时，是 `connect_ms` 的一部分 |
| `response_headers_wait_ms` | 请求正文发完到响应头收完，包括服务器等待与网络，不等于纯搜索时间 |
| `download_ms` / `response_bytes` | 响应正文读取时间和长度 |
| `response_processing_ms` | JSON 解析、PV 合法性/分值校验及结果转换 |
| `save_ms` | 本步保存过程，包含锁等待、IO 调度、事务、主线程恢复 |
| `database_ms` | IO 工作线程进入保存到事务结束，包括当前棋谱的 JSON 编码/解码 |
| `total_ms` | 从本步分析开始到分析与保存结束；失败/取消记录则止于结束处理 |
| `total_to_frame_ms` | 从本步分析开始到界面采用该结果后下一次 Compose 帧回调；不是屏幕物理呈现测量 |
| `after_completion_to_frame_ms` | 保存/分析结束后至该帧回调的等待 |
| `connection_reused` / `connect_attempts` / `ip_family` | 连接复用、尝试次数、IPv4/IPv6；不记录具体地址 |
| `transports` | 开始时的系统活动网络类型，可能包括 wifi、cellular、ethernet、vpn；空数组表示未取得 |
| `server_search_ms` / `server_queue_ms` / `server_response_ms` / `server_cached` | 服务器明确提供的搜索、排队、总处理时间和缓存标记，未提供为 null |

`analysis_ms` 包含 `http_ms`、`response_processing_ms` 等；`save_ms` 包含 `database_ms`；`connect_ms` 包含 `tls_ms`。这些嵌套字段不能直接相加。整盘分析按步请求、按步保存，计时参数沿用 0.8.1，没有添加模拟思考等待。

`record_reloads` 单独记录每次全棋谱 JSON 解码的起始单调时间、耗时、数据库记录数和正文字符数。Room 查询时间不包括在这个解码时间中；它与分析/保存时间可能重叠，不能直接相加。它用于判断是否存在主线程被大量旧记录解析阻塞的情况。

`status` 为 success、cancelled、failed 或 discarded。失败只记录异常类型，不记录可能带口令的异常正文。没有收到的阶段或没有观察到的帧保持 null，不伪造为 0。只比较前台成功记录；切后台的帧等待可能包含后台停留时间。

## 朋友的服务端如何记录

收到 `/sf/v1/analyze-move` 时，读取客户端请求头 `X-Request-ID`，在服务端日志写入这个编号，并用服务端自己的单调时钟分别记录：

- 排队：进入计算队列到取得引擎工作槽。
- 搜索：本步全部 Stockfish 搜索的总耗时，包括最佳、指定实战着及补充搜索；单一子搜索的耗时不能冒充整个分析耗时。
- 总处理：进入服务到生成响应的耗时，包含排队、搜索和服务端结果处理。
- 缓存是否命中、实际 profile、MultiPV、完成深度以及引擎/线程配置。无需打印口令或所有请求头。

可以在原响应上增加可选字段，不影响旧 App：

```json
"stats": {
  "searchTimeMs": 600,
  "queueWaitMs": 0,
  "responseTimeMs": 630,
  "cached": false
}
```

以上数字仅是结构示例；必须填写本次测量值。0.8.2 会自动读取这些字段，服务器未修改时手机记录仍可用，只是服务端时间为 null。

也支持响应头 `Server-Timing: search;dur=600, queue;dur=0, total;dur=630`，单位毫秒。JSON 中有效的 stats 优先；这个头不提供缓存标记。不需要客户端与服务端时钟完全同步来比较各自计算的持续时间。

## 对照判断

- 手机 `http_ms` 约 3000，而同一请求的服务端总处理约 630：优先检查公网路径、代理、连接重试、正文传输与客户端调度。不能仅用“搜索600”排除服务端排队。
- 手机 `http_ms` 约 700，但 `save_ms` 或 `after_completion_to_frame_ms` 很大：检查保存、主线程阻塞与界面；结合 `record_reloads` 看全棋谱解码耗时。
- 服务端 `server_queue_ms` 大：检查工作槽、并发和其他请求；搜索约 600 不代表请求能在 600 内返回。
- 首步慢、后续连接复用后快：看 DNS、连接、TLS 数据。多个连接尝试可能包含失败路由或重试。

公平比较须使用同一局面和实战走法、`profile=deep`、`multiPv=2`、相同引擎配置，并区分缓存命中与未命中。单独 `/evaluate` 或快速档的 0.6 秒不能直接与整步深度分析比较。
