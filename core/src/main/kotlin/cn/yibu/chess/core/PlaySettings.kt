package cn.yibu.chess.core

enum class ColorPreference(val chinese: String, val humanWhite: Boolean?) {
    RANDOM("随机", null), WHITE("白方", true), BLACK("黑方", false)
}

data class PlaySettings(
    val mode: Difficulty = Difficulty.MATCHED,
    val color: ColorPreference = ColorPreference.RANDOM,
    val stockfishToken: String = ""
)
