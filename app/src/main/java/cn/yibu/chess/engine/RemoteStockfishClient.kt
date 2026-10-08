package cn.yibu.chess.engine

import cn.yibu.chess.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
internal data class PositionDto(val initialFen: String? = null, val moves: List<String> = emptyList())

@Serializable
internal data class EvaluateReqDto(val position: PositionDto, val profile: String = "standard", val multiPv: Int = 1)

@Serializable
internal data class AnalyzeMoveReqDto(val position: PositionDto, val playedMove: String, val profile: String = "deep", val multiPv: Int = 2)

@Serializable
internal data class ScoreDetailDto(val type: String, val value: Int)

@Serializable
internal data class WdlDetailDto(val win: Int, val draw: Int, val loss: Int)

@Serializable
internal data class EvalItemDto(
    val move: String? = null,
    val depth: Int,
    val seldepth: Int? = null,
    val score: ScoreDetailDto? = null,
    val wdl: WdlDetailDto? = null,
    val pv: List<String> = emptyList()
) {
    fun toEvaluation(): Evaluation = Evaluation(
        depth = depth,
        multiPv = 1,
        cp = if (score?.type == "cp") score.value else null,
        mate = if (score?.type == "mate") score.value else null,
        win = wdl?.win ?: 0,
        draw = wdl?.draw ?: 1000,
        loss = wdl?.loss ?: 0,
        pv = pv
    )
}

@Serializable
internal data class EngineInfoDto(val name: String? = null, val version: String? = null)

@Serializable
internal data class ComparisonResultDto(val canCompare: Boolean, val commonDepth: Int, val diffCp: Int? = null, val diffWdlLoss: Int? = null)

@Serializable
internal data class HealthRespDto(val status: String, val engine: EngineInfoDto? = null)

@Serializable
internal data class EvaluateRespDto(
    val completedDepth: Int,
    val best: EvalItemDto? = null,
    val candidates: List<EvalItemDto> = emptyList(),
    val engine: EngineInfoDto? = null
)

@Serializable
internal data class AnalyzeMoveRespDto(
    val best: EvalItemDto,
    val played: EvalItemDto? = null,
    val second: EvalItemDto? = null,
    val previousBest: EvalItemDto? = null,
    val comparison: ComparisonResultDto,
    val engine: EngineInfoDto? = null
)

@Serializable
internal data class ErrorDetailDto(val detail: String? = null)

class RemoteStockfishClient(
    private val tokenProvider: () -> String,
    private val baseUrl: String = "https://chess.jeefy.top"
) : StockfishService {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun checkHealth(customToken: String? = null): Result<String> = runCatching {
        val token = (customToken ?: tokenProvider()).trim()
        if (token.isEmpty()) throw IllegalStateException("未配置 Access Token")
        val request = Request.Builder()
            .url("$baseUrl/sf/v1/health")
            .header("X-Access-Token", token)
            .get()
            .build()
        val responseBody = executeRequest(request)
        val resp = json.decodeFromString<HealthRespDto>(responseBody)
        val name = resp.engine?.name ?: "Stockfish"
        val version = resp.engine?.version ?: ""
        "$name $version".trim()
    }

    override suspend fun evaluate(history: List<String>, profile: String, multiPv: Int): RemoteEvaluation {
        val token = tokenProvider().trim()
        if (token.isEmpty()) throw IllegalStateException("未配置云端 Access Token，请在设置中配置")
        val reqDto = EvaluateReqDto(
            position = PositionDto(moves = history),
            profile = profile,
            multiPv = multiPv
        )
        val body = json.encodeToString(reqDto).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/sf/v1/evaluate")
            .header("X-Access-Token", token)
            .post(body)
            .build()
        val responseBody = executeRequest(request)
        val resp = json.decodeFromString<EvaluateRespDto>(responseBody)
        val best = resp.best?.toEvaluation() ?: error("远端服务未返回最佳走法")
        val bestMove = resp.best.move ?: best.pv.firstOrNull() ?: error("远端服务未返回走法")
        val candidates = resp.candidates.map { it.toEvaluation() }
        val engineName = resp.engine?.name ?: "Stockfish 19"
        return RemoteEvaluation(resp.completedDepth, bestMove, best, candidates, engineName)
    }

    override suspend fun analyzeMove(history: List<String>, playedMove: String, deep: Boolean): RemoteMoveAnalysis {
        val token = tokenProvider().trim()
        if (token.isEmpty()) throw IllegalStateException("未配置云端 Access Token，请在设置中配置")
        val reqDto = AnalyzeMoveReqDto(
            position = PositionDto(moves = history),
            playedMove = playedMove,
            profile = if (deep) "deep" else "fast",
            multiPv = 2
        )
        val body = json.encodeToString(reqDto).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/sf/v1/analyze-move")
            .header("X-Access-Token", token)
            .post(body)
            .build()
        val responseBody = executeRequest(request)
        val resp = json.decodeFromString<AnalyzeMoveRespDto>(responseBody)
        val best = resp.best.toEvaluation()
        val played = resp.played?.toEvaluation() ?: best
        val second = resp.second?.toEvaluation()
        val previous = resp.previousBest?.toEvaluation()
        val comp = resp.comparison
        val engineName = resp.engine?.name ?: "Stockfish 19"
        return RemoteMoveAnalysis(
            best = best,
            played = played,
            second = second,
            previousBest = previous,
            canCompare = comp.canCompare,
            commonDepth = comp.commonDepth,
            diffCp = comp.diffCp,
            diffWdlLoss = comp.diffWdlLoss,
            engineName = engineName
        )
    }

    override fun stop() {
        client.dispatcher.cancelAll()
    }

    private suspend fun executeRequest(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    val content = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        val errorMsg = runCatching {
                            json.decodeFromString<ErrorDetailDto>(content).detail
                        }.getOrNull() ?: "HTTP ${res.code} ${res.message}"
                        if (cont.isActive) cont.resumeWithException(IOException(errorMsg))
                        return
                    }
                    if (cont.isActive) cont.resume(content)
                }
            }
        })
    }
}
