package cn.yibu.chess.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Accent = Color(0xFF28594B)
internal val Background = Color(0xFFF7F6F2)
internal val Panel = Color(0xFFFFFFFF)
internal val Ink = Color(0xFF202B27)
internal val Muted = Color(0xFF65716A)
internal val Soft = Color(0xFFECEFE8)
internal val Line = Color(0xFFDDE3DC)
internal val Danger = Color(0xFFAD493D)
internal val Gold = Color(0xFF8D692D)

@Composable
internal fun ChessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent, onPrimary = Color.White, primaryContainer = Soft, onPrimaryContainer = Accent,
            secondary = Accent, secondaryContainer = Soft, onSecondaryContainer = Accent,
            background = Background, onBackground = Ink, surface = Panel, onSurface = Ink,
            surfaceVariant = Soft, onSurfaceVariant = Muted, outline = Line, outlineVariant = Line,
            surfaceTint = Accent, surfaceContainer = Panel, surfaceContainerHigh = Panel,
            surfaceContainerLow = Panel, surfaceContainerLowest = Panel, surfaceContainerHighest = Soft,
            surfaceBright = Panel, surfaceDim = Background,
            error = Danger, errorContainer = Color(0xFFFFEEE9), onErrorContainer = Danger,
        ),
        typography = Typography(
            titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
        ),
        content = content,
    )
}
