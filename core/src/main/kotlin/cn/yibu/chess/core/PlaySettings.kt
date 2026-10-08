package cn.yibu.chess.core

enum class ColorPreference(val chinese: String, val humanWhite: Boolean?) {
    RANDOM("随机", null), WHITE("白方", true), BLACK("黑方", false)
}

enum class AnalysisBudget(val profileName: String, val title: String, val description: String) {
    LIGHTNING("lightning", "lightning (极速)", "0.5s 左右 · 22 层"),
    DEEP("deep", "deep (深度)", "固定 4s 搜索预算")
}

data class PlaySettings(
    val mode: Difficulty = Difficulty.MATCHED,
    val color: ColorPreference = ColorPreference.RANDOM,
    val stockfishToken: String = "",
    val analysisBudget: AnalysisBudget = AnalysisBudget.LIGHTNING
)
