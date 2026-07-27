package com.btv.mvp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.btv.mvp.data.AppLogger
import com.btv.mvp.data.AuthManager
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

class LoginViewModel : ViewModel() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class LoggedIn(val isAdmin: Boolean) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun register(baseUrl: String, username: String, password: String) {
        AppLogger.i("LoginVM", "注册: $username")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                val body = mapOf("username" to username, "password" to password)
                val request = Request.Builder()
                    .url("$baseUrl/api/auth/register")
                    .post(gson.toJson(body).toRequestBody(jsonMediaType))
                    .build()
                val result = client.newCall(request).execute()
                val respBody = result.body?.string() ?: ""

                if (result.isSuccessful) {
                    val map: Map<String, Any> = gson.fromJson(respBody, object : TypeToken<Map<String, Any>>() {}.type)
                    saveAndEmit(map)
                } else {
                    val detail = extractDetail(respBody)
                    AppLogger.e("LoginVM", "注册失败 HTTP ${result.code}: $detail")
                    _uiState.value = UiState.Error("注册失败: $detail")
                }
            } catch (e: IOException) {
                AppLogger.e("LoginVM", "注册IO异常: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.value = UiState.Error("网络错误: ${e.javaClass.simpleName}")
            } catch (e: Exception) {
                AppLogger.e("LoginVM", "注册异常: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.value = UiState.Error("${e.javaClass.simpleName}")
            }
        }
    }

    fun login(baseUrl: String, username: String, password: String) {
        AppLogger.i("LoginVM", "登录: $username")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                val body = mapOf("username" to username, "password" to password)
                val request = Request.Builder()
                    .url("$baseUrl/api/auth/login")
                    .post(gson.toJson(body).toRequestBody(jsonMediaType))
                    .build()
                val result = client.newCall(request).execute()
                val respBody = result.body?.string() ?: ""

                if (result.isSuccessful) {
                    val map: Map<String, Any> = gson.fromJson(respBody, object : TypeToken<Map<String, Any>>() {}.type)
                    saveAndEmit(map)
                } else {
                    val detail = extractDetail(respBody)
                    AppLogger.e("LoginVM", "登录失败 HTTP ${result.code}: $detail")
                    _uiState.value = UiState.Error("登录失败: $detail")
                }
            } catch (e: IOException) {
                AppLogger.e("LoginVM", "登录IO异常: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.value = UiState.Error("网络错误: ${e.javaClass.simpleName}")
            } catch (e: Exception) {
                AppLogger.e("LoginVM", "登录异常: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.value = UiState.Error("${e.javaClass.simpleName}")
            }
        }
    }

    private fun saveAndEmit(map: Map<String, Any>) {
        val token = map["token"] as? String ?: ""
        val userId = map["userId"] as? String ?: ""
        val username = map["username"] as? String ?: ""
        val isAdmin = (map["isAdmin"] as? Boolean) ?: false

        AuthManager.saveSession(token, userId, username, isAdmin)
        AppLogger.i("LoginVM", "认证成功 userId=$userId admin=$isAdmin")
        _uiState.value = UiState.LoggedIn(isAdmin)
    }

    private fun extractDetail(body: String): String {
        return try {
            val map: Map<String, Any> = gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
            map["detail"] as? String ?: body.take(80)
        } catch (_: Exception) {
            body.take(80)
        }
    }
}
