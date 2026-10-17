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

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class DataSource(val labelRes: Int) {
    USGS(com.example.eew.testlite.R.string.source_usgs),
    WOLFX_CENC(com.example.eew.testlite.R.string.source_wolfx),
    WOLFX_JMA(com.example.eew.testlite.R.string.source_wolfx_jma),
    EMSC(com.example.eew.testlite.R.string.source_emsc),
    EMSC_MT(com.example.eew.testlite.R.string.source_emsc_mt),
    HKO(com.example.eew.testlite.R.string.source_hko),
    BMKG(com.example.eew.testlite.R.string.source_bmkg),
    JMA_XML(com.example.eew.testlite.R.string.source_jma_xml),
    JRISQ(com.example.eew.testlite.R.string.source_jrisq),
    CWA(com.example.eew.testlite.R.string.source_cwa),
    CEA(com.example.eew.testlite.R.string.source_cea),
    CEA_PR(com.example.eew.testlite.R.string.source_cea_pr),
}

object EarthquakeRepository {

    @Volatile
    var lastStatuses: List<SourceStatus> = emptyList()
        private set

    private const val USGS_URL =
        "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson" +
            "&orderby=time&limit=50&minmagnitude=2.5"

    private const val WOLFX_CENC_URL = "https://api.wolfx.jp/cenc_eqlist.json"
    private const val WOLFX_JMA_URL = "https://api.wolfx.jp/jma_eqlist.json"
    private const val EMSC_URL = "https://www.seismicportal.eu/fdsnws/event/1/query?limit=50&format=json"
    private const val HKO_URL =
        "https://data.weather.gov.hk/weatherAPI/opendata/earthquake.php?dataType=qem&lang=sc"
    private const val BMKG_URL = "https://data.bmkg.go.id/DataMKG/TEWS/gempaterkini.json"
    private const val JMA_XML_FEED_URL = "https://www.data.jma.go.jp/developer/xml/feed/eqvol.xml"
    private const val EMSC_MTWS_URL =
        "https://seismicportal.eu/mtws/api/search?&format=json&downloadAsFile=false" +
            "&orderby=time-desc&offset=0&limit=20"
    private const val JRISQ_URL = "https://www.j-risq.bosai.go.jp/report/sp/feed/rss2.xml"

    suspend fun fetchRecentEarthquakes(context: android.content.Context): List<Earthquake> {
        val statuses = mutableListOf<SourceStatus>()
        suspend fun <T> track(
            label: String,
            block: suspend () -> List<T>
        ): List<T> {
            val start = System.currentTimeMillis()
            return try {
                val result = block()
                statuses.add(SourceStatus(label, true, System.currentTimeMillis() - start))
                result
            } catch (e: Exception) {
                try {
                    val result = block()
                    statuses.add(SourceStatus(label, true, System.currentTimeMillis() - start))
                    result
                } catch (e2: Exception) {
                    statuses.add(SourceStatus(label, false, System.currentTimeMillis() - start))
                    emptyList()
                }
            }
        }
        val results = coroutineScope {
            val deferred = mutableListOf<kotlinx.coroutines.Deferred<List<Earthquake>>>()
            deferred.add(async { track("美国地质调查局") { fetchUsgs() } })
            deferred.add(async { track("Wolfx 中国地震台网") { fetchWolfxCenc() } })
            deferred.add(async { track("Wolfx 日本气象厅") { fetchWolfxJma() } })
            deferred.add(async { track("EMSC 欧洲地中海地震中心") { fetchEmsc() } })
            deferred.add(async { track("EMSC 震源机制解") { fetchEmscMt() } })
            deferred.add(async { track("香港天文台") { fetchHko() } })
            deferred.add(async { track("BMKG 印度尼西亚气象局") { fetchBmkg() } })
            deferred.add(async { track("JMA 日本气象厅") { fetchJmaXml() } })
            deferred.add(async { track("J-RISQ 地震速報") { fetchJrisq() } })
            deferred.mapNotNull { it.runCatching { await() }.getOrNull() }.flatten()
        }
        lastStatuses = statuses.sortedBy { it.label }
        MomentTensorStore.save(context)
        val seen = LinkedHashSet<String>()
        return results
            .filter { seen.add(it.id) }
            .sortedByDescending { it.timeMillis }
    }

