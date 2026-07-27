package com.btv.mvp.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.btv.mvp.data.AppLogger
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
        AppLogger.i("WS", "连接: $wsUrl")
        val request = Request.Builder().url(wsUrl).build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                AppLogger.i("WS", "连接已建立")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val map: Map<String, Any> = gson.fromJson(
                        text,
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                    val type = map["type"] as? String ?: "?"
                    if (type != "heartbeat" && type != "correct") {
                        AppLogger.d("WS", "收到: type=$type")
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        onMessage?.invoke(map)
                    }
                } catch (e: Exception) {
                    AppLogger.e("WS", "消息解析失败: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.w("WS", "关闭中 code=$code reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.w("WS", "已关闭 code=$code")
                CoroutineScope(Dispatchers.Main).launch {
                    onDisconnect?.invoke()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.e("WS", "连接失败: ${t.javaClass.simpleName}: ${t.message}${if (response != null) " HTTP ${response.code}" else ""}")
                CoroutineScope(Dispatchers.Main).launch {
                    onDisconnect?.invoke()
                }
            }
        })
    }

    fun send(type: String, payload: Map<String, Any> = emptyMap()) {
        val msg = mapOf("type" to type, "payload" to payload)
        val json = gson.toJson(msg)
        if (type != "heartbeat") {
            AppLogger.d("WS", "发送: type=$type")
        }
        ws?.send(json)
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
