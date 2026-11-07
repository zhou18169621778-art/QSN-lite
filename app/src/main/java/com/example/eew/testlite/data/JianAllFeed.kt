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

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Jian Project /all 聚合 WebSocket（wss://api.sismotide.top/all）。
 *
 * 连上后服务端先推一次各源最新快照（键名 source：{源id}），连接成功后发送
 * alllist 拉取 cenc/cwa/jma/hko 各 50 条历史；此后按单源信封 {type, Data, md5}
 * 增量推送。事件按源解析为 Earthquake，MainActivity/EewService 通过 acquire/
 * release 共享一条连接；merge() 负责与 HTTP 轮询结果做同事件去重合并。
 */
object JianAllFeed {
    private const val TAG = "JianAllFeed"
    private const val WS_URL = "wss://api.sismotide.top/all"
    private const val CACHE_FILE = "jian_feed_cache.bin"
    private const val MIN_MAGNITUDE = 2.0
    private const val EVENT_TTL_MS = 24 * 60 * 60 * 1000L
    private const val MAX_EVENTS = 300
    private const val RECONNECT_BASE_MS = 10_000L
    private const val RECONNECT_MAX_MS = 300_000L

    enum class State { IDLE, CONNECTING, CONNECTED, RECONNECT_WAIT, AUTH_FAILED }

    @Volatile
    var state: State = State.IDLE
        private set

    /** 数据更新回调，在 WebSocket 线程触发，调用方自行切线程 */
    @Volatile
    var listener: (() -> Unit)? = null

    /** 预警帧回调（CEA / CEA-PR / CWA-EEW / SA），在 WebSocket 线程触发 */
    @Volatile
    var eewListener: ((Eew) -> Unit)? = null

    private val eewSources = setOf("cea", "cea-pr", "cwa-eew", "sa")

    private val refs = AtomicInteger(0)
    private val events = LinkedHashMap<String, Earthquake>()

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null

    @Volatile
    private var stopped = true

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var reconnectAttempt = 0

    /** 每次 start/stop 递增，令在途的连接/重连任务失效 */
    @Volatile
    private var generation = 0

    fun acquire(context: Context) {
        appContext = context.applicationContext
        if (refs.incrementAndGet() == 1) start()
    }

    fun release() {
        if (refs.decrementAndGet() > 0) return
        synchronized(this) { refs.set(0) }
        stop()
    }

    /** 凭证变化（保存/清除）后重置连接状态；有持有者时立即用新凭证重连 */
    fun onCredentialChanged() {
        appContext ?: return
        if (refs.get() > 0) {
            stop()
            start()
        } else {
            state = State.IDLE
        }
    }

    private fun start() {
        val context = appContext ?: return
        if (!JianAuth.hasCredential(context)) {
            state = State.IDLE
            return
        }
        synchronized(this) {
            loadCache(context)
            stopped = false
            reconnectAttempt = 0
            generation++
        }
        val gen = generation
        // 换票是同步网络请求，不能在调用方（多为主线程）直接执行
        Thread {
            connect(gen)
        }.start()
    }

    private fun stop() {
        stopped = true
        generation++
        state = State.IDLE
        socket?.cancel()
        socket = null
    }

    private fun connect(gen: Int) {
        val context = appContext ?: return
        if (stopped || gen != generation) return
        val token = try {
            JianAuth.accessToken(context)
        } catch (e: JianAuth.AuthException) {
            if (stopped || gen != generation) return
            if (e.isCredentialInvalid()) {
                state = State.AUTH_FAILED
            } else {
                scheduleReconnect()
            }
            return
        } catch (e: Exception) {
            if (!stopped && gen == generation) scheduleReconnect()
            return
        }
        if (stopped || gen != generation) return
        state = State.CONNECTING
        val request = Request.Builder()
            .url("$WS_URL?key=$token")
            .header("User-Agent", "Mozilla/5.0 (QSN-lite)")
            .build()
        socket = client.newWebSocket(request, FeedSocketListener())
    }

