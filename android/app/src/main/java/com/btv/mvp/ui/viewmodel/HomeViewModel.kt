package com.btv.mvp.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class HomeViewModel : ViewModel() {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class RoomCreated(val roomId: String, val userId: String) : UiState()
        data class RoomJoined(val roomId: String, val userId: String) : UiState()
        data class RoomChecked(val exists: Boolean, val memberCount: Int) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _roomCode = MutableStateFlow("")
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

    fun updateRoomCode(code: String) {
        if (code.length <= 6) {
            _roomCode.value = code.uppercase()
        }
    }

    fun createRoom(baseUrl: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val result = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$baseUrl/api/room/create")
                        .post("{}".toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(request).execute()
                }
                if (result.isSuccessful) {
                    val body = result.body?.string() ?: ""
                    val map: Map<String, Any> = gson.fromJson(
                        body,
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                    val roomId = map["roomId"] as? String ?: ""
                    val userId = map["hostId"] as? String ?: ""
                    _uiState.value = UiState.RoomCreated(roomId, userId)
                } else {
                    _uiState.value = UiState.Error("创建房间失败")
                }
            } catch (e: IOException) {
                _uiState.value = UiState.Error("网络连接失败: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("发生错误: ${e.message}")
            }
        }
    }

    fun checkRoom(baseUrl: String, roomId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val result = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$baseUrl/api/room/check/$roomId")
                        .post("{}".toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(request).execute()
                }
                if (result.isSuccessful) {
                    val body = result.body?.string() ?: ""
                    val map: Map<String, Any> = gson.fromJson(
                        body,
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                    val exists = (map["exists"] as? Boolean) ?: false
                    val memberCount = ((map["memberCount"] as? Double)?.toInt()) ?: 0
                    _uiState.value = UiState.RoomChecked(exists, memberCount)
                } else {
                    _uiState.value = UiState.Error("检查房间失败")
                }
            } catch (e: IOException) {
                _uiState.value = UiState.Error("网络连接失败: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("发生错误: ${e.message}")
            }
        }
    }

    fun joinRoom(baseUrl: String, roomId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val body = mapOf("roomId" to roomId)
                val result = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$baseUrl/api/room/join")
                        .post(gson.toJson(body).toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(request).execute()
                }
                if (result.isSuccessful) {
                    val bodyStr = result.body?.string() ?: ""
                    val map: Map<String, Any> = gson.fromJson(
                        bodyStr,
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                    val joinedRoomId = map["roomId"] as? String ?: roomId
                    val userId = map["userId"] as? String ?: ""
                    _uiState.value = UiState.RoomJoined(joinedRoomId, userId)
                } else {
                    _uiState.value = UiState.Error("加入房间失败，房间不存在或已满")
                }
            } catch (e: IOException) {
                _uiState.value = UiState.Error("网络连接失败: ${e.message}")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("发生错误: ${e.message}")
            }
        }
    }
}
