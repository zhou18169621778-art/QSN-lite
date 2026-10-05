package com.example.eew.testlite.data

enum class AlertLevel(val color: Int) {
    BLUE(0xFF1E88E5.toInt()),
    YELLOW(0xFFF9A825.toInt()),
    ORANGE(0xFFEF6C00.toInt()),
    RED(0xFFC62828.toInt()),
    NONE(0xFF757575.toInt());

    companion object {
        fun fromName(name: String): AlertLevel {
            return when (name) {
                "蓝色" -> BLUE
                "黄色" -> YELLOW
                "橙色" -> ORANGE
                "红色" -> RED
                else -> NONE
            }
        }
    }
}

data class WeatherAlert(
    val id: String,
    val title: String,
    val kind: String,
    val level: AlertLevel,
    val station: String,
    val issueTimeMillis: Long,
    val detailUrl: String
) {
    val levelName: String
        get() = when (level) {
            AlertLevel.BLUE -> "蓝色"
            AlertLevel.YELLOW -> "黄色"
            AlertLevel.ORANGE -> "橙色"
            AlertLevel.RED -> "红色"
            AlertLevel.NONE -> ""
        }
}
