/*
 * QSN-lite - 轻量级 Android 地震监测与地震预警应用
 * Copyright (C) 2026 zhou18169621778-art
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
