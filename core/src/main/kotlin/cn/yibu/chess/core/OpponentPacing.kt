package cn.yibu.chess.core

import kotlin.random.Random

/** Search time counts towards the pause; a slow search incurs no extra wait. */
object OpponentPacing {
    fun targetMs(random: Random = Random.Default): Long = random.nextLong(1_000, 2_001)
    fun remainingMs(targetMs: Long, elapsedMs: Long): Long = (targetMs - elapsedMs.coerceAtLeast(0)).coerceAtLeast(0)
}
