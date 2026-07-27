package com.btv.mvp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.btv.mvp.data.AppLogger
import com.btv.mvp.data.AuthManager
import com.btv.mvp.data.PrefsManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.IOException

class AdminViewModel : ViewModel() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    data class Stats(
        val totalUsers: Int = 0,
        val activeRooms: Int = 0,
        val rooms: List<RoomInfo> = emptyList()
    )

    data class RoomInfo(
        val roomId: String,
        val hostId: String,
        val videoUrl: String,
        val isPlaying: Boolean,
        val memberCount: Int
    )

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Loaded(val stats: Stats) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                val request = Request.Builder()
                    .url("${PrefsManager.serverUrl}/api/admin/stats")
                    .header("X-User-Id", AuthManager.userId ?: "")
                    .get()
                    .build()
                val result = client.newCall(request).execute()
                val respBody = result.body?.string() ?: ""
                if (result.isSuccessful) {
                    val map: Map<String, Any> = gson.fromJson(respBody, object : TypeToken<Map<String, Any>>() {}.type)
                    val roomsRaw = map["rooms"] as? List<*> ?: emptyList<Any>()
                    val rooms = roomsRaw.mapNotNull { r ->
                        val rm = r as? Map<*, *> ?: return@mapNotNull null
                        RoomInfo(
                            roomId = rm["roomId"] as? String ?: "",
                            hostId = rm["hostId"] as? String ?: "",
                            videoUrl = rm["videoUrl"] as? String ?: "",
                            isPlaying = rm["isPlaying"] as? Boolean ?: false,
                            memberCount = ((rm["memberCount"] as? Number)?.toInt() ?: 0)
                        )
                    }
                    val stats = Stats(
                        totalUsers = ((map["totalUsers"] as? Number)?.toInt() ?: 0),
                        activeRooms = ((map["activeRooms"] as? Number)?.toInt() ?: 0),
                        rooms = rooms
                    )
                    _uiState.value = UiState.Loaded(stats)
                } else {
                    _uiState.value = UiState.Error("加载失败 HTTP ${result.code}")
                }
            } catch (e: IOException) {
                _uiState.value = UiState.Error("网络错误: ${e.javaClass.simpleName}")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}
