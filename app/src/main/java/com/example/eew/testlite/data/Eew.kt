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
