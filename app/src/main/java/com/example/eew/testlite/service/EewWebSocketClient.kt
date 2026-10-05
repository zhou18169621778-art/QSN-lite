package com.example.eew.testlite.service

import android.util.Log
import com.example.eew.testlite.data.DataSource
import com.example.eew.testlite.data.Eew
import com.example.eew.testlite.data.EewRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

class EewWebSocketClient(
    private val onEew: (Eew) -> Unit,
    private val onConnectionStateChanged: (DataSource, Boolean) -> Unit = { _, _ -> }
) {
    companion object {
        private const val TAG = "EewWebSocket"
        private const val JMA_EEW_WS_URL = "wss://ws-api.wolfx.jp/jma_eew"
        private const val CENC_EEW_WS_URL = "wss://ws-api.wolfx.jp/cenc_eew"
        private const val RECONNECT_DELAY_MS = 10000L
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val sockets = mutableListOf<WebSocket>()
    private var closed = false

    fun start() {
        closed = false
        connect(JMA_EEW_WS_URL, DataSource.WOLFX_JMA) { json -> EewRepository.parseJmaJson(json) }
        connect(CENC_EEW_WS_URL, DataSource.WOLFX_CENC) { json -> EewRepository.parseCencJson(json) }
    }

    private fun connect(
        url: String,
        source: DataSource,
        parser: (String) -> Eew?
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Origin", "https://api.wolfx.jp")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "connected: $url")
                onConnectionStateChanged(source, true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    if (!text.contains("\"type\"")) return
                    val type = org.json.JSONObject(text).optString("type")
                    if (type == "heartbeat" || type == "pong" || type.isBlank()) return
                    parser(text)?.let { onEew(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "parse failed", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "closed: $url")
                onConnectionStateChanged(source, false)
                scheduleReconnect(url, source, parser)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "failure: $url", t)
                onConnectionStateChanged(source, false)
                scheduleReconnect(url, source, parser)
            }
        })
        synchronized(sockets) {
            sockets.add(ws)
        }
    }

    private fun scheduleReconnect(
        url: String,
        source: DataSource,
        parser: (String) -> Eew?
    ) {
        if (closed) return
        Thread {
            try {
                Thread.sleep(RECONNECT_DELAY_MS)
            } catch (e: InterruptedException) {
                return@Thread
            }
            if (!closed) connect(url, source, parser)
        }.start()
    }

    fun close() {
        closed = true
        synchronized(sockets) {
            sockets.forEach { it.cancel() }
            sockets.clear()
        }
        client.dispatcher.executorService.shutdown()
    }
}
