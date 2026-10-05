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
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object WeatherPrefs {
    const val PREFS = "weather_prefs"
    const val KEY_PROVINCE = "province"
    const val KEY_CITY = "city"
}

object WeatherAlertRepository {

    private const val API_URL = "https://www.nmc.cn/rest/findAlarm?pageNo=%d&pageSize=%d"
    private const val NMC_BASE = "https://www.nmc.cn"
    private const val PAGE_SIZE = 20

    data class PageResult(
        val alerts: List<WeatherAlert>,
        val total: Int,
        val hasMore: Boolean
    )

    suspend fun fetchAlerts(pageNo: Int, province: String = "", city: String = ""): PageResult {
        val urlString = buildString {
            append(String.format(Locale.US, API_URL, pageNo, PAGE_SIZE))
            append("&signaltype=&signallevel=")
            if (province.isNotBlank()) {
                append("&province=").append(URLEncoder.encode(province, "UTF-8"))
            }
        }
        val jsonString = request(urlString)
        val root = JSONObject(jsonString)
        val data = root.optJSONObject("data") ?: throw RuntimeException("bad response")
        val page = data.optJSONObject("page") ?: throw RuntimeException("bad response")
        val total = page.optInt("count", 0)
        val next = page.optInt("next", pageNo)
        val list = page.optJSONArray("list") ?: org.json.JSONArray()
        val all = buildList {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val title = item.optString("title")
                if (title.isBlank()) continue
                val parsed = parseTitle(title)
                add(
                    WeatherAlert(
                        id = item.optString("alertid"),
                        title = title,
                        kind = parsed.first,
                        level = parsed.second,
                        station = parseStation(title),
                        issueTimeMillis = parseIssueTime(item.optString("issuetime")),
                        detailUrl = NMC_BASE + item.optString("url")
                    )
                )
            }
        }
        val filtered = if (city.isNotBlank() && city != "全市") {
            all.filter { it.title.contains(city) }
        } else {
            all
        }
        return PageResult(
            alerts = filtered,
            total = total,
            hasMore = next > pageNo
        )
    }

    private fun parseTitle(title: String): Pair<String, AlertLevel> {
        val m = Regex("发布(.+?)(蓝色|黄色|橙色|红色)预警").find(title)
        return if (m != null) {
            m.groupValues[1] to AlertLevel.fromName(m.groupValues[2])
        } else {
            "气象" to AlertLevel.NONE
        }
    }

    private fun parseStation(title: String): String {
        return Regex("(.+?气象台)").find(title)?.groupValues?.get(1) ?: title.substringBefore("发布").ifBlank { "气象台" }
    }

    private fun parseIssueTime(value: String): Long {
        if (value.isBlank()) return 0L
        return try {
            val format = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
            format.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            format.parse(value)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun request(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (EarthquakeMonitor)"
            )
            val code = connection.responseCode
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
