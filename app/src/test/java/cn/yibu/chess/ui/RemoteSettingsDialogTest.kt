package cn.yibu.chess.ui

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import cn.yibu.chess.core.PlaySettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
class RemoteSettingsDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun tokenInputAndConnectionTestUseTheCurrentSettings() {
        var settings by mutableStateOf(PlaySettings())
        var tested: String? = null
        compose.setContent {
            ChessTheme {
                Box(Modifier.fillMaxSize()) {
                NewGameSettingsEditor(settings, 500, onChange = { settings = it }, onTestToken = { token ->
                    tested = token; Result.success("Stockfish 19")
                })
                }
            }
        }
        compose.mainClock.advanceTimeBy(200)
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("sample-token")
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("测试连接").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        assertEquals("sample-token", tested)
        compose.onNodeWithText("连接成功：Stockfish 19").assertExists()
        assertEquals("sample-token", settings.stockfishToken)
    }
}
