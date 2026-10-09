package cn.yibu.chess.ui

import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import cn.yibu.chess.diagnostics.AnalysisTiming
import cn.yibu.chess.diagnostics.AnalysisTimings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalysisFrameTimingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyTheResultAdoptedByTheUiGetsAFrameMeasurement() {
        AnalysisTimings.clear()
        var nanos = 0L
        val first = AnalysisTiming(1, 1, "deep") { nanos }
        val second = AnalysisTiming(1, 2, "deep") { nanos }
        nanos = 10_000_000L; first.finish("success"); second.finish("success")
        val id = mutableStateOf<String?>(null)
        compose.setContent { AnalysisFrameTiming(id.value); Text("计时") }
        compose.mainClock.autoAdvance = false
        try {
            nanos = 20_000_000L
            compose.runOnIdle { id.value = first.id; id.value = second.id }
            repeat(6) {
                shadowOf(Looper.getMainLooper()).idle()
                compose.mainClock.advanceTimeByFrame()
                shadowOf(Looper.getMainLooper()).idle()
                compose.waitForIdle()
            }
            val rows = AnalysisTimings.snapshot().getJSONArray("analysis")
            assertTrue(rows.getJSONObject(0).isNull("total_to_frame_ms"))
            assertEquals(20.0, rows.getJSONObject(1).getDouble("total_to_frame_ms"), .001)
            assertEquals(10.0, rows.getJSONObject(1).getDouble("after_completion_to_frame_ms"), .001)
        } finally { compose.mainClock.autoAdvance = true }
    }
}