    private fun scheduleReconnect() {
        if (stopped) return
        state = State.RECONNECT_WAIT
        val delay = (RECONNECT_BASE_MS shl reconnectAttempt.coerceAtMost(5))
            .coerceAtMost(RECONNECT_MAX_MS)
        reconnectAttempt++
        val gen = generation
        Thread {
            try {
                Thread.sleep(delay)
            } catch (e: InterruptedException) {
                return@Thread
            }
            connect(gen)
        }.start()
    }

    private class FeedSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "connected")
            JianAllFeed.state = State.CONNECTED
            JianAllFeed.reconnectAttempt = 0
            webSocket.send("alllist")
            JianAllFeed.notifyChanged()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                JianAllFeed.handleFrame(text)
            } catch (e: Exception) {
                Log.w(TAG, "frame parse failed", e)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            onMessage(webSocket, bytes.utf8())
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "closed: $code")
            JianAllFeed.scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "failure", t)
            if (response?.code == 401) {
                // 访问令牌被拒：丢弃缓存令牌后按退避重试，避免反复无鉴权被掐触发 IP 封禁
                JianAuth.invalidateAccessToken()
            }
            JianAllFeed.scheduleReconnect()
        }
    }

    private fun handleFrame(text: String) {
        val obj = JSONObject(text)
        when (val type = obj.optString("type")) {
            "heartbeat", "ping", "pong" -> return
            "error" -> {
                handleError(obj.optString("message"))
                return
            }
            "all" -> handleSnapshot(obj)
            else -> {
                when {
                    type in eewSources -> {
                        handleEew(type, obj)
                        return
                    }
                    type.endsWith("list_response") ->
                        handleListResponse(type.removeSuffix("_response").removeSuffix("list"), obj)
                    else -> handleSingle(type, obj)
                }
            }
        }
        notifyChanged()
    }

    /** 初始快照：键名为 source：{源id}（全角冒号，兼容半角） */
    private fun handleSnapshot(root: JSONObject) {
        for (key in root.keys()) {
            if (!key.startsWith("source：") && !key.startsWith("source:")) continue
            val sourceId = key.substringAfter('：').substringAfter(':')
            val data = root.optJSONObject(key)?.optJSONObject("Data") ?: continue
            if (sourceId in eewSources) {
                parseEew(sourceId, data)?.let { eewListener?.invoke(it) }
            } else {
                parseQuake(sourceId, data)?.let { upsert(it) }
            }
        }
    }

    private fun handleSingle(sourceId: String, envelope: JSONObject) {
        val data = envelope.optJSONObject("Data") ?: return
        parseQuake(sourceId, data)?.let { upsert(it) }
    }

    private fun handleEew(sourceId: String, envelope: JSONObject) {
        val data = envelope.optJSONObject("Data") ?: return
        parseEew(sourceId, data)?.let { eewListener?.invoke(it) }
    }

    /**
     * 预警帧字段很少（id/number/originTime/经纬度/深度/震级/地名），
     * 没有发布时刻与强度等级：报告时刻取发震时刻，预警通道默认视为警报。
     */
    private fun parseEew(sourceId: String, d: JSONObject): Eew? {
        val source = when (sourceId) {
            "cea" -> DataSource.CEA
            "cea-pr" -> DataSource.CEA_PR
            "cwa-eew" -> DataSource.CWA
            "sa" -> DataSource.SA
            else -> return null
        }
        val id = d.optString("id")
        if (id.isBlank()) return null
        val origin = parseTimeMillis(d)
        if (origin <= 0L) return null
        return Eew(
            id = "jian_${sourceId}_$id",
            source = source,
            reportTimeMillis = origin,
            originTimeMillis = origin,
            hypocenter = d.optString("placeName"),
            latitude = d.optDouble("latitude", 0.0),
            longitude = d.optDouble("longitude", 0.0),
            magnitude = d.optDouble("magnitude", 0.0),
            depthKm = d.optDouble("depth", 0.0),
            maxIntensity = "",
            isWarn = true,
            isFinal = false,
            isCancel = false,
            reportNum = d.optInt("number", 0)
        )
    }

    /** alllist 响应：cenc/cwa/jma/hko 各推送 50 条历史 */
    private fun handleListResponse(sourceId: String, envelope: JSONObject) {
        if (sourceOf(sourceId) == null) return
        val data = envelope.optJSONArray("Data") ?: return
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            parseQuake(sourceId, item)?.let { upsert(it) }
        }
    }

    private fun handleError(message: String) {
        Log.w(TAG, "server error: $message")
        // 令牌被拒时丢弃缓存，下次重连前重新换票，避免反复无鉴权被掐触发 IP 封禁
        JianAuth.invalidateAccessToken()
        socket?.cancel()
        scheduleReconnect()
    }

    private fun upsert(quake: Earthquake) {
        synchronized(this) {
            events[quake.id] = quake
        }
    }

    @Synchronized
    private fun pruneLocked() {
        val cutoff = System.currentTimeMillis() - EVENT_TTL_MS
        events.values.removeAll { it.timeMillis < cutoff || it.timeMillis <= 0L }
        if (events.size > MAX_EVENTS) {
            events.entries
                .sortedByDescending { it.value.timeMillis }
                .drop(MAX_EVENTS)
                .forEach { events.remove(it.key) }
        }
    }

    @Synchronized
    private fun quakesLocked(): List<Earthquake> {
        pruneLocked()
        return events.values.sortedByDescending { it.timeMillis }
    }

    /**
     * 把 feed 事件合并进 HTTP 轮询结果：feed 事件优先，HTTP 中与 feed
     * 同源且同事件（时间/震级/位置相近）的条目视为重复丢弃。
     */
    fun merge(base: List<Earthquake>): List<Earthquake> {
        val feed = quakesLocked()
        if (feed.isEmpty()) return base
        val merged = ArrayList<Earthquake>(feed)
        outer@ for (quake in base) {
            for (feedQuake in feed) {
                if (feedQuake.id == quake.id || isSameEvent(feedQuake, quake)) continue@outer
            }
            merged.add(quake)
        }
        return merged.sortedByDescending { it.timeMillis }
    }

    private fun isSameEvent(a: Earthquake, b: Earthquake): Boolean {
        if (a.source != b.source) return false
        if (kotlin.math.abs(a.timeMillis - b.timeMillis) > 2 * 60 * 1000L) return false
        if (kotlin.math.abs(a.magnitude - b.magnitude) > 0.5) return false
        if (a.latitude != 0.0 && b.latitude != 0.0) {
            if (kotlin.math.abs(a.latitude - b.latitude) > 2.0) return false
            if (kotlin.math.abs(a.longitude - b.longitude) > 2.0) return false
        }
        return true
    }

    /** 各源当前缓存的列表事件数（供面板诊断展示） */
    @Synchronized
    fun countsBySource(): Map<DataSource, Int> {
        pruneLocked()
        return events.values.groupingBy { it.source }.eachCount()
    }

    private fun notifyChanged() {
        appContext?.let { persist(it) }
        listener?.invoke()
    }

    private fun persist(context: Context) {
        try {
            val snapshot = ArrayList(quakesLocked())
            ObjectOutputStream(File(context.cacheDir, CACHE_FILE).outputStream().buffered())
                .use { it.writeObject(snapshot) }
        } catch (e: Exception) {
            Log.w(TAG, "persist failed", e)
        }
    }

    private fun loadCache(context: Context) {
        val file = File(context.cacheDir, CACHE_FILE)
        if (!file.exists()) return
        try {
            @Suppress("UNCHECKED_CAST")
            val saved = ObjectInputStream(file.inputStream().buffered()).use {
                it.readObject() as? ArrayList<Earthquake>
            }
            saved?.forEach { events[it.id] = it }
        } catch (e: Exception) {
            file.delete()
        }
    }

    private fun sourceOf(sourceId: String): DataSource? {
        return when (sourceId) {
            "usgs" -> DataSource.USGS
            "cenc" -> DataSource.WOLFX_CENC
            "jma" -> DataSource.WOLFX_JMA
            "hko" -> DataSource.HKO
            "emsc" -> DataSource.EMSC
            "bmkg" -> DataSource.BMKG
            "cwa" -> DataSource.CWA
            "kma" -> DataSource.KMA
            "geonet" -> DataSource.GEONET
            "gfz" -> DataSource.GFZ
            "ingv" -> DataSource.INGV
            "afad" -> DataSource.AFAD
            else -> null
        }
    }

    private fun parseQuake(sourceId: String, d: JSONObject): Earthquake? {
        val source = sourceOf(sourceId) ?: return null
        val timeMillis = parseTimeMillis(d)
        if (timeMillis <= 0L) return null
        val magnitude = d.optDouble("magnitude", 0.0)
        if (magnitude < MIN_MAGNITUDE) return null
        val rawId = d.optString("id")
        if (rawId.isBlank()) return null
        val placeName = d.optString("placeName")
        val place = when (sourceId) {
            "kma" -> d.optString("placename_zh").ifBlank { placeName }
            else -> placeName
        }
        return Earthquake(
            id = "jian_${sourceId}_$rawId",
            magnitude = magnitude,
            place = place,
            timeMillis = timeMillis,
            latitude = d.optDouble("latitude", 0.0),
            longitude = d.optDouble("longitude", 0.0),
            depthKm = d.optDouble("depth", 0.0),
            url = sourceUrl(sourceId, rawId),
            source = source,
            magnitudeType = when (sourceId) {
                "jma" -> d.optString("magnitudeType")
                // AFAD 的 infoTypeName 为震级类型（如 ML）
                "afad" -> d.optString("infoTypeName")
                else -> ""
            },
            agency = if (sourceId == "emsc") d.optString("agency") else "",
            status = when (sourceId) {
                "cenc" -> normalizeDetermination(d.optString("infoTypeName"))
                // USGS/GeoNet 的 infoTypeName 为 automatic / reviewed
                "usgs", "geonet" -> d.optString("infoTypeName").lowercase()
                else -> ""
            },
            maxIntensity = when (sourceId) {
                "jma", "kma" -> d.optString("intensity")
                "cwa" -> d.optInt("intensity", 0).takeIf { it > 0 }?.toString() ?: ""
                else -> ""
            }
        )
    }

    private fun parseTimeMillis(d: JSONObject): Long {
        return when (val value = d.opt("originTime")) {
            is Number -> value.toLong()
            is String -> try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (e: Exception) {
                0L
            }
            else -> 0L
        }
    }

    /** CENC 的 infoTypeName 形如 [正式测定]/[自动测定]，归一为既有 status 取值 */
    private fun normalizeDetermination(value: String): String {
        return when {
            value.contains("正式") -> "reviewed"
            value.contains("自动") -> "automatic"
            else -> ""
        }
    }

    private fun sourceUrl(sourceId: String, rawId: String): String {
        return when (sourceId) {
            "usgs" -> "https://earthquake.usgs.gov/earthquakes/eventpage/$rawId"
            "cenc" -> "https://www.ceic.ac.cn/"
            "jma" -> "https://www.jma.go.jp/bosai/quake/"
            "hko" -> "https://www.hko.gov.hk/sc/gts/equake/equake.htm"
            "emsc" -> "https://www.emsc-csem.org/"
            "bmkg" -> "https://www.bmkg.go.id/gempabumi/"
            "cwa" -> "https://www.cwa.gov.tw/V8/C/"
            "kma" -> "https://www.kma.go.kr/"
            "geonet" -> "https://www.geonet.org.nz/"
            "gfz" -> "https://www.gfz-potsdam.de/"
            "ingv" -> "https://www.ingv.it/"
            "afad" -> "https://www.afad.gov.tr/"
            else -> ""
        }
    }
}
