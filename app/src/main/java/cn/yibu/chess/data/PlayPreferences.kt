package cn.yibu.chess.data

import android.content.Context
import cn.yibu.chess.core.ColorPreference
import cn.yibu.chess.core.Difficulty
import cn.yibu.chess.core.PlaySettings

class PlayPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("play-settings", Context.MODE_PRIVATE)
    fun read(): PlaySettings {
        val mode = runCatching { Difficulty.valueOf(preferences.getString("mode", "MATCHED")!!) }.getOrNull()
            ?.takeIf { it in Difficulty.choices } ?: Difficulty.MATCHED
        val color = runCatching { ColorPreference.valueOf(preferences.getString("color", "RANDOM")!!) }.getOrDefault(ColorPreference.RANDOM)
        return PlaySettings(mode, color)
    }
    fun save(settings: PlaySettings) {
        require(settings.mode in Difficulty.choices)
        preferences.edit().putString("mode", settings.mode.name).putString("color", settings.color.name).apply()
    }
}
