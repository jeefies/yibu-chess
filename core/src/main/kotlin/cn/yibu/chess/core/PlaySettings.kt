package cn.yibu.chess.core

enum class ColorPreference(val chinese: String, val humanWhite: Boolean?) {
    RANDOM("随机", null), WHITE("白方", true), BLACK("黑方", false)
}

enum class AnalysisBudget(val profileName: String, val title: String, val description: String) {
    LIGHTNING("lightning", "极速", "目标深度 22 · 0.5 秒搜索预算，实际深度以结果为准"),
    DEEP("deep", "深入", "采用服务器较长搜索预算，适合复杂局面")
}

data class PlaySettings(
    val mode: Difficulty = Difficulty.MATCHED,
    val color: ColorPreference = ColorPreference.RANDOM,
    val stockfishToken: String = "",
    val analysisBudget: AnalysisBudget = AnalysisBudget.LIGHTNING
)
