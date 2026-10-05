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

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object EewRepository {

    private const val JMA_EEW_URL = "https://api.wolfx.jp/jma_eew.json"
    private const val CENC_EEW_URL = "https://api.wolfx.jp/cenc_eew.json"
    private const val SC_EEW_URL = "https://api.wolfx.jp/sc_eew.json"
    private const val FJ_EEW_URL = "https://api.wolfx.jp/fj_eew.json"
    private const val CQ_EEW_URL = "https://api.wolfx.jp/cq_eew.json"

    suspend fun fetchLatestWarnings(): List<Eew> {
        return buildList {
            runCatching { addAll(fetchJma()) }
            runCatching { addAll(fetchCenc()) }
            runCatching { addAll(fetchSc()) }
            runCatching { addAll(fetchFj()) }
            runCatching { addAll(fetchCq()) }
        }.sortedByDescending { it.reportTimeMillis }
    }

    private fun optMagnitude(root: JSONObject): Double {
        return root.optDouble("Magnitude", 0.0)
            .takeIf { it > 0.0 } ?: root.optDouble("Magunitude", 0.0)
    }

    fun parseJmaJson(jsonString: String): Eew? {
        val root = JSONObject(jsonString)
        val eventId = root.optString("EventID")
        val magnitude = optMagnitude(root)
        if (eventId.isBlank() || root.optBoolean("isTraining", false)) return null
        return Eew(
            id = eventId,
            source = DataSource.WOLFX_JMA,
            reportTimeMillis = parseJst(root.optString("AnnouncedTime")),
            originTimeMillis = parseJst(root.optString("OriginTime")),
            hypocenter = root.optString("Hypocenter"),
            latitude = root.optDouble("Latitude", 0.0),
            longitude = root.optDouble("Longitude", 0.0),
            magnitude = magnitude,
            depthKm = root.optDouble("Depth", 0.0),
            maxIntensity = root.optString("MaxIntensity"),
            isWarn = root.optBoolean("isWarn", false),
            isFinal = root.optBoolean("isFinal", false),
            isCancel = root.optBoolean("isCancel", false),
            reportNum = root.optInt("Serial", 0),
            method = parseJmaMethod(root)
        )
    }

    private fun parseJmaMethod(root: JSONObject): String {
        if (root.optBoolean("isAssumption", false)) return "PLUM法（推定震源）"
        val magnitude = optMagnitude(root)
        val depth = root.optDouble("Depth", 0.0)
        val intensity = root.optString("MaxIntensity", "")
        val hasIntensity = intensity.isNotBlank() && intensity != "0" && intensity != "null"
        return when {
            // 深层: 无最大震度, 有震级, 深度大于150km
            !hasIntensity && magnitude > 0.0 && depth > 150.0 -> "深层"
            // IPF单点: 无最大震度, 有震级, 深度固定10km
            !hasIntensity && magnitude > 0.0 && kotlin.math.abs(depth - 10.0) <= 3.0 -> "IPF单点"
            // Level法: 只有最大震度, 无震级无深度
            hasIntensity && magnitude <= 0.0 && depth <= 0.0 -> "Level法"
            else -> ""
        }
    }

    fun parseCencJson(jsonString: String): Eew? {
        val root = JSONObject(jsonString)
        val eventId = root.optString("EventID")
        if (eventId.isBlank()) return null
        val intensity = root.optString("MaxIntensity", "")
        return Eew(
            id = eventId,
            source = DataSource.CEA,
            reportTimeMillis = parseCst(root.optString("ReportTime")),
            originTimeMillis = parseCst(root.optString("OriginTime")),
            hypocenter = root.optString("HypoCenter"),
            latitude = root.optDouble("Latitude", 0.0),
            longitude = root.optDouble("Longitude", 0.0),
            magnitude = root.optDouble("Magnitude", 0.0),
            depthKm = root.optDouble("Depth", 0.0),
            maxIntensity = intensity,
            isWarn = intensity.isNotBlank() && intensity != "0" && intensity != "null",
            isFinal = root.optBoolean("isFinal", false),
            isCancel = false,
            reportNum = root.optInt("ReportNum", 0)
        )
    }

    fun parseScEewJson(jsonString: String): Eew? {
        return parsePrEewJson(jsonString, "sc")
    }

    fun parseFjEewJson(jsonString: String): Eew? {
        return parsePrEewJson(jsonString, "fj")
    }

    fun parseCqEewJson(jsonString: String): Eew? {
        return parsePrEewJson(jsonString, "cq")
    }

    private fun parsePrEewJson(jsonString: String, prefix: String): Eew? {
        val root = JSONObject(jsonString)
        val eventId = root.optString("EventID")
        val id = root.optLong("ID", 0L)
        if (eventId.isBlank() && id == 0L) return null
        val magnitude = optMagnitude(root)
        val intensity = root.optString("MaxIntensity", "")
        return Eew(
            id = "cea_pr_${prefix}_" + eventId.ifBlank { "$id" },
            source = DataSource.CEA_PR,
            reportTimeMillis = parseCst(root.optString("ReportTime")),
            originTimeMillis = parseCst(root.optString("OriginTime")),
            hypocenter = root.optString("HypoCenter"),
            latitude = root.optDouble("Latitude", 0.0),
            longitude = root.optDouble("Longitude", 0.0),
            magnitude = magnitude,
            depthKm = root.optDouble("Depth", 0.0),
            maxIntensity = intensity,
            isWarn = intensity.isNotBlank() && intensity != "0" && intensity != "null",
            isFinal = root.optBoolean("isFinal", false),
            isCancel = root.optBoolean("isCancel", false),
            reportNum = root.optInt("ReportNum", 0)
        )
    }

    private suspend fun fetchJma(): List<Eew> {
        return parseJmaJson(request(JMA_EEW_URL))?.let { listOf(it) } ?: emptyList()
    }

    private suspend fun fetchCenc(): List<Eew> {
        return parseCencJson(request(CENC_EEW_URL))?.let { listOf(it) } ?: emptyList()
    }

    private suspend fun fetchSc(): List<Eew> {
        return parseScEewJson(request(SC_EEW_URL))?.let { listOf(it) } ?: emptyList()
    }

    private suspend fun fetchFj(): List<Eew> {
        return parseFjEewJson(request(FJ_EEW_URL))?.let { listOf(it) } ?: emptyList()
    }

    private suspend fun fetchCq(): List<Eew> {
        return parseCqEewJson(request(CQ_EEW_URL))?.let { listOf(it) } ?: emptyList()
    }

    private fun request(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "GET"
            val code = connection.responseCode
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCst(value: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            format.parse(value)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseJst(value: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
            format.parse(value)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
