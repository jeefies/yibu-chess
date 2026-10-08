package cn.yibu.chess.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import cn.yibu.chess.core.SoundCue

internal val LocalSoundFeedback = staticCompositionLocalOf<(SoundCue) -> Unit> { {} }
internal val LocalReviewSound = staticCompositionLocalOf<(List<String>, List<String>) -> Unit> { { _, _ -> } }

@Composable
internal fun feedbackClick(action: () -> Unit): () -> Unit {
    val feedback = LocalSoundFeedback.current
    return { feedback(SoundCue.SELECT); action() }
}
