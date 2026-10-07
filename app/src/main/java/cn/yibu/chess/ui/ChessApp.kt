package cn.yibu.chess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yibu.chess.AppState
import cn.yibu.chess.BuildConfig
import cn.yibu.chess.GameViewModel
import cn.yibu.chess.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.tanh

private val Accent = Color(0xFFB6D99A)
private val Background = Color(0xFF111813)
private val Panel = Color(0xFF1D2920)
private val Muted = Color(0xFFA5B0A4)

private fun gradeColor(grade: Grade): Color = when (grade) {
    Grade.BRILLIANT -> Color(0xFF69D7CB)
    Grade.GREAT, Grade.BEST, Grade.EXCELLENT -> Accent
    Grade.INACCURACY -> Color(0xFFE4CE6C)
    Grade.MISTAKE -> Color(0xFFF0AC70)
    Grade.BLUNDER -> Color(0xFFEE8F85)
    else -> Muted
}

@Composable
fun ChessApp(model: GameViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var newDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    var resignDialog by remember { mutableStateOf(false) }
    var promotion by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember(state.game.id, state.boardHistory) { mutableStateOf<Int?>(null) }
    var flipOverride by remember(state.game.id) { mutableStateOf(false) }
    val fen = remember(state.boardHistory) { ChessRules.board(state.boardHistory).fen }
    val legal = remember(state.boardHistory) { ChessRules.legal(state.boardHistory) }
    val targets = remember(selected, legal) { legal.filter { it.take(2) == selected?.let(ChessRules::squareName) }.map { ChessRules.squareIndex(it.substring(2, 4)) }.toSet() }
    val colors = darkColorScheme(primary = Accent, onPrimary = Background, background = Background,
        surface = Panel, onSurface = Color(0xFFEAF0E5), surfaceVariant = Color(0xFF2B382D), onSurfaceVariant = Muted)
    MaterialTheme(colorScheme = colors) {
        Scaffold(containerColor = Background, bottomBar = {
            NavigationBar(containerColor = Background) {
                listOf("对弈" to "♞", "复盘" to "↗", "棋谱" to "☷").forEachIndexed { index, (name, glyph) ->
                    NavigationBarItem(selected = state.page == index, onClick = { model.page(index) },
                        icon = { Text(glyph, fontSize = 23.sp) }, label = { Text(name) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Panel))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("♞", color = Accent, fontSize = 34.sp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("弈步", fontWeight = FontWeight.Bold, fontSize = 23.sp)
                        Text("每一步，都看得更清楚", color = Muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { aboutDialog = true }) { Text("说明") }
                    FilledTonalButton(onClick = { newDialog = true }, enabled = state.ready) { Text("新局") }
                }
                if (state.page == 2) {
                    Library(state, model)
                } else {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (state.page == 1) "逐步复盘" else state.game.difficulty.chinese, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (state.page == 1) "${state.game.moves.size} 步 · ${model.resultChinese(state.game)}" else "你执${if (state.game.humanWhite) "白" else "黑"} · 手机本地 Stockfish", color = Muted, fontSize = 12.sp)
                            }
                            Text("● ${if (state.ready) "离线就绪" else "准备中"}", color = if (state.ready) Accent else Muted, fontSize = 12.sp)
                        }
                        if (state.page == 1) EvaluationChart(state.game, state.cursor, model::cursor)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (state.variation.isNotEmpty()) "推荐／实战分支 · 第 ${state.variationStep} 步" else if (state.page == 1) "第 ${state.cursor} / ${state.game.moves.size} 步" else if (state.humanTurn) "轮到你走棋" else "Stockfish 的回合", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { flipOverride = !flipOverride }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("翻转", fontSize = 12.sp) }
                        }
                        ChessBoard(fen, flipped = !state.game.humanWhite xor flipOverride,
                            selected = selected, targets = if (state.page == 0 && !state.busy) targets else emptySet(),
                            lastMove = state.boardHistory.lastOrNull(),
                            arrow = if (state.variation.isNotEmpty() && state.variationStep == 0) state.variation.first() else null) { square ->
                            if (state.page == 0 && state.ready && !state.busy && state.humanTurn && !state.game.finished) {
                                val choices = legal.filter { it.take(2) == selected?.let(ChessRules::squareName) && it.substring(2, 4) == ChessRules.squareName(square) }
                                when {
                                    choices.size > 1 -> { promotion = choices; selected = null }
                                    choices.size == 1 -> { model.play(choices.first()); selected = null }
                                    legal.any { it.take(2) == ChessRules.squareName(square) } -> selected = if (selected == square) null else square
                                    else -> selected = null
                                }
                            }
                        }
                        if (state.page == 1) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { model.cursor(0) }, modifier = Modifier.weight(1f)) { Text("起始") }
                                FilledTonalButton(onClick = { model.step(-1) }, modifier = Modifier.weight(1f)) { Text("上一步") }
                                FilledTonalButton(onClick = { model.step(1) }, modifier = Modifier.weight(1f)) { Text("下一步") }
                                OutlinedButton(onClick = { model.cursor(state.game.moves.size) }, modifier = Modifier.weight(1f)) { Text("末尾") }
                            }
                        }
                        MoveStrip(state, model)
                        if (state.busy || (!state.ready && state.error == null)) {
                            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(state.status, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                if (state.page == 1 && state.busy) TextButton(onClick = model::pauseReview) { Text("暂停") }
                            }
                        }
                        RatingCard(state, model)
                        if (state.error != null) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF422B28))) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(state.error!!, fontSize = 13.sp)
                                    TextButton(onClick = model::retry, enabled = state.ready && !state.busy) { Text("继续／重试") }
                                }
                            }
                        }
                        if (state.page == 1) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = model::reviewAll, enabled = state.ready && !state.busy && state.game.moves.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("整盘深度复评") }
                                OutlinedButton(onClick = { context.startActivity(model.share(false)) }, enabled = state.game.moves.isNotEmpty()) { Text("导出 PGN") }
                            }
                            Text("初评与深度复评可能不同；分析按步保存，可暂停后继续。", color = Muted, fontSize = 11.sp)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { model.page(1) }, enabled = state.game.moves.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(if (state.game.finished) "赛后复盘" else "查看复盘") }
                                if (!state.game.finished) OutlinedButton(onClick = { resignDialog = true }, enabled = !state.busy) { Text("认输") }
                            }
                            if (!state.game.finished && state.humanTurn && ChessRules.drawClaim(state.game.moves) != null)
                                OutlinedButton(onClick = model::claimDraw, enabled = !state.busy) { Text("申请和棋 · ${ChessRules.drawClaim(state.game.moves)}") }
                            if (state.game.finished) Text("${model.resultChinese(state.game)} · ${state.game.ending}", color = Accent, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        if (newDialog) NewGameDialog(state.game.difficulty, onDismiss = { newDialog = false }) { difficulty, white ->
            model.newGame(difficulty, white); newDialog = false
        }
        if (promotion.isNotEmpty()) AlertDialog(onDismissRequest = { promotion = emptyList() }, title = { Text("选择升变棋子") }, text = {
            Column {
                listOf('q' to "后 ♛", 'r' to "车 ♜", 'b' to "象 ♝", 'n' to "马 ♞").forEach { (piece, title) ->
                    TextButton(onClick = { promotion.find { it.last() == piece }?.let(model::play); promotion = emptyList() }, modifier = Modifier.fillMaxWidth()) { Text(title) }
                }
            }
        }, confirmButton = {})
        if (resignDialog) AlertDialog(onDismissRequest = { resignDialog = false }, title = { Text("结束这盘对局？") }, text = { Text("棋谱和分析会保留，可以继续复盘。") },
            confirmButton = { TextButton(onClick = { model.resign(); resignDialog = false }) { Text("认输") } }, dismissButton = { TextButton(onClick = { resignDialog = false }) { Text("继续对弈") } })
        if (aboutDialog) AlertDialog(onDismissRequest = { aboutDialog = false }, title = { Text("关于弈步 · ${BuildConfig.VERSION_NAME}") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("离线引擎：Stockfish 17.1\n权重随安装包提供，无需网络权限。\n轻松／接近档是练习难度，不代表真实等级分。", fontSize = 13.sp)
                Text("评级根据最佳着与实际着的预期得分差：\n5–10 个百分点：?!\n10–20 个百分点：?\n20 个百分点以上：??\n! 表示关键好棋，!! 表示经深入验证的弃子。", fontSize = 13.sp)
                Text("WDL 是引擎自我对弈的局面质量模型，不是你的真实胜率。实时结果为初评，复盘可深入计算。", color = Muted, fontSize = 12.sp)
                Text("Stockfish：GPLv3-or-later\nchesslib：Apache-2.0\n完整许可和引擎版本记录包含在源码及 APK 内。", fontSize = 12.sp)
                TextButton(onClick = { context.startActivity(model.share(true)) }) { Text("导出对局诊断 JSON") }
                TextButton(onClick = { context.startActivity(model.shareLicenses()) }) { Text("查看／导出开源许可证") }
            }
        }, confirmButton = { TextButton(onClick = { aboutDialog = false }) { Text("知道了") } })
    }
}

