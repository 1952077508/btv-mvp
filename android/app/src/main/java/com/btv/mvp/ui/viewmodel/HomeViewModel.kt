package com.btv.mvp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.btv.mvp.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class RoomCreated(val roomId: String, val userId: String) : UiState()
        data class RoomJoined(val roomId: String, val userId: String) : UiState()
        data class RoomChecked(val exists: Boolean, val memberCount: Int) : UiState()
        data class DiagCompleted(val report: FullDiagReport) : UiState()
        data class HistoryLoaded(val entries: List<RoomHistoryEntry>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _roomCode = MutableStateFlow("")
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

    private val _roomHistory = MutableStateFlow<List<RoomHistoryEntry>>(emptyList())
    val roomHistory: StateFlow<List<RoomHistoryEntry>> = _roomHistory.asStateFlow()

    fun updateRoomCode(code: String) {
        if (code.length <= 6) _roomCode.value = code.uppercase()
    }

    fun createRoom(baseUrl: String) {
        AppLogger.i("HomeVM", "创建房间")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/room/create")
                    .header("X-User-Id", AuthManager.userId ?: "")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                val result = client.newCall(request).execute()
                val respBody = result.body?.string() ?: ""
                if (result.isSuccessful) {
                    val map: Map<String, Any> = gson.fromJson(respBody, object : TypeToken<Map<String, Any>>() {}.type)
                    val roomId = map["roomId"] as? String ?: ""
                    val userId = map["hostId"] as? String ?: ""
                    AppLogger.i("HomeVM", "房间创建成功 $roomId")
                    saveLocalHistory(roomId, "host")
                    _uiState.value = UiState.RoomCreated(roomId, userId)
                } else {
                    AppLogger.e("HomeVM", "创建失败 HTTP ${result.code}")
                    _uiState.value = UiState.Error("创建房间失败 (HTTP ${result.code})")
                }
            } catch (e: IOException) {
                AppLogger.e("HomeVM", "创建IO异常: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.value = UiState.Error("网络错误: ${e.javaClass.simpleName}")
            } catch (e: Exception) {
                AppLogger.e("HomeVM", "创建异常: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.value = UiState.Error("${e.javaClass.simpleName}")
            }
        }
    }

    fun joinRoom(baseUrl: String, roomId: String) {
        AppLogger.i("HomeVM", "加入房间 $roomId")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                val body = mapOf("roomId" to roomId)
                val request = Request.Builder()
                    .url("$baseUrl/api/room/join")
                    .header("X-User-Id", AuthManager.userId ?: "")
                    .post(gson.toJson(body).toRequestBody(jsonMediaType))
                    .build()
                val result = client.newCall(request).execute()
                val respBody = result.body?.string() ?: ""
                if (result.isSuccessful) {
                    val map: Map<String, Any> = gson.fromJson(respBody, object : TypeToken<Map<String, Any>>() {}.type)
                    val joinedRoomId = map["roomId"] as? String ?: roomId
                    val userId = map["userId"] as? String ?: ""
                    AppLogger.i("HomeVM", "加入房间成功 $joinedRoomId")
                    saveLocalHistory(joinedRoomId, "guest")
                    _uiState.value = UiState.RoomJoined(joinedRoomId, userId)
                } else {
                    AppLogger.e("HomeVM", "加入失败 HTTP ${result.code}")
                    _uiState.value = UiState.Error("加入房间失败 (HTTP ${result.code})")
                }
            } catch (e: IOException) {
                AppLogger.e("HomeVM", "加入IO异常: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.value = UiState.Error("网络错误: ${e.javaClass.simpleName}")
            } catch (e: Exception) {
                AppLogger.e("HomeVM", "加入异常: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.value = UiState.Error("${e.javaClass.simpleName}")
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(getApplication())
                val entries = db.roomHistoryDao().getAll().map {
                    RoomHistoryEntry(it.roomId, it.role, it.joinedAt)
                }
                _roomHistory.value = entries
                _uiState.value = UiState.HistoryLoaded(entries)
            } catch (e: Exception) {
                AppLogger.e("HomeVM", "加载历史失败: ${e.message}")
            }
        }
    }

    fun deleteHistory(roomId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(getApplication())
                db.roomHistoryDao().deleteByRoomId(roomId)
                loadHistory()
            } catch (_: Exception) {}
        }
    }

    fun runDiagnostics(baseUrl: String) {
        AppLogger.i("HomeVM", "网络诊断: $baseUrl")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                val report = NetworkDiagnostics.run(baseUrl)
                for (r in report.results) {
                    val s = if (r.success) "PASS" else "FAIL"
                    AppLogger.i("Diag", "$s ${r.step}: ${r.detail} (${r.durationMs}ms)")
                }
                AppLogger.i("Diag", "结果: ${report.overallHealth}")
                _uiState.value = UiState.DiagCompleted(report)
            } catch (e: Exception) {
                AppLogger.e("Diag", "异常: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.value = UiState.Error("诊断失败")
            }
        }
    }

    private fun saveLocalHistory(roomId: String, role: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(getApplication())
                db.roomHistoryDao().insert(
                    RoomHistoryEntity(roomId = roomId, role = role, joinedAt = System.currentTimeMillis())
                )
            } catch (_: Exception) {}
        }
    }
}

data class RoomHistoryEntry(
    val roomId: String,
    val role: String,
    val joinedAt: Long
)
