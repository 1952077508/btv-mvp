package com.btv.mvp.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager {

    private var ws: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onMessage: ((Map<String, Any>) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null

    fun connect(url: String, userId: String) {
        val wsUrl = "$url?userId=$userId"
        val request = Request.Builder().url(wsUrl).build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val map: Map<String, Any> = gson.fromJson(
                        text,
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                    CoroutineScope(Dispatchers.Main).launch {
                        onMessage?.invoke(map)
                    }
                } catch (_: Exception) {}
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    onDisconnect?.invoke()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                CoroutineScope(Dispatchers.Main).launch {
                    onDisconnect?.invoke()
                }
            }
        })
    }

    fun send(type: String, payload: Map<String, Any> = emptyMap()) {
        val msg = mapOf("type" to type, "payload" to payload)
        ws?.send(gson.toJson(msg))
    }

    fun startHeartbeat(positionProvider: () -> Long, isPlayingProvider: () -> Boolean) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(5000)
                val msg = mapOf(
                    "type" to "heartbeat",
                    "payload" to mapOf(
                        "position" to (positionProvider() / 1000.0),
                        "isPlaying" to isPlayingProvider()
                    )
                )
                ws?.send(gson.toJson(msg))
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
    }

    fun disconnect() {
        stopHeartbeat()
        ws?.close(1000, "user_disconnect")
        ws = null
    }

    fun isConnected(): Boolean = ws != null
}
