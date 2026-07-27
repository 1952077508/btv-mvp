package com.btv.mvp.data

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

data class DiagResult(
    val step: String,
    val success: Boolean,
    val detail: String,
    val durationMs: Long
)

data class FullDiagReport(
    val host: String,
    val port: Int,
    val results: List<DiagResult>,
    val overallHealth: String
)

object NetworkDiagnostics {

    suspend fun run(serverUrl: String): FullDiagReport = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagResult>()
        val url = java.net.URL(serverUrl)
        val host = url.host
        val port = if (url.port > 0) url.port else 80

        // Step 1: DNS resolution
        var start = System.currentTimeMillis()
        try {
            val addr = java.net.InetAddress.getByName(host)
            results.add(DiagResult(
                step = "DNS 解析",
                success = true,
                detail = "$host → ${addr.hostAddress}",
                durationMs = System.currentTimeMillis() - start
            ))
        } catch (e: Exception) {
            results.add(DiagResult(
                step = "DNS 解析",
                success = false,
                detail = "解析失败: ${e.message}",
                durationMs = System.currentTimeMillis() - start
            ))
        }

        // Step 2: TCP connection
        start = System.currentTimeMillis()
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.close()
            results.add(DiagResult(
                step = "TCP $host:$port",
                success = true,
                detail = "端口可达",
                durationMs = System.currentTimeMillis() - start
            ))
        } catch (e: Exception) {
            results.add(DiagResult(
                step = "TCP $host:$port",
                success = false,
                detail = "连接失败: ${e.message}",
                durationMs = System.currentTimeMillis() - start
            ))
        }

        // Step 3: HTTP POST to /api/room/create
        start = System.currentTimeMillis()
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url("$serverUrl/api/room/create")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            results.add(DiagResult(
                step = "HTTP POST /api/room/create",
                success = response.isSuccessful,
                detail = if (response.isSuccessful) "HTTP ${response.code} OK" else "HTTP ${response.code} ${response.message}",
                durationMs = System.currentTimeMillis() - start
            ))
            response.close()
        } catch (e: IOException) {
            results.add(DiagResult(
                step = "HTTP POST /api/room/create",
                success = false,
                detail = "请求失败: ${e.message}",
                durationMs = System.currentTimeMillis() - start
            ))
        } catch (e: Exception) {
            results.add(DiagResult(
                step = "HTTP POST /api/room/create",
                success = false,
                detail = "异常: ${e.message}",
                durationMs = System.currentTimeMillis() - start
            ))
        }

        val failedCount = results.count { !it.success }
        val health = when {
            failedCount == 0 -> "全部通过"
            failedCount <= 1 -> "部分异常"
            else -> "连接失败"
        }

        FullDiagReport(host, port, results, health)
    }
}
