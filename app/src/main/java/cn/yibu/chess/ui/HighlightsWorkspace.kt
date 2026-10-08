package cn.yibu.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.yibu.chess.core.*
import kotlinx.coroutines.delay

/** A short, interruptible tour. Its branches never alter the saved game. */
@Composable
internal fun HighlightsWorkspace(game: GameRecord, highlights: List<ReviewHighlight>, flipped: Boolean,
    onClose: () -> Unit, onFlip: () -> Unit) {
    if (highlights.isEmpty()) return
    var point by remember(game.id, highlights) { mutableIntStateOf(0) }
    var frame by remember(game.id, highlights) { mutableIntStateOf(0) }
    var playing by remember(game.id, highlights) { mutableStateOf(true) }
    var complete by remember(game.id, highlights) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) playing = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun lastFrame(index: Int): Int = highlights[index].lesson?.variation?.take(3)?.size?.let { it + 2 } ?: 1
    LaunchedEffect(playing, game.id, highlights) {
        if (!playing) return@LaunchedEffect
        var index = point
        var phase = frame
        while (true) {
            delay(if (phase == 0 || phase == 2) 1800 else 3400)
            if (!playing) return@LaunchedEffect
            if (phase < lastFrame(index)) { phase++; frame = phase }
            else if (index < highlights.lastIndex) { index++; phase = 0; point = index; frame = 0 }
            else { complete = true; playing = false; break }
        }
    }
    val highlight = highlights[point]
    val root = remember(game.moves, highlight.ply) { game.moves.take(highlight.ply - 1) }
    val line = highlight.lesson?.variation?.take(3).orEmpty()
    val history = when {
        frame == 1 -> root + game.moves[highlight.ply - 1]
        frame >= 3 -> root + line.take(frame - 2)
        else -> root
    }
    val fen = remember(history) { ChessRules.board(history).fen }
    val step = highlight.lesson?.steps?.getOrNull(frame - 3)
    val actor = if (highlight.ply % 2 == 1) "白方" else "黑方"
    val san = remember(game.id, highlight.ply, game.moves) { ChessRules.san(root, game.moves[highlight.ply - 1]) }
    val label = when {
        frame == 0 -> "第 ${highlight.ply} 步之前"
        frame == 1 -> "实战 · $actor $san"
        frame == 2 -> "回到起点 · 看推荐走法"
        else -> "推荐路线 ${frame - 2} / ${line.size}"
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boardSize = minOf(maxWidth - 8.dp, maxHeight * .43f, 340.dp).coerceAtLeast(140.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (complete) "复盘完成" else "本局关键点", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("${point + 1} / ${highlights.size} · ${highlight.title}", color = Muted, fontSize = 13.sp,
                        modifier = Modifier.testTag("highlight-title"))
                }
                TextButton(onClick = { playing = false; onClose() }) { Text("逐步复盘") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                highlights.forEachIndexed { index, _ ->
                    LinearProgressIndicator(progress = { if (index <= point) 1f else 0f },
                        modifier = Modifier.weight(1f).height(5.dp), color = Accent, trackColor = Soft)
                }
            }
            Box(Modifier.align(Alignment.CenterHorizontally).size(boardSize)
                .background(Ink, RoundedCornerShape(15.dp)).padding(4.dp).testTag("highlight-board")) {
                ChessBoard(fen, flipped, null, emptySet(), if (frame == 0 || frame == 2) null else history.lastOrNull(),
                    arrow = if (frame == 2) line.firstOrNull() else null,
                    animationKey = game.id + highlight.ply) {}
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).testTag("highlight-position"))
                IconAction(ChessIcon.FLIP, "翻转棋盘", onFlip)
            }
            Column(Modifier.weight(1f).fillMaxWidth().background(Soft, RoundedCornerShape(16.dp))
                .verticalScroll(key(point, frame) { rememberScrollState() }).padding(14.dp).testTag("highlight-notes"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when {
                    frame <= 1 -> highlight.reason
                    frame == 2 -> highlight.lesson?.why.orEmpty()
                    else -> step?.title.orEmpty()
                }, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.testTag("highlight-explanation"))
                if (frame >= 3) Text(step?.explanation.orEmpty(), fontSize = 14.sp, lineHeight = 22.sp)
                if (frame >= 2) Text("这是引擎参考路线；对手改变走法时，需要重新判断。", color = Muted, fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { playing = false; complete = false; point--; frame = 0 }, enabled = point > 0) { Text("上个点") }
                FilledTonalButton(onClick = {
                    if (complete) { point = 0; frame = 0; complete = false; playing = true }
                    else playing = !playing
                }) { Text(if (complete) "再看一次" else if (playing) "暂停" else "继续播放") }
                TextButton(onClick = { playing = false; complete = false; point++; frame = 0 }, enabled = point < highlights.lastIndex) { Text("下个点") }
            }
        }
    }
}
