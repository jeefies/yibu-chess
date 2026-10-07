package cn.yibu.chess

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.yibu.chess.core.*
import cn.yibu.chess.data.GameRepository
import cn.yibu.chess.engine.NativeStockfish
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class AppState(
    val game: GameRecord = GameRecord(),
    val games: List<GameRecord> = emptyList(),
    val page: Int = 0,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val status: String = "正在校验离线引擎…",
    val error: String? = null,
    val cursor: Int = 0,
    val variation: List<String> = emptyList(),
    val variationBase: Int = 0,
    val variationStep: Int = 0,
    val reviewDone: Int = 0,
) {
    val boardHistory: List<String> get() = when {
        page != 1 -> game.moves
        variation.isNotEmpty() -> game.moves.take(variationBase) + variation.take(variationStep)
        else -> game.moves.take(cursor)
    }
    val chosenReview: MoveReview? get() = game.reviews.find { it.ply == if (page == 1) cursor else game.moves.size }
    val humanTurn: Boolean get() = (game.moves.size % 2 == 0) == game.humanWhite
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)
    private val engine = NativeStockfish(application)
    private val analyzer = MoveAnalyzer(engine)
    private val opponent = Opponent(engine)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var generation = 0
    private val persistence = Mutex()

    init {
        viewModelScope.launch { repository.games.collect { games -> mutable.update { it.copy(games = games) } } }
        viewModelScope.launch {
            try {
                val restored = repository.latest()
                if (restored != null && !restored.finished) mutable.update { it.copy(game = restored, cursor = restored.moves.size) }
                engine.initialize()
                mutable.update { it.copy(ready = true, status = "离线引擎已就绪") }
                if (!mutable.value.humanTurn) advance()
            } catch (e: Exception) { mutable.update { it.copy(error = "引擎启动失败：${e.message}", status = "启动失败") } }
        }
    }
    private fun cancelWork() {
        val snapshot = mutable.value.game
        generation++
        work?.cancel()
        engine.stop()
        mutable.update { it.copy(busy = false) }
        if (snapshot.moves.isNotEmpty()) viewModelScope.launch { persist(snapshot) }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun newGame(difficulty: Difficulty, humanWhite: Boolean) {
        if (!mutable.value.ready) return
        cancelWork()
        val game = GameRecord(difficulty = difficulty, humanWhite = humanWhite)
        mutable.update { it.copy(game = game, page = 0, cursor = 0, variation = emptyList(), status = "新对局已开始", error = null) }
        viewModelScope.launch { persist(game); if (!humanWhite) advance() }
    }
    fun play(uci: String) {
        val state = mutable.value
        if (!state.ready || state.busy || state.page != 0 || state.game.finished || !state.humanTurn) return
        try {
            if (uci !in ChessRules.legal(state.game.moves)) return
            val game = finishIfNecessary(state.game.copy(moves = state.game.moves + uci))
            mutable.update { it.copy(game = game, cursor = game.moves.size) }
            advance()
        } catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
    }
    private fun finishIfNecessary(game: GameRecord): GameRecord = ChessRules.outcome(game.moves)?.let { (result, ending) ->
        game.copy(result = result, ending = ending, finished = true)
    } ?: game
    private suspend fun persist(game: GameRecord) = persistence.withLock {
        // Concurrent cancellation/navigation must not overwrite a newer snapshot.
        val current = mutable.value.game
        val latest = if (current.id == game.id) current else game
        withContext(Dispatchers.IO) { repository.save(latest) }
    }
    private fun advance() {
        if (!mutable.value.ready || mutable.value.busy) return
        val token = generation
        mutable.update { it.copy(busy = true, error = null) }
        work = viewModelScope.launch {
            try {
                persist(mutable.value.game)
                val last = mutable.value.game.moves.size
                if (last > 0 && mutable.value.game.reviews.none { it.ply == last }) analyzePly(last, false, token)
                currentCoroutineContext().ensureActive()
                if (token == generation && !mutable.value.game.finished && !mutable.value.humanTurn && mutable.value.page == 0) {
                    mutable.update { it.copy(status = "Stockfish 正在思考…") }
                    val before = mutable.value.game
                    val move = opponent.move(before.moves, before.difficulty)
                    currentCoroutineContext().ensureActive()
                    if (token != generation) return@launch
                    val game = withContext(Dispatchers.Default) { finishIfNecessary(before.copy(moves = before.moves + move)) }
                    mutable.update { it.copy(game = game, cursor = game.moves.size, status = "正在评估机器人走法…") }
                    persist(game)
                    analyzePly(game.moves.size, false, token)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) mutable.update { it.copy(error = "本次计算失败：${e.message}，可点击继续重试。") } }
            finally {
                if (token == generation) mutable.update { it.copy(busy = false, status = if (it.game.finished) "${it.game.ending} · ${resultChinese(it.game)}" else "轮到${if (it.humanTurn) "你" else "Stockfish"}走棋") }
            }
        }
    }
    private suspend fun analyzePly(ply: Int, deep: Boolean, token: Int) {
        val game = mutable.value.game
        mutable.update { it.copy(status = if (deep) "深度复评 $ply / ${game.moves.size}" else "正在分析第 ${(ply + 1) / 2} 回合…") }
        val review = withContext(Dispatchers.Default) { analyzer.analyze(game.moves.take(ply - 1), game.moves[ply - 1], deep) }
        currentCoroutineContext().ensureActive()
        if (token != generation || game.id != mutable.value.game.id) return
        val updated = mutable.value.game.copy(reviews = (mutable.value.game.reviews.filterNot { it.ply == ply } + review).sortedBy { it.ply })
        mutable.update { it.copy(game = updated) }
        persist(updated)
        // A promising tactical move gets an extra verification pass immediately,
        // so !/!! can appear during play while remaining conservative.
        if (!deep && review.pointsLost < 0.02 && ChessRules.legal(game.moves.take(ply - 1)).size > 1) {
            val unique = review.bestMove == review.uci && review.second?.let { review.best.expected - it.expected >= 0.10 } == true && review.best.mate != 1
            val sacrifice = review.played.expected >= 0.5 && ChessRules.substantialSacrifice(game.moves.take(ply - 1), review.played.pv)
            if (unique || sacrifice) analyzePly(ply, true, token)
        }
    }
    fun page(page: Int) {
        if (page == mutable.value.page) return
        cancelWork()
        mutable.update { it.copy(page = page, cursor = it.game.moves.size, variation = emptyList(), status = "离线引擎已就绪") }
        if (page == 0 && !mutable.value.game.finished && !mutable.value.humanTurn) advance()
    }
    fun load(game: GameRecord) {
        cancelWork()
        mutable.update { it.copy(game = game, page = 1, cursor = game.moves.size, variation = emptyList(), error = null) }
    }
    fun cursor(ply: Int) { mutable.update { it.copy(cursor = ply.coerceIn(0, it.game.moves.size), variation = emptyList()) } }
    fun step(delta: Int) {
        val state = mutable.value
        if (state.variation.isNotEmpty()) mutable.update { it.copy(variationStep = (it.variationStep + delta).coerceIn(0, it.variation.size)) }
        else cursor(state.cursor + delta)
    }
    fun showVariation(best: Boolean) {
        val state = mutable.value
        val review = state.chosenReview ?: return
        val base = review.ply - 1
        val pv = if (best) review.best.pv else review.played.pv
        val safe = ChessRules.legalVariation(state.game.moves.take(base), pv)
        mutable.update { it.copy(variation = safe, variationBase = base, variationStep = 0) }
    }
    fun retry() { if (mutable.value.page == 0) advance() else analyzeSelected() }
    fun analyzeSelected() {
        val state = mutable.value
        if (!state.ready || state.busy || state.cursor == 0) return
        val token = generation
        mutable.update { it.copy(busy = true, error = null) }
        work = viewModelScope.launch {
            try { analyzePly(state.cursor, true, token) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
            finally { if (token == generation) mutable.update { it.copy(busy = false, status = "本步复评完成") } }
        }
    }
    fun reviewAll() {
        if (!mutable.value.ready || mutable.value.busy || mutable.value.game.moves.isEmpty()) return
        val token = generation
        mutable.update { it.copy(busy = true, reviewDone = 0, error = null) }
        work = viewModelScope.launch {
            try {
                val game = mutable.value.game
                for (ply in 1..game.moves.size) {
                    currentCoroutineContext().ensureActive()
                    if (game.reviews.find { it.ply == ply }?.provisional != false) analyzePly(ply, true, token)
                    mutable.update { it.copy(reviewDone = ply) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = "复评失败：${e.message}") } }
            finally { if (token == generation) mutable.update { it.copy(busy = false, status = "复评已保存") } }
        }
    }
    fun pauseReview() { cancelWork(); mutable.update { it.copy(status = "复评已暂停，已完成的结果已保存") } }
    fun resign() {
        if (mutable.value.game.finished) return
        cancelWork()
        val game = mutable.value.game.copy(finished = true, result = if (mutable.value.game.humanWhite) "0-1" else "1-0", ending = "认输")
        mutable.update { it.copy(game = game, status = "对局已结束") }
        viewModelScope.launch { persist(game) }
    }
    fun claimDraw() {
        val state = mutable.value
        if (state.busy || !state.humanTurn || state.game.finished) return
        val reason = ChessRules.drawClaim(state.game.moves) ?: return
        cancelWork()
        val game = state.game.copy(finished = true, result = "1/2-1/2", ending = reason)
        mutable.update { it.copy(game = game, status = "和棋 · $reason") }
        viewModelScope.launch { persist(game) }
    }
    fun share(diagnostics: Boolean): Intent {
        val game = mutable.value.game
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "yibu-${game.id}.${if (diagnostics) "json" else "pgn"}")
        file.writeText(if (diagnostics) repository.diagnostics(game) else ChessRules.pgn(game))
        val uri = FileProvider.getUriForFile(getApplication(), "cn.yibu.chess.files", file)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = if (diagnostics) "application/json" else "application/x-chess-pgn"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, if (diagnostics) "导出对局诊断" else "分享棋谱")
    }
    fun shareLicenses(): Intent {
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "yibu-open-source.txt")
        getApplication<Application>().assets.open("licenses/OPEN_SOURCE.txt").use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = FileProvider.getUriForFile(getApplication(), "cn.yibu.chess.files", file)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "开源许可证")
    }
    fun pauseForBackground() {
        if (mutable.value.busy) { cancelWork(); mutable.update { it.copy(status = "已暂停计算，棋谱已保存") } }
    }
    fun resumeForeground() {
        if (mutable.value.ready && mutable.value.page == 0 && !mutable.value.game.finished && !mutable.value.humanTurn && !mutable.value.busy) advance()
    }
    fun resultChinese(game: GameRecord): String = when (game.result) {
        "1-0" -> "白方获胜"; "0-1" -> "黑方获胜"; "1/2-1/2" -> "和棋"; else -> "进行中"
    }
    override fun onCleared() { work?.cancel(); engine.stop(); super.onCleared() }
}
