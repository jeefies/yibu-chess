package cn.yibu.chess.core

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LessonPlaybackTest {
    @Test fun aLongEngineLineKeepsSixteenLegalPliesWithAnnotationsAndNextReplyContext() {
        val line = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6", "e1g1", "f8e7",
            "f1e1", "b7b5", "a4b3", "d7d6", "c2c3", "e8g8", "h2h3", "c6b8")
        val eval = Evaluation(22, cp = 30, pv = line)
        val review = MoveReview(1, "e2e4", "e4", eval, eval, grade = Grade.BEST, explanation = "")
        val lesson = MoveCoach.explain(emptyList(), review)
        assertEquals(line.take(16), lesson.variation)
        assertEquals(16, lesson.steps.size)
        assertTrue(lesson.steps[10].explanation.contains("下一着参考：b5"))
        assertTrue(lesson.steps.last().title.contains("黑方 O-O"))
        assertTrue(lesson.steps.last().explanation.contains("不代表计划已经完成"))
        assertFalse(lesson.plan.contains("白方 h3"))
    }

    @Test fun everyShownMoveHasAnExplanationIncludingMovesBeyondTheSixth() {
        val line = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6", "e1g1", "f8e7")
        val eval = Evaluation(22, cp = 30, pv = line)
        val review = MoveReview(1, "d2d4", "d4", eval, eval, grade = Grade.GOOD, explanation = "")
        val lesson = MoveCoach.explain(emptyList(), review)
        assertEquals(line, lesson.steps.map { it.uci })
        lesson.steps.forEachIndexed { index, step ->
            assertTrue(step.title.contains(ChessRules.san(line.take(index), step.uci)))
            assertTrue(step.explanation.isNotBlank())
            assertTrue(lesson.plan.contains("${index + 1}. ${step.title}"))
        }
        assertTrue(lesson.steps[8].explanation.contains("易位"))
        assertEquals(lesson, Json.decodeFromString<MoveLesson>(Json.encodeToString(MoveLesson.serializer(), lesson)))
    }

    @Test fun savedLegacyLessonsKeepTheirTextAndGetLocalAnnotations() {
        val lesson = Json.decodeFromString<MoveLesson>("""{"ply":1,"recommendedMove":"e2e4","why":"旧原因","plan":"旧思路","variation":["e2e4","e7e5"],"depth":22}""")
        assertEquals("旧原因", lesson.why)
        assertEquals("旧思路", lesson.plan)
        assertTrue(lesson.steps.isEmpty())
        val steps = MoveCoach.annotatedSteps(emptyList(), lesson.variation)
        assertEquals(lesson.variation, steps.map { it.uci })
        assertTrue(steps[1].title.contains("对手关键应对：黑方 e5"))
    }

    @Test fun noAnnotationContinuesBeyondCheckmateOrAnIllegalMove() {
        val history = listOf("f2f3", "e7e5", "g2g4")
        val steps = MoveCoach.annotatedSteps(history, listOf("d8h4", "e2e3"))
        assertEquals(listOf("d8h4"), steps.map { it.uci })
        assertTrue(steps.single().explanation.contains("直接将杀"))
    }
}