    private suspend fun fetchUsgs(): List<Earthquake> {
        val jsonString = request(USGS_URL)
        val root = JSONObject(jsonString)
        val features = root.getJSONArray("features")
        return buildList {
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                val geometry = feature.getJSONObject("geometry")
                val coords = geometry.getJSONArray("coordinates")
                add(
                    Earthquake(
                        id = feature.optString("id"),
                        magnitude = props.optDouble("mag", 0.0),
                        place = props.optString("place"),
                        timeMillis = props.optLong("time", 0L),
                        latitude = coords.optDouble(1, 0.0),
                        longitude = coords.optDouble(0, 0.0),
                        depthKm = coords.optDouble(2, 0.0),
                        url = props.optString("url"),
                        source = DataSource.USGS,
                        magnitudeType = props.optString("magType"),
                        status = props.optString("status"),
                        reportTimeMillis = props.optLong("updated", 0L)
                    )
                )
            }
        }
    }

    private suspend fun fetchWolfxCenc(): List<Earthquake> {
        val jsonString = request(WOLFX_CENC_URL)
        val root = JSONObject(jsonString)
        return buildList {
            val keys = root.keys().asSequence()
                .filter { it.startsWith("No") }
                .sortedBy { it.substring(2).toIntOrNull() ?: Int.MAX_VALUE }
            for (key in keys) {
                val j = root.getJSONObject(key)
                val magnitude = parseDouble(j.optString("magnitude"))
                if (magnitude < 2.0) continue
                add(
                    Earthquake(
                        id = j.optString("EventID"),
                        magnitude = magnitude,
                        place = j.optString("location"),
                        timeMillis = parseCst(j.optString("time")),
                        latitude = parseDouble(j.optString("latitude")),
                        longitude = parseDouble(j.optString("longitude")),
                        depthKm = parseDouble(j.optString("depth")),
                        url = "https://www.ceic.ac.cn/",
                        source = DataSource.WOLFX_CENC,
                        status = j.optString("type"),
                        reportTimeMillis = parseCst(j.optString("ReportTime"))
                    )
                )
            }
        }
    }

    private suspend fun fetchWolfxJma(): List<Earthquake> {
        val jsonString = request(WOLFX_JMA_URL)
        val root = JSONObject(jsonString)
        return buildList {
            val keys = root.keys().asSequence()
                .filter { it.startsWith("No") }
                .sortedBy { it.substring(2).toIntOrNull() ?: Int.MAX_VALUE }
            for (key in keys) {
                val j = root.getJSONObject(key)
                val magnitude = parseDouble(j.optString("magnitude"))
                if (magnitude < 2.0) continue
                add(
                    Earthquake(
                        id = j.optString("EventID"),
                        magnitude = magnitude,
                        place = j.optString("location"),
                        timeMillis = parseJst(j.optString("time_full")),
                        latitude = parseDouble(j.optString("latitude")),
                        longitude = parseDouble(j.optString("longitude")),
                        depthKm = parseDepth(j.optString("depth")),
                        url = "https://www.jma.go.jp/bosai/quake/",
                        source = DataSource.WOLFX_JMA,
                        status = j.optString("type")
                    )
                )
            }
        }
    }

    private suspend fun fetchEmsc(): List<Earthquake> {
        val jsonString = request(EMSC_URL)
        val root = JSONObject(jsonString)
        val features = root.getJSONArray("features")
        return buildList {
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                val magnitude = props.optDouble("mag", 0.0)
                if (magnitude < 2.0) continue
                add(
                    Earthquake(
                        id = "emsc_" + props.optString("unid"),
                        magnitude = magnitude,
                        place = props.optString("flynn_region"),
                        timeMillis = parseIso(props.optString("time")),
                        latitude = props.optDouble("lat", 0.0),
                        longitude = props.optDouble("lon", 0.0),
                        depthKm = props.optDouble("depth", 0.0),
                        url = "https://www.emsc-csem.org/Earthquake/information/earthquake.php?id=${props.optString("source_id")}",
                        source = DataSource.EMSC,
                        magnitudeType = props.optString("magtype"),
                        agency = props.optString("auth")
                    )
                )
            }
        }
    }

    /**
     * EMSC MTWS 震源机制解: 每个地震取 preferred 解 (mt_preferred),
     * 按 ev_unid 去重, 最多返回 12 条。机制解明细存入 MomentTensorStore,
     * 列表/详情凭 Earthquake.id (emscmt_<mt_id>) 查表。
     */
    private suspend fun fetchEmscMt(): List<Earthquake> {
        val jsonString = request(EMSC_MTWS_URL)
        val arr = JSONArray(jsonString)
        val preferred = LinkedHashMap<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("ev_unid").ifBlank { o.optString("ev_id") }
            if (key.isBlank()) continue
            val prev = preferred[key]
            if (prev == null || (!prev.optBoolean("mt_preferred") && o.optBoolean("mt_preferred"))) {
                preferred[key] = o
            }
        }
        val mts = LinkedHashMap<String, MomentTensor>()
        val quakes = buildList {
            for (o in preferred.values.take(12)) {
                val mtId = o.optLong("mt_id", 0L)
                if (mtId == 0L) continue
                val quakeId = "emscmt_$mtId"
                val mw = o.optDouble("mt_mw", 0.0).takeIf { it > 0 }
                    ?: o.optDouble("ev_mag_value", 0.0)
                if (mw < 2.0) continue
                val timeMillis = parseMtwsTime(o.optString("ev_event_time"))
                if (timeMillis <= 0L) continue
                val mt = MomentTensor(
                    quakeId = quakeId,
                    mtId = mtId,
                    eventUnid = o.optString("ev_unid"),
                    strike1 = o.optDouble("mt_strike_1"),
                    dip1 = o.optDouble("mt_dip_1"),
                    rake1 = o.optDouble("mt_rake_1"),
                    strike2 = o.optDouble("mt_strike_2"),
                    dip2 = o.optDouble("mt_dip_2"),
                    rake2 = o.optDouble("mt_rake_2"),
                    mw = mw,
                    depthKm = o.optDouble("mt_depth", 0.0),
                    centroidLat = o.optDouble("mt_latitude", o.optDouble("ev_latitude")),
                    centroidLon = o.optDouble("mt_longitude", o.optDouble("ev_longitude")),
                    centroidTimeMillis = parseMtwsTime(o.optString("mt_centroid_time")),
                    region = o.optString("mt_region").ifBlank { o.optString("ev_region") },
                    catalog = o.optString("mt_source_catalog"),
                    dcPercent = o.optDouble("mt_per_dc", 0.0),
                    clvdPercent = o.optDouble("mt_per_clvd", 0.0),
                    isoPercent = o.optDouble("mt_per_iso", 0.0),
                    pAz = o.optDouble("mt_paz"),
                    pPlunge = o.optDouble("mt_pplung"),
                    tAz = o.optDouble("mt_taz"),
                    tPlunge = o.optDouble("mt_tplung"),
                    nAz = o.optDouble("mt_naz"),
                    nPlunge = o.optDouble("mt_nplung")
                )
                mts[quakeId] = mt
                add(
                    Earthquake(
                        id = quakeId,
                        magnitude = mw,
                        place = mt.region.ifBlank { "震源机制解" },
                        timeMillis = timeMillis,
                        latitude = mt.centroidLat,
                        longitude = mt.centroidLon,
                        depthKm = mt.depthKm,
                        url = "",
                        source = DataSource.EMSC_MT,
                        magnitudeType = "mw",
                        agency = mt.catalog,
                        reportTimeMillis = mt.centroidTimeMillis
                    )
                )
            }
        }
        MomentTensorStore.putAll(mts)
        return quakes
    }

    /** MTWS 时间 "2026-10-02 16:34:39.96 UTC", 小数秒位数不定, 按 UTC 解析 */
    private fun parseMtwsTime(value: String): Long {
        val m = Regex("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})(?:\\.(\\d+))? UTC").find(value.trim())
            ?: return 0L
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val base = format.parse(m.groupValues[1])?.time ?: return 0L
            val frac = m.groupValues[2]
            if (frac.isEmpty()) return base
            base + (frac + "000").substring(0, 3).toInt()
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun fetchHko(): List<Earthquake> {
        val jsonString = request(HKO_URL)
        val root = JSONObject(jsonString)
        val magnitude = root.optDouble("mag", 0.0)
        if (magnitude < 2.0) return emptyList()
        return listOf(
            Earthquake(
                id = "hko_" + root.optString("ptime") + "_" + root.optString("lat") + "_" + root.optString("lon"),
                magnitude = magnitude,
                place = root.optString("region"),
                timeMillis = parseHko(root.optString("ptime")),
                latitude = root.optDouble("lat", 0.0),
                longitude = root.optDouble("lon", 0.0),
                depthKm = 0.0,
                url = "https://www.hko.gov.hk/sc/gts/equake/equake.htm",
                source = DataSource.HKO
            )
        )
    }

    private suspend fun fetchBmkg(): List<Earthquake> {
        val jsonString = request(BMKG_URL, listOf("Referer: https://data.bmkg.go.id/gempaterkini.html"))
        val root = JSONObject(jsonString)
        val infogempa = root.optJSONObject("Infogempa") ?: return emptyList()
        val gems = infogempa.optJSONArray("gempa") ?: return emptyList()
        return buildList {
            for (i in 0 until gems.length()) {
                val j = gems.getJSONObject(i)
                val magnitude = parseDouble(j.optString("Magnitude"))
                if (magnitude < 2.0) continue
                val coords = j.optString("Coordinates").split(",")
                val lat = coords.getOrNull(0)?.trim()?.toDoubleOrNull() ?: 0.0
                val lon = coords.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0
                val dateTime = j.optString("DateTime")
                add(
                    Earthquake(
                        id = "bmkg_" + dateTime,
                        magnitude = magnitude,
                        place = j.optString("Wilayah"),
                        timeMillis = parseBmkg(dateTime),
                        latitude = lat,
                        longitude = lon,
                        depthKm = parseDepth(j.optString("Kedalaman")),
                        url = "https://www.bmkg.go.id/gempabumi/",
                        source = DataSource.BMKG
                    )
                )
            }
        }
    }

    private suspend fun fetchJmaXml(): List<Earthquake> {
        val feedXml = request(JMA_XML_FEED_URL)
        val feedDoc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }.newDocumentBuilder().parse(feedXml.byteInputStream())
        val entries = feedDoc.getElementsByTagName("entry")
        val seenEvents = LinkedHashSet<String>()
        return buildList {
            for (i in 0 until entries.length) {
                if (size >= 3) break
                val entry = entries.item(i) as Element
                val title = firstChildText(entry, "title") ?: continue
                if (!title.contains("震源")) continue
                val linkNode = entry.getElementsByTagName("link")
                val href = if (linkNode.length > 0) {
                    (linkNode.item(0) as Element).getAttribute("href")
                } else {
                    ""
                }
                if (href.isBlank()) continue
                val detail = runCatching { parseJmaDetailXml(request(href)) }.getOrNull()
                if (detail == null) continue
                if (!seenEvents.add(detail.id)) continue
                val reportTime = parseJmaDetailTime(firstChildText(entry, "updated") ?: "")
                add(if (reportTime > 0) detail.copy(reportTimeMillis = reportTime) else detail)
            }
        }
    }

    private suspend fun fetchJrisq(): List<Earthquake> {
        // J-RISQ 地震速報 RSS: content:encoded 里有 M/震源地/最大計測震度 与
        // 推定震度分布图片 <img src="...">
        val xml = request(JRISQ_URL)
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }.newDocumentBuilder().parse(xml.byteInputStream())
        val items = doc.getElementsByTagName("item")
        return buildList {
            for (i in 0 until items.length) {
                val item = items.item(i) as Element
                val content = firstChildText(item, "content:encoded") ?: continue
                val magnitude = regexFirst(content, "M\\s*([\\d.]+)")?.toDoubleOrNull()
                    ?: continue
                if (magnitude < 2.0) continue
                val place = regexFirst(content, "震源地:([^\\s<]+)")?.trim() ?: ""
                val intensity = regexFirst(content, "最大計測震度([\\d.]+)") ?: ""
                // 発生时刻 "2026/08/25 18:07頃発生"
                val originMatch = Regex("(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2})頃発生").find(content)
                val timeMillis = originMatch?.let {
                    parseJstShort(it.groupValues[1])
                } ?: 0L
                if (timeMillis <= 0L) continue
                val imageUrl = Regex("<img\\s+src=\"([^\"]+)\"").find(content)
                    ?.groupValues?.get(1) ?: ""
                val link = firstChildText(item, "link") ?: ""
                val triggerId = regexFirst(link, "(R-\\d+)") ?: "$timeMillis"
                val reportTime = parseJmaDetailTime(
                    firstChildText(item, "dcterms:modified") ?: ""
                )
                // 报次: title 形如 "[J-RISQ] 2026/08/25 18:18:47 発表(Ver.8 最終報)"
                val title = firstChildText(item, "title") ?: ""
                val ver = regexFirst(title, "Ver\\.?(\\d+)")
                val isFinal = title.contains("最終報")
                val verLabel = buildString {
                    if (!ver.isNullOrBlank()) append("Ver.").append(ver)
                    if (isFinal) append(" 最終報")
                }
                add(
                    Earthquake(
                        id = "jrisq_$triggerId",
                        magnitude = magnitude,
                        place = place.ifBlank { "日本附近" },
                        timeMillis = timeMillis,
                        latitude = 0.0,
                        longitude = 0.0,
                        depthKm = 0.0,
                        url = link,
                        source = DataSource.JRISQ,
                        maxIntensity = intensity,
                        reportTimeMillis = reportTime,
                        mapImageUrl = imageUrl,
                        status = verLabel
                    )
                )
            }
        }
    }

    /** JST 短时间格式 "2026/08/25 18:07" */
    private fun parseJstShort(value: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
            format.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
            format.parse(value)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseJmaDetailXml(xml: String): Earthquake? {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }.newDocumentBuilder().parse(xml.byteInputStream())
        val root = doc.documentElement
        val coordinate = descendantText(root, "Coordinate") ?: return null
        val magnitudeNode = descendantElement(root, "Magnitude") ?: return null
        val magnitude = magnitudeNode.textContent.trim().toDoubleOrNull() ?: return null
        if (magnitude < 2.0) return null
        val originTime = descendantText(root, "OriginTime") ?: ""
        val name = descendantText(root, "Name") ?: ""
        val eventId = descendantText(root, "EventID") ?: ""
        val (lat, lon, depthKm) = parseJmaCoordinate(coordinate)
        return Earthquake(
            id = "jmaxml_" + eventId.ifBlank { originTime },
            magnitude = magnitude,
            place = name,
            timeMillis = parseJmaDetailTime(originTime),
            latitude = lat,
            longitude = lon,
            depthKm = depthKm,
            url = "https://www.jma.go.jp/bosai/quake/",
            source = DataSource.JMA_XML,
            magnitudeType = magnitudeNode.getAttribute("type")
        )
    }

    private fun parseJmaCoordinate(value: String): Triple<Double, Double, Double> {
        // JMA 格式: +lat+lon-depth/ 如 +32.2+130.4-10000/ (深度单位米)
        val m = Regex("([+\\-]\\d+(?:\\.\\d+)?)([+\\-]\\d+(?:\\.\\d+)?)([+\\-]\\d+(?:\\.\\d+)?)").find(value)
            ?: return Triple(0.0, 0.0, 0.0)
        return try {
            Triple(
                m.groupValues[1].toDouble(),
                m.groupValues[2].toDouble(),
                kotlin.math.abs(m.groupValues[3].toDouble()) / 1000.0
            )
        } catch (e: Exception) {
            Triple(0.0, 0.0, 0.0)
        }
    }

    private fun parseJmaDetailTime(value: String): Long {
        // 2026-08-07T06:56:00+09:00
        return try {
            java.time.Instant.parse(value).toEpochMilli()
        } catch (e: Exception) {
            try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                format.parse(value)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    private fun regexFirst(text: String, pattern: String): String? {
        return Regex(pattern).find(text)?.groupValues?.get(1)
    }

    private fun firstChildText(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }

    private fun descendantText(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
    }

    private fun descendantElement(parent: Element, tag: String): Element? {
        val nodes = parent.getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0) as? Element else null
    }

    private fun request(urlString: String, headers: List<String> = emptyList()): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (EarthquakeMonitor)"
            )
            headers.forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    connection.setRequestProperty(
                        line.substring(0, idx).trim(),
                        line.substring(idx + 1).trim()
                    )
                }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDouble(value: String): Double {
        return try {
            value.trim().toDouble()
        } catch (e: NumberFormatException) {
            0.0
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

    private fun parseDepth(value: String): Double {
        val cleaned = value.replace("km", "").trim()
        return parseDouble(cleaned)
    }

    private fun parseIso(value: String): Long {
        return try {
            java.time.Instant.parse(value).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseHko(value: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            format.parse(value)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseBmkg(value: String): Long {
        // BMKG 现格式 ISO8601 "2026-08-09T15:15:04+00:00" (UTC), 旧格式 "09-Aug-26 17:57:22" (WIB, UTC+7)
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                .parse(value)?.time ?: 0L
        } catch (e: Exception) {
            try {
                val format = SimpleDateFormat("dd-MMM-yy HH:mm:ss", Locale.US)
                format.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                format.parse(value)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }
}