package cn.yibu.chess.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.yibu.chess.core.ChessRules
import cn.yibu.chess.core.BoardTransition
import cn.yibu.chess.core.KingBreak
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChessBoard(
    fen: String,
    flipped: Boolean,
    selected: Int?,
    targets: Set<Int>,
    lastMove: String?,
    arrow: String? = null,
    animationKey: Long = 0,
    kingBreak: KingBreak? = null,
    onKingBreakFinished: (Long) -> Unit = {},
    onKingBreakStarted: (Long) -> Unit = {},
    onSquare: (Int) -> Unit,
) {
    // Read the latest turn/readiness/selection callback without restarting a tap gesture.
    val currentOnSquare by rememberUpdatedState(onSquare)
    val pieces = remember(fen) { ChessRules.fenPieces(fen) }
    val highlighted = remember(lastMove) {
        lastMove?.takeIf { it.length >= 4 }?.let { setOf(ChessRules.squareIndex(it.take(2)), ChessRules.squareIndex(it.substring(2, 4))) }.orEmpty()
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL); textAlign = Paint.Align.CENTER } }
    val progress = remember(flipped, animationKey) { Animatable(1f) }
    var previousFen by remember(flipped, animationKey) { mutableStateOf(fen) }
    var scene by remember(flipped, animationKey) { mutableStateOf<PieceScene?>(null) }
    var completedScene by remember(flipped, animationKey) { mutableStateOf<PieceScene?>(null) }
    // Prepare the first visual frame synchronously: an effect-only plan would show
    // the destination for one frame before it could publish the moving pieces.
    val plannedScene = remember(fen, flipped, animationKey) {
        val transition = if (ValueAnimator.areAnimatorsEnabled()) BoardTransition.between(previousFen, fen) else null
        transition?.let {
            val oldScene = scene
            val fraction = progress.value
            fun position(square: Int): Offset {
                val moving = oldScene?.motions?.find { motion -> motion.to == square }
                return moving?.let { motion -> motion.start + (boardPoint(square, flipped) - motion.start) * fraction }
                    ?: boardPoint(square, flipped)
            }
            val moving = it.motions.map { motion -> FlyingPiece(motion.to, motion.piece, position(motion.from)) }.toMutableList()
            // A quick next step keeps any previous piece in flight from its current position.
            oldScene?.motions?.filter { old -> moving.none { motion -> motion.to == old.to } &&
                it.motions.none { motion -> motion.from == old.to } && pieces[old.to] == old.piece }?.forEach { old ->
                moving += old.copy(start = position(old.to))
            }
            PieceScene(fen, moving, it.fades)
        }
    }
    LaunchedEffect(fen, plannedScene, flipped, animationKey) {
        previousFen = fen
        if (plannedScene == null) {
            scene = null
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        scene = plannedScene
        progress.animateTo(1f, tween(250, easing = CubicBezierEasing(.77f, 0f, .175f, 1f)))
        completedScene = plannedScene
        scene = null
    }
    val activeScene = plannedScene?.takeUnless { it === completedScene }
    val burst = remember(kingBreak?.gameId) { Animatable(0f) }
    var breaking by remember(kingBreak?.gameId) { mutableStateOf(false) }
    var fallenKing by remember(flipped, animationKey, fen) { mutableStateOf<Int?>(null) }
    val finishedCallback by rememberUpdatedState(onKingBreakFinished)
    val startedCallback by rememberUpdatedState(onKingBreakStarted)
    LaunchedEffect(kingBreak?.gameId) {
        val event = kingBreak ?: return@LaunchedEffect
        if (ValueAnimator.areAnimatorsEnabled()) {
            // Let the mating move land first. This one-time end celebration lasts 800 ms.
            if (event.checkmate) delay(260)
            breaking = true
            startedCallback(event.gameId)
            burst.animateTo(1f, tween(800, easing = androidx.compose.animation.core.LinearEasing))
            fallenKing = event.square
            breaking = false
        }
        finishedCallback(event.gameId)
    }
    val hidden = activeScene?.let { it.motions.map { motion -> motion.to }.toSet() + it.fades.filter { fading -> fading.appearing }.map { fading -> fading.square } }.orEmpty()
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
        .semantics { contentDescription = "国际象棋棋盘，${if (flipped) "黑方" else "白方"}视角" }
        .pointerInput(flipped, fen) {
            detectTapGestures { position ->
                val col = (position.x / (size.width / 8f)).toInt().coerceIn(0, 7)
                val row = (position.y / (size.height / 8f)).toInt().coerceIn(0, 7)
                currentOnSquare(if (flipped) row * 8 + 7 - col else (7 - row) * 8 + col)
            }
        }) {
      Canvas(Modifier.matchParentSize()) {
        val cell = size.width / 8
        fun center(index: Int): Offset {
            val col = if (flipped) 7 - index % 8 else index % 8
            val row = if (flipped) index / 8 else 7 - index / 8
            return Offset((col + .5f) * cell, (row + .5f) * cell)
        }
        for (row in 0..7) for (col in 0..7) {
            val index = if (flipped) row * 8 + 7 - col else (7 - row) * 8 + col
            val topLeft = Offset(col * cell, row * cell)
            val light = (row + col) % 2 == 0
            drawRect(if (light) Color(0xFFEEEBDD) else Color(0xFF799184), topLeft, Size(cell, cell))
            if (index in highlighted) drawRect(Color(0xFFDFCE71).copy(alpha = .56f), topLeft, Size(cell, cell))
            if (index == selected) drawRect(Color(0xFFE6C463).copy(alpha = .80f), topLeft, Size(cell, cell))
            val piece = pieces[index]
            val c = center(index)
            if (piece != ' ' && index !in hidden && index != fallenKing && !(breaking && index == kingBreak?.square)) drawChessPiece(piece, c, cell, paint)
            if (index in targets) {
                if (piece == ' ') drawCircle(Color(0xFF263D28).copy(alpha = .38f), cell * .12f, c)
                else drawCircle(Color(0xFFE8C858), cell * .43f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(cell * .07f))
            }
            paint.style = Paint.Style.FILL
            paint.textSize = cell * .19f
            paint.color = android.graphics.Color.rgb(32, 56, 44)
            if (col == 0) drawContext.canvas.nativeCanvas.drawText((index / 8 + 1).toString(), topLeft.x + cell * .12f, topLeft.y + cell * .22f, paint)
            if (row == 7) drawContext.canvas.nativeCanvas.drawText(('a' + index % 8).toString(), topLeft.x + cell * .89f, topLeft.y + cell * .94f, paint)
        }
        arrow?.takeIf { it.length >= 4 }?.let { move ->
            val start = center(ChessRules.squareIndex(move.take(2)))
            val end = center(ChessRules.squareIndex(move.substring(2, 4)))
            val angle = atan2(end.y - start.y, end.x - start.x)
            val shortened = Offset(end.x - cos(angle) * cell * .20f, end.y - sin(angle) * cell * .20f)
            drawLine(Color(0xFFF2B83C).copy(alpha = .88f), start, shortened, cell * .12f, StrokeCap.Round)
            val head = Path().apply {
                moveTo(end.x, end.y)
                lineTo(end.x - cos(angle - .55f) * cell * .45f, end.y - sin(angle - .55f) * cell * .45f)
                lineTo(end.x - cos(angle + .55f) * cell * .45f, end.y - sin(angle + .55f) * cell * .45f)
                close()
            }
            drawPath(head, Color(0xFFF2B83C).copy(alpha = .92f))
        }
      }
      activeScene?.fades?.forEach { fading ->
          Canvas(Modifier.matchParentSize().graphicsLayer {
              val fraction = if (scene === activeScene) progress.value else 0f
              alpha = if (fading.appearing) fraction else 1f - fraction
          }) {
              val cell = size.width / 8
              drawChessPiece(fading.piece, (boardPoint(fading.square, flipped) + Offset(.5f, .5f)) * cell, cell, paint)
          }
      }
      activeScene?.motions?.forEach { moving ->
          Canvas(Modifier.matchParentSize().graphicsLayer {
              val cell = size.width / 8f
              val destination = boardPoint(moving.to, flipped)
              val fraction = if (scene === activeScene) progress.value else 0f
              val distance = (moving.start - destination) * (1f - fraction) * cell
              translationX = distance.x
              translationY = distance.y
          }.testTag("piece-motion-${ChessRules.squareName(moving.to)}")) {
              val cell = size.width / 8
              drawChessPiece(moving.piece, (boardPoint(moving.to, flipped) + Offset(.5f, .5f)) * cell, cell, paint)
          }
      }
      if (breaking && kingBreak != null) {
          val point = boardPoint(kingBreak.square, flipped)
          val rim = listOf(Offset(0f, 0f), Offset(.5f, 0f), Offset(1f, 0f), Offset(1f, .5f),
              Offset(1f, 1f), Offset(.5f, 1f), Offset(0f, 1f), Offset(0f, .5f))
          rim.indices.forEach { index ->
              val direction = (rim[index] + rim[(index + 1) % rim.size]) / 2f - Offset(.5f, .5f)
              Canvas(Modifier.matchParentSize().graphicsLayer {
                  val cell = size.width / 8f
                  val t = burst.value
                  val launch = CubicBezierEasing(.23f, 1f, .32f, 1f).transform(t)
                  transformOrigin = TransformOrigin((point.x + .5f) / 8f, (point.y + .5f) / 8f)
                  translationX = direction.x * cell * 2.8f * launch
                  translationY = (direction.y * 1.8f * launch - .35f * launch + 1.5f * t * t) * cell
                  rotationZ = (if (index % 2 == 0) 1 else -1) * (45f + index * 9f) * t
                  alpha = ((1f - t) / .35f).coerceIn(0f, 1f)
              }.testTag("king-shard-$index")) {
                  val cell = size.width / 8f
                  val start = point * cell
                  val mask = Path().apply {
                      moveTo(start.x + .5f * cell, start.y + .53f * cell)
                      lineTo(start.x + rim[index].x * cell, start.y + rim[index].y * cell)
                      lineTo(start.x + rim[(index + 1) % rim.size].x * cell, start.y + rim[(index + 1) % rim.size].y * cell)
                      close()
                  }
                  clipPath(mask) { drawChessPiece(if (kingBreak.white) 'K' else 'k', start + Offset(.5f, .5f) * cell, cell, paint) }
              }
          }
      }
    }
}

