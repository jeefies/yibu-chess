package cn.yibu.chess.core

import org.junit.Assert.*
import org.junit.Test

class SoundEventsTest {
    @Test fun ordinaryCapturesEnPassantCastlingAndPromotionHaveDistinctImpacts() {
        assertEquals(SoundCue.MOVE, SoundEvents.move(emptyList(), "e2e4").first().cue)
        assertEquals(SoundCue.CAPTURE, SoundEvents.move(listOf("e2e4", "d7d5"), "e4d5").first().cue)
        assertEquals(SoundCue.CAPTURE, SoundEvents.move(listOf("e2e4", "a7a6", "e4e5", "d7d5"), "e5d6").first().cue)
        val castle = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6")
        assertEquals(SoundCue.CASTLE, SoundEvents.move(castle, "e1g1").first().cue)
        val promote = listOf("a2a4", "h7h5", "a4a5", "h5h4", "a5a6", "h4h3", "a6b7", "h3g2")
        val cues = SoundEvents.move(promote, "b7a8q").map { it.cue }
        assertEquals(listOf(SoundCue.CAPTURE, SoundCue.PROMOTE), cues)
    }

    @Test fun checkAndMateAreSeparateAndTheResultIsHeardOnlyOnce() {
        assertEquals(listOf(SoundCue.MOVE, SoundCue.CHECK), SoundEvents.move(listOf("e2e4", "f7f6"), "d1h5").map { it.cue })
        val before = GameRecord(moves = listOf("f2f3", "e7e5", "g2g4"), humanWhite = true)
        val after = before.copy(moves = before.moves + "d8h4", finished = true, ending = "将杀", result = "0-1")
        val sounds = SoundEvents.transition(before, after)
        assertEquals(listOf(SoundCue.MOVE, SoundCue.CHECKMATE, SoundCue.LOSE), sounds.map { it.cue })
        assertTrue(sounds.last().delayMs >= 1000)
        assertEquals(1, sounds.count { it.cue == SoundCue.CHECKMATE })
        assertTrue(SoundEvents.transition(after, after).isEmpty())
        assertTrue(SoundEvents.transition(before.copy(id = -1), after).isEmpty())
        assertEquals(SoundCue.WIN, SoundEvents.transition(before.copy(humanWhite = false), after.copy(humanWhite = false)).last().cue)
    }

    @Test fun resigningAndClaimingDrawHaveTheirOwnSoundsWithoutFakeMate() {
        val before = GameRecord()
        val loss = before.copy(finished = true, result = "0-1", ending = "认输")
        assertEquals(listOf(SoundCue.RESIGN, SoundCue.LOSE), SoundEvents.transition(before, loss).map { it.cue })
        assertEquals(listOf(SoundCue.DRAW), SoundEvents.transition(before, before.copy(finished = true,
            result = "1/2-1/2", ending = "三次重复局面")).map { it.cue })
    }

    @Test fun reviewMovesMakeChessSoundsWithoutReplayingVictoryAndStationaryBoardsAreSilent() {
        val root = listOf("f2f3", "e7e5", "g2g4")
        assertEquals(listOf(SoundCue.MOVE, SoundCue.CHECKMATE), SoundEvents.preview(root, root + "d8h4").map { it.cue })
        assertTrue(SoundEvents.preview(root, root).isEmpty())
        assertEquals(listOf(SoundCue.NAVIGATE), SoundEvents.preview(root, root.dropLast(1)).map { it.cue })
        assertEquals(listOf(SoundCue.NAVIGATE), SoundEvents.preview(emptyList(), root).map { it.cue })
    }
}
