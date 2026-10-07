package cn.yibu.chess.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cn.yibu.chess.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayPreferencesTest {
    @Test fun randomMatchedDefaultAndExplicitSettingsSurviveReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PlayPreferences(context)
        assertEquals(PlaySettings(), preferences.read())
        val chosen = PlaySettings(Difficulty.MATCHED, ColorPreference.BLACK)
        preferences.save(chosen)
        assertEquals(chosen, PlayPreferences(context).read())
        preferences.save(PlaySettings(Difficulty.STRONG, ColorPreference.WHITE))
        assertEquals(Difficulty.STRONG, PlayPreferences(context).read().mode)
    }
}