private data class FlyingPiece(val to: Int, val piece: Char, val start: Offset)
private data class PieceScene(val fen: String, val motions: List<FlyingPiece>, val fades: List<cn.yibu.chess.core.FadingPiece>)
private fun boardPoint(index: Int, flipped: Boolean): Offset = Offset(
    (if (flipped) 7 - index % 8 else index % 8).toFloat(),
    (if (flipped) index / 8 else 7 - index / 8).toFloat(),
)
private val glyphs = mapOf('k' to "♚", 'q' to "♛", 'r' to "♜", 'b' to "♝", 'n' to "♞", 'p' to "♟")
private fun DrawScope.drawChessPiece(piece: Char, center: Offset, cell: Float, paint: Paint) {
    paint.textSize = cell * .84f
    val baseline = center.y - (paint.ascent() + paint.descent()) / 2 - cell * .02f
    if (piece.isUpperCase()) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = cell * .035f
        paint.color = android.graphics.Color.rgb(39, 49, 36)
        drawContext.canvas.nativeCanvas.drawText(glyphs.getValue(piece.lowercaseChar()), center.x, baseline, paint)
    }
    paint.style = Paint.Style.FILL
    paint.color = if (piece.isUpperCase()) android.graphics.Color.rgb(252, 250, 236) else android.graphics.Color.rgb(28, 36, 28)
    drawContext.canvas.nativeCanvas.drawText(glyphs.getValue(piece.lowercaseChar()), center.x, baseline, paint)
}
