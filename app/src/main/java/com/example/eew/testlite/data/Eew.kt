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

import java.io.Serializable

data class Eew(
    val id: String,
    val source: DataSource,
    val reportTimeMillis: Long,
    val originTimeMillis: Long,
    val hypocenter: String,
    val latitude: Double,
    val longitude: Double,
    val magnitude: Double,
    val depthKm: Double,
    val maxIntensity: String,
    val isWarn: Boolean,
    val isFinal: Boolean,
    val isCancel: Boolean,
    val reportNum: Int = 0,
    val method: String = ""
) : Serializable {

    fun intensityName(): String {
        return if (source == DataSource.WOLFX_JMA) "震度" else "烈度"
    }

    fun hasIntensity(): Boolean {
        return maxIntensity.isNotBlank() && maxIntensity != "0" && maxIntensity != "null"
    }

    fun displayMagnitude(): Double {
        return if (magnitude > 0.0) magnitude else 1.0
    }

    fun sourceShortLabelRes(): Int {
        return when (source) {
            DataSource.WOLFX_JMA -> com.example.eew.testlite.R.string.source_short_wolfx_jma
            DataSource.CEA -> com.example.eew.testlite.R.string.source_short_cea
            DataSource.CEA_PR -> com.example.eew.testlite.R.string.source_short_cea_pr
            else -> com.example.eew.testlite.R.string.source_short_wolfx
        }
    }
}