@Composable
private fun RatingCard(state: AppState, model: GameViewModel) {
    val review = state.chosenReview
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (review == null) {
                Text(if (state.game.moves.isEmpty()) "从第一步开始" else "本步尚未完成分析", fontWeight = FontWeight.SemiBold)
                Text(if (state.page == 0) "点击棋子，再点击落点。走后会显示评级和推荐着。" else "选择一步棋，点击深度复评即可分析。", color = Muted, fontSize = 13.sp)
                if (state.page == 1 && state.cursor > 0) TextButton(onClick = model::analyzeSelected, enabled = state.ready && !state.busy) { Text("分析本步") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(review.grade.symbol.ifEmpty { "•" }, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = gradeColor(review.grade))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${review.san} · ${review.grade.chinese}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${if (review.provisional) "初评" else "深度复评"} · 深度 ${minOf(review.best.depth, review.played.depth)}", color = Muted, fontSize = 11.sp)
                    }
                    Text(review.played.display(whitePerspective = true, moverWhite = review.moverWhite), fontSize = 17.sp, color = Accent)
                }
                val root = state.game.moves.take(review.ply - 1)
                val bestSan = remember(review, root) { ChessRules.san(root, review.bestMove) }
                Text("推荐 $bestSan  ·  得分损失 ${String.format(Locale.ROOT, "%.1f", review.pointsLost * 100)} 个百分点", color = Accent, fontSize = 13.sp)
                Text(review.explanation, color = Color(0xFFD1DACA), fontSize = 13.sp, lineHeight = 20.sp)
                if (state.page == 1) {
                    Text("推荐：${ChessRules.variationSan(root, review.best.pv.take(6)).joinToString("  ")}", color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { model.showVariation(true) }) { Text("跟走推荐") }
                        OutlinedButton(onClick = { model.showVariation(false) }) { Text("实战变化") }
                        TextButton(onClick = model::analyzeSelected, enabled = !state.busy && state.ready) { Text("复评") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveStrip(state: AppState, model: GameViewModel) {
    val sans = remember(state.game.moves) { ChessRules.sanMoves(state.game.moves) }
    val scroll = rememberLazyListState()
    LaunchedEffect(state.game.moves.size, state.cursor, state.page) {
        if (sans.isNotEmpty()) scroll.animateScrollToItem((if (state.page == 1) state.cursor - 1 else sans.lastIndex).coerceIn(0, sans.lastIndex))
    }
    LazyRow(state = scroll, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(sans) { index, san ->
            val review = state.game.reviews.find { it.ply == index + 1 }
            val active = (if (state.page == 1) state.cursor else state.game.moves.size) == index + 1
            Surface(color = if (active) Color(0xFF354631) else Panel, shape = RoundedCornerShape(9.dp), modifier = Modifier.clickable { if (state.page != 1) model.page(1); model.cursor(index + 1) }) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${index / 2 + 1}${if (index % 2 == 0) "." else "…"} $san", fontSize = 13.sp)
                    Text(review?.grade?.symbol?.ifEmpty { "·" } ?: "…", color = review?.grade?.let(::gradeColor) ?: Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EvaluationChart(game: GameRecord, cursor: Int, onSelect: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("局势变化", fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("白方视角 · 点击跳转", fontSize = 11.sp, color = Muted)
            }
            val points = game.reviews.associate { it.ply to tanh(it.played.whiteScore(it.moverWhite) / 3.0).toFloat() }
            Canvas(Modifier.fillMaxWidth().height(80.dp).pointerInput(game.moves.size) {
                detectTapGestures { onSelect((it.x / size.width * game.moves.size).toInt().coerceIn(0, game.moves.size)) }
            }) {
                val middle = size.height / 2
                drawLine(Color(0xFF50604F), Offset(0f, middle), Offset(size.width, middle), 1.dp.toPx())
                val count = game.moves.size.coerceAtLeast(1)
                val path = Path()
                var previousPly = -2
                (listOf(0 to 0f) + points.toList().sortedBy { it.first }).forEach { (ply, value) ->
                    val x = size.width * ply / count
                    val y = middle - value * (middle - 6.dp.toPx())
                    if (ply != previousPly + 1) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(Accent, 2.dp.toPx(), Offset(x, y))
                    previousPly = ply
                }
                drawPath(path, Accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                val x = size.width * cursor / count
                drawLine(Color(0xFFE1CA78), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
        }
    }
}

@Composable
private fun Library(state: AppState, model: GameViewModel) {
    val formatter = remember { SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("我的棋谱", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("保存在这台手机 · ${state.games.size} 盘", color = Muted, fontSize = 12.sp)
        if (state.games.isEmpty()) Text("开始一盘对弈，棋谱会自动保存。", color = Muted, modifier = Modifier.padding(vertical = 40.dp))
        state.games.forEach { game ->
            Card(Modifier.fillMaxWidth().clickable { model.load(game) }, colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row {
                        Text(game.difficulty.chinese, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(model.resultChinese(game), color = Accent, fontSize = 12.sp)
                    }
                    Text("${formatter.format(Date(game.startedAt))} · 你执${if (game.humanWhite) "白" else "黑"} · ${game.moves.size} 步", color = Muted, fontSize = 12.sp)
                    val mistakes = game.reviews.count { it.moverWhite == game.humanWhite && it.grade in listOf(Grade.MISTAKE, Grade.BLUNDER) }
                    Text("已分析 ${game.reviews.size} 步 · 你的失误 $mistakes 次", color = Muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NewGameDialog(initial: Difficulty, onDismiss: () -> Unit, onStart: (Difficulty, Boolean) -> Unit) {
    var difficulty by remember { mutableStateOf(initial) }
    var white by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("开始一盘练习") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Difficulty.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().clickable { difficulty = option }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = difficulty == option, onClick = { difficulty = option })
                    Column {
                        Text(option.chinese, fontSize = 15.sp)
                        Text(option.description, fontSize = 11.sp, color = Muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("执棋", modifier = Modifier.weight(1f))
                FilterChip(selected = white, onClick = { white = true }, label = { Text("白方") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = !white, onClick = { white = false }, label = { Text("黑方") })
            }
            Text("当前对局会保留在棋谱中。", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
        }
    }, confirmButton = { Button(onClick = { onStart(difficulty, white) }) { Text("开始对弈") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
