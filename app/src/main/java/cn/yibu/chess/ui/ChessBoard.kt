package cn.yibu.chess.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.yibu.chess.core.ChessRules
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
    onSquare: (Int) -> Unit,
) {
    val pieces = remember(fen) { ChessRules.fenPieces(fen) }
    val highlighted = remember(lastMove) {
        lastMove?.takeIf { it.length >= 4 }?.let { setOf(ChessRules.squareIndex(it.take(2)), ChessRules.squareIndex(it.substring(2, 4))) }.orEmpty()
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL); textAlign = Paint.Align.CENTER } }
    val glyphs = mapOf('k' to "♚", 'q' to "♛", 'r' to "♜", 'b' to "♝", 'n' to "♞", 'p' to "♟")
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
        .semantics { contentDescription = "国际象棋棋盘，${if (flipped) "黑方" else "白方"}视角" }
        .pointerInput(flipped, fen) {
            detectTapGestures { position ->
                val col = (position.x / (size.width / 8f)).toInt().coerceIn(0, 7)
                val row = (position.y / (size.height / 8f)).toInt().coerceIn(0, 7)
                onSquare(if (flipped) row * 8 + 7 - col else (7 - row) * 8 + col)
            }
        }) {
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
            drawRect(if (light) Color(0xFFE4E2CD) else Color(0xFF738B69), topLeft, Size(cell, cell))
            if (index in highlighted) drawRect(Color(0xFFDBCF62).copy(alpha = .5f), topLeft, Size(cell, cell))
            if (index == selected) drawRect(Color(0xFFEAC74B).copy(alpha = .70f), topLeft, Size(cell, cell))
            val piece = pieces[index]
            val c = center(index)
            if (piece != ' ') {
                paint.textSize = cell * .84f
                paint.color = if (piece.isUpperCase()) android.graphics.Color.rgb(252, 250, 236) else android.graphics.Color.rgb(28, 36, 28)
                paint.style = Paint.Style.FILL
                val baseline = c.y - (paint.ascent() + paint.descent()) / 2 - cell * .02f
                if (piece.isUpperCase()) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = cell * .035f
                    paint.color = android.graphics.Color.rgb(39, 49, 36)
                    drawContext.canvas.nativeCanvas.drawText(glyphs.getValue(piece.lowercaseChar()), c.x, baseline, paint)
                    paint.style = Paint.Style.FILL
                    paint.color = android.graphics.Color.rgb(252, 250, 236)
                }
                drawContext.canvas.nativeCanvas.drawText(glyphs.getValue(piece.lowercaseChar()), c.x, baseline, paint)
            }
            if (index in targets) {
                if (piece == ' ') drawCircle(Color(0xFF263D28).copy(alpha = .38f), cell * .12f, c)
                else drawCircle(Color(0xFFE8C858), cell * .43f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(cell * .07f))
            }
            paint.style = Paint.Style.FILL
            paint.textSize = cell * .19f
            paint.color = if (light) android.graphics.Color.rgb(78, 100, 69) else android.graphics.Color.rgb(234, 233, 214)
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
}
