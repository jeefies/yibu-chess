package cn.yibu.chess

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.yibu.chess.core.*
import cn.yibu.chess.data.GameRepository
import cn.yibu.chess.data.PlayPreferences
import cn.yibu.chess.diagnostics.RuntimeDiagnostics
import cn.yibu.chess.engine.MaiaModel
import cn.yibu.chess.engine.NativeStockfish
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
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
    val profile: PlayerProfile = PlayerProfile(),
    val games: List<GameRecord> = emptyList(),
    val page: Int = 0,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val analyzing: Boolean = false,
    val transitioning: Boolean = false,
    val status: String = "正在校验离线引擎…",
    val error: String? = null,
    val cursor: Int = 0,
    val variation: List<String> = emptyList(),
    val variationBase: Int = 0,
    val variationStep: Int = 0,
    val reviewDone: Int = 0,
    val settings: PlaySettings = PlaySettings(),
    val brilliantNotices: List<MoveReview> = emptyList(),
    val explainingPly: Int? = null,
    val lessonOpen: Boolean = false,
) {
    val boardHistory: List<String> get() = when {
        page != 1 -> game.moves
        variation.isNotEmpty() -> game.moves.take(variationBase) + variation.take(variationStep)
        lessonOpen -> game.moves.take((cursor - 1).coerceAtLeast(0))
        else -> game.moves.take(cursor)
    }
    val chosenReview: MoveReview? get() = game.reviews.find { it.ply == if (page == 1) cursor else game.moves.size }
    val chosenLesson: MoveLesson? get() = if (page == 1) game.lessons.find { it.ply == cursor } else null
    val humanTurn: Boolean get() = (game.moves.size % 2 == 0) == game.humanWhite
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)
    private val preferences = PlayPreferences(application)
    private val engine = NativeStockfish(application)
    private val maia = MaiaModel(application)
    private val analyzer = MoveAnalyzer(engine)
    private val opponent = Opponent(engine)
    private val humanOpponent = HumanOpponent(maia)
    private val mutable = MutableStateFlow(AppState(settings = preferences.read()))
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var liveAnalysis: Job? = null
    private var generation = 0
    private val persistence = Mutex()

    init {
        viewModelScope.launch { repository.games.collect { games -> mutable.update { it.copy(games = games) } } }
        viewModelScope.launch { repository.profiles.collect { profile ->
            mutable.update { if (profile.ratedGames >= it.profile.ratedGames) it.copy(profile = profile) else it }
        } }
        viewModelScope.launch {
            try {
                val profile = repository.profile()
                val restored = repository.latest()
                val settings = mutable.value.settings
                val resumed = restored?.takeUnless { it.finished }
                val game = resumed?.let { HumanOpponent.prepare(it) } ?: EloRules.newGame(profile, settings.mode, settings.color.humanWhite)
                mutable.update { it.copy(profile = profile, game = game, cursor = game.moves.size) }
                maia.initialize()
                engine.initialize()
                mutable.update { it.copy(ready = true, status = "离线引擎已就绪") }
                if (!mutable.value.humanTurn) advance() else scheduleLiveAnalysis()
            } catch (e: Exception) { mutable.update { it.copy(error = "引擎启动失败：${e.message}", status = "启动失败") } }
        }
    }
    private fun cancelWork(saveSnapshot: Boolean = true) {
        val snapshot = mutable.value.game
        generation++
        work?.cancel()
        liveAnalysis?.cancel()
        engine.stop()
        mutable.update { it.copy(busy = false, analyzing = false, explainingPly = null) }
        if (saveSnapshot && snapshot.moves.isNotEmpty()) viewModelScope.launch { persist(snapshot) }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun configureAndStart(settings: PlaySettings) {
        if (!mutable.value.ready || mutable.value.transitioning) return
        preferences.save(settings)
        mutable.update { it.copy(settings = settings) }
        newGame()
    }
    fun newGame(difficulty: Difficulty = mutable.value.settings.mode, humanWhite: Boolean? = mutable.value.settings.color.humanWhite) {
        if (!mutable.value.ready || mutable.value.transitioning || difficulty !in Difficulty.choices) return
        val previous = mutable.value.game
        cancelWork(false)
        mutable.update { it.copy(busy = true, transitioning = true, error = null) }
        viewModelScope.launch {
            try {
                if (previous.moves.isNotEmpty() || previous.finished) persist(previous)
                val profile = repository.profile()
                val game = EloRules.newGame(profile, difficulty, humanWhite)
                mutable.update { it.copy(profile = profile, game = game, page = 0, cursor = 0, variation = emptyList(), lessonOpen = false,
                    reviewDone = 0, brilliantNotices = emptyList(), status = "新对局已开始") }
                persist(game)
                mutable.update { it.copy(busy = false, transitioning = false) }
                if (!game.humanWhite) advance()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(busy = false, transitioning = false, error = "新局保存失败：${e.message}") } }
        }
    }
    fun play(uci: String) {
        val state = mutable.value
        if (!state.ready || state.busy || state.page != 0 || state.game.finished || !state.humanTurn) return
        try {
            if (uci !in ChessRules.legal(state.game.moves)) return
            // Give a new move priority over optional background analysis.
            cancelWork(saveSnapshot = false)
            val game = finishIfNecessary(state.game.copy(moves = state.game.moves + uci))
            mutable.update { it.copy(game = game, cursor = game.moves.size, brilliantNotices = emptyList()) }
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
        val saved = withContext(Dispatchers.IO) { repository.save(latest) }
        mutable.update { state ->
            val profile = if (saved.profile.ratedGames >= state.profile.ratedGames) saved.profile else state.profile
            state.copy(profile = profile, game = if (state.game.id == saved.game?.id) state.game.copy(ratingChange = saved.game?.ratingChange) else state.game)
        }
    }
    private fun advance() {
        if (!mutable.value.ready || mutable.value.busy || mutable.value.transitioning) return
        val token = generation
        mutable.update { it.copy(busy = true, error = null) }
        work = viewModelScope.launch {
            try {
                persist(mutable.value.game)
                currentCoroutineContext().ensureActive()
                if (token == generation && !mutable.value.game.finished && !mutable.value.humanTurn && mutable.value.page == 0) {
                    mutable.update { it.copy(status = "对手正在思考…") }
                    val before = mutable.value.game
                    val started = SystemClock.elapsedRealtime()
                    val thinkingTime = OpponentPacing.targetMs()
                    val move = if (before.mode == Difficulty.MATCHED) humanOpponent.move(before, mutable.value.games)
                        else opponent.move(before.moves)
                    delay(OpponentPacing.remainingMs(thinkingTime, SystemClock.elapsedRealtime() - started))
                    currentCoroutineContext().ensureActive()
                    if (token != generation || mutable.value.game.id != before.id || mutable.value.page != 0 || mutable.value.game.finished) return@launch
                    val game = withContext(Dispatchers.Default) { finishIfNecessary(before.copy(moves = before.moves + move)) }
                    mutable.update { it.copy(game = game, cursor = game.moves.size) }
                    persist(game)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) mutable.update { it.copy(error = "本次计算失败：${e.message}，可点击继续重试。") } }
            finally {
                if (token == generation) {
                    mutable.update { it.copy(busy = false, status = if (it.game.finished) "${it.game.ending} · ${resultChinese(it.game)}" else "轮到${if (it.humanTurn) "你" else "对手"}走棋") }
                    scheduleLiveAnalysis()
                }
            }
        }
    }
    private fun scheduleLiveAnalysis() {
        val state = mutable.value
        if (!state.ready || state.busy || state.transitioning || state.page != 0 ||
            (!state.humanTurn && !state.game.finished) || liveAnalysis?.isActive == true) return
        val missing = (1..state.game.moves.size).filter { ply -> state.game.reviews.none { it.ply == ply } }
        // Verify both sides of the current turn before catching up older interrupted work.
        val pending = missing.filter { it >= state.game.moves.size - 1 } + missing.filter { it < state.game.moves.size - 1 }
        if (pending.isEmpty()) return
        val token = generation
        mutable.update { it.copy(analyzing = true) }
        liveAnalysis = viewModelScope.launch {
            try {
                for (ply in pending) {
                    currentCoroutineContext().ensureActive()
                    if (token != generation) return@launch
                    analyzePly(ply, false, token)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generation) mutable.update { it.copy(error = "后台分析暂未完成：${e.message}，可在复盘重试；仍可继续走棋。") }
            } finally {
                if (token == generation) mutable.update { it.copy(analyzing = false,
                    status = if (it.game.finished) "${it.game.ending} · ${resultChinese(it.game)}" else "轮到你走棋") }
            }
        }
    }
    private suspend fun analyzePly(ply: Int, deep: Boolean, token: Int) {
        if (token != generation) return
        val game = mutable.value.game
        mutable.update { it.copy(status = if (it.page == 1) {
            if (deep) "深度复评 $ply / ${game.moves.size}" else "正在分析第 ${(ply + 1) / 2} 回合…"
        } else "后台分析第 $ply 步${if (game.finished) "" else " · 可继续走棋"}") }
        val scoringElo = scoringElo(game, ply)
        val review = withContext(Dispatchers.Default) { analyzer.analyze(game.moves.take(ply - 1), game.moves[ply - 1], deep, scoringElo) }
        currentCoroutineContext().ensureActive()
        if (token != generation || game.id != mutable.value.game.id) return
        val updated = mutable.value.game.copy(reviews = (mutable.value.game.reviews.filterNot { it.ply == ply } + review).sortedBy { it.ply },
            lessons = mutable.value.game.lessons.filterNot { it.ply == ply && it.recommendedMove != review.bestMove })
        mutable.update { state ->
            val notices = state.brilliantNotices.filterNot { it.ply == ply }
            state.copy(game = updated, brilliantNotices = if (state.page == 0 && review.ply >= state.game.moves.size - 1 && review.grade == Grade.BRILLIANT && !review.provisional)
                (notices + review).sortedBy { it.ply }.takeLast(2) else notices)
        }
        persist(updated)
        // Only a promising sacrifice merits live verification; ordinary grades stay in review.
        if (!deep && review.pointsLost < 0.02 && ChessRules.legal(game.moves.take(ply - 1)).size > 1) {
            val sacrifice = (review.playedExpectedPoints ?: 0.5) >= 0.5 && ChessRules.substantialSacrifice(game.moves.take(ply - 1), review.played.pv)
            if (sacrifice) analyzePly(ply, true, token)
        }
    }
    private fun scoringElo(game: GameRecord, ply: Int): Int =
        if ((ply % 2 == 1) == game.humanWhite) game.playerEloAtStart ?: mutable.value.profile.rating
        else game.opponentElo ?: if (game.mode == Difficulty.STRONG) 2800 else 500

    fun page(page: Int) {
        if (mutable.value.transitioning) return
        if (page == mutable.value.page) return
        cancelWork()
        mutable.update { it.copy(page = page, cursor = it.game.moves.size, variation = emptyList(), lessonOpen = false, status = "离线引擎已就绪") }
        if (page == 0) {
            if (!mutable.value.game.finished && !mutable.value.humanTurn) advance() else scheduleLiveAnalysis()
        }
    }
    fun load(game: GameRecord) {
        if (mutable.value.transitioning) return
        cancelWork()
        mutable.update { it.copy(game = HumanOpponent.prepare(game), page = 1, cursor = game.moves.size, variation = emptyList(), lessonOpen = false, brilliantNotices = emptyList(), error = null) }
    }
    fun cursor(ply: Int) { mutable.update { it.copy(cursor = ply.coerceIn(0, it.game.moves.size), variation = emptyList(), lessonOpen = false) } }
    fun step(delta: Int) {
        val state = mutable.value
        if (state.variation.isNotEmpty()) mutable.update { it.copy(variationStep = (it.variationStep + delta).coerceIn(0, it.variation.size)) }
        else if (!state.lessonOpen) cursor(state.cursor + delta)
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
    fun explainSelected() {
        val state = mutable.value
        if (state.page != 1 || state.cursor == 0) return
        if (state.chosenLesson != null) { showLessonVariation(); return }
        if (!state.ready || state.busy) return
        val token = generation
        val ply = state.cursor
        val gameId = state.game.id
        mutable.update { it.copy(busy = true, explainingPly = ply, lessonOpen = true, variation = emptyList(),
            variationStep = 0, error = null, status = "深入讲解第 $ply 步…") }
        work = viewModelScope.launch {
            try {
                if (state.chosenReview?.canReuseDeep(scoringElo(state.game, ply)) != true) analyzePly(ply, true, token)
                currentCoroutineContext().ensureActive()
                if (token != generation || mutable.value.game.id != gameId) return@launch
                val game = mutable.value.game
                val review = game.reviews.first { it.ply == ply }
                val lesson = withContext(Dispatchers.Default) { MoveCoach.explain(game.moves.take(ply - 1), review) }
                currentCoroutineContext().ensureActive()
                if (token != generation || mutable.value.game.id != gameId) return@launch
                mutable.update {
                    val open = it.lessonOpen && it.page == 1 && it.cursor == ply
                    it.copy(game = it.game.copy(lessons = (it.game.lessons.filterNot { note -> note.ply == ply } + lesson).sortedBy { note -> note.ply }),
                        variation = if (open) lesson.variation else it.variation,
                        variationBase = if (open) ply - 1 else it.variationBase,
                        variationStep = if (open) 0 else it.variationStep)
                }
                persist(mutable.value.game)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == generation) mutable.update { it.copy(error = "本步讲解失败：${e.message}，可再次点击讲解。") } }
            finally { if (token == generation) mutable.update { it.copy(busy = false, explainingPly = null,
                status = if (it.game.lessons.any { note -> note.ply == ply }) "第 $ply 步讲解已保存" else "本步讲解未完成，可重试") } }
        }
    }
    fun showLessonVariation() {
        val state = mutable.value
        val lesson = state.chosenLesson ?: return
        val base = lesson.ply - 1
        val safe = ChessRules.legalVariation(state.game.moves.take(base), lesson.variation)
        mutable.update { it.copy(lessonOpen = true, variation = safe, variationBase = base, variationStep = 0) }
    }
    fun closeLesson() { mutable.update { it.copy(lessonOpen = false, variation = emptyList(), variationStep = 0) } }
    fun lessonSeek(step: Int) {
        if (!mutable.value.lessonOpen) return
        mutable.update { it.copy(variationStep = step.coerceIn(0, it.variation.size)) }
    }
    fun analyzeSelected() {
        val state = mutable.value
        if (!state.ready || state.busy || state.page != 1 || state.cursor == 0) return
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
        if (!mutable.value.ready || mutable.value.busy || mutable.value.page != 1 || mutable.value.game.moves.isEmpty()) return
        val token = generation
        mutable.update { it.copy(busy = true, reviewDone = 0, error = null) }
        work = viewModelScope.launch {
            try {
                val game = mutable.value.game
                for (ply in 1..game.moves.size) {
                    currentCoroutineContext().ensureActive()
                    val review = mutable.value.game.reviews.find { it.ply == ply }
                    if (review?.canReuseDeep(scoringElo(game, ply)) != true) analyzePly(ply, true, token)
                    mutable.update { it.copy(reviewDone = ply) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = "复评失败：${e.message}") } }
            finally { if (token == generation) mutable.update { it.copy(busy = false, status = "复评已保存") } }
        }
    }
    fun pauseReview() { cancelWork(); mutable.update { it.copy(status = "复评已暂停，已完成的结果已保存") } }
    fun resign() {
        if (!mutable.value.ready || mutable.value.transitioning || mutable.value.game.finished) return
        cancelWork()
        val game = mutable.value.game.copy(finished = true, result = if (mutable.value.game.humanWhite) "0-1" else "1-0", ending = "认输")
        mutable.update { it.copy(game = game, status = "对局已结束") }
        viewModelScope.launch { persist(game); scheduleLiveAnalysis() }
    }
    fun claimDraw() {
        val state = mutable.value
        if (state.busy || !state.humanTurn || state.game.finished) return
        val reason = ChessRules.drawClaim(state.game.moves) ?: return
        cancelWork()
        val game = state.game.copy(finished = true, result = "1/2-1/2", ending = reason)
        mutable.update { it.copy(game = game, status = "和棋 · $reason") }
        viewModelScope.launch { persist(game); scheduleLiveAnalysis() }
    }
    fun delete(game: GameRecord) {
        if (mutable.value.transitioning) return
        val active = mutable.value.game.id == game.id
        val snapshot = mutable.value.game
        if (active) cancelWork(false)
        mutable.update { it.copy(transitioning = true) }
        viewModelScope.launch {
            try {
                if (active && snapshot.finished) persist(snapshot)
                persistence.withLock { repository.delete(game.id) }
                mutable.update { state ->
                    if (state.game.id == game.id) state.copy(game = EloRules.newGame(state.profile, state.settings.mode, state.settings.color.humanWhite),
                        page = 2, cursor = 0, variation = emptyList(), lessonOpen = false, busy = false, reviewDone = 0,
                        brilliantNotices = emptyList(), transitioning = false, status = "棋谱已删除")
                    else state.copy(transitioning = false)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(transitioning = false, error = "删除失败：${e.message}") } }
        }
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
    fun shareRuntimeDiagnostics(): Intent {
        val application = getApplication<Application>()
        val dir = File(application.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "yibu-runtime-diagnostics.json")
        file.writeText(RuntimeDiagnostics.collect(application))
        val uri = FileProvider.getUriForFile(application, "cn.yibu.chess.files", file)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "导出运行诊断")
    }
    fun pauseForBackground() {
        if ((mutable.value.busy || mutable.value.analyzing) && !mutable.value.transitioning) { cancelWork(); mutable.update { it.copy(status = "已暂停计算，棋谱已保存") } }
    }
    fun resumeForeground() {
        val state = mutable.value
        if (state.ready && state.page == 0 && !state.busy) {
            if (!state.game.finished && !state.humanTurn) advance() else scheduleLiveAnalysis()
        }
    }
    fun resultChinese(game: GameRecord): String = when (game.result) {
        "1-0" -> "白方获胜"; "0-1" -> "黑方获胜"; "1/2-1/2" -> "和棋"; else -> "进行中"
    }
    override fun onCleared() { work?.cancel(); liveAnalysis?.cancel(); engine.stop(); super.onCleared() }
}
