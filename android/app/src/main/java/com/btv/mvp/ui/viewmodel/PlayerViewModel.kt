package com.btv.mvp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.btv.mvp.network.WebSocketManager
import com.btv.mvp.player.ExoPlayerManager
import com.btv.mvp.data.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val exoPlayer = ExoPlayerManager()
    val wsManager = WebSocketManager()

    private var roomId: String = ""
    private var userId: String = ""
    private var isHost: Boolean = false
    private var lastLocalActionTime: Long = 0L
    private var progressJob: Job? = null
    private var cooldownJob: Job? = null

    sealed class PlaybackState {
        data object Idle : PlaybackState()
        data object Loading : PlaybackState()
        data object Playing : PlaybackState()
        data object Paused : PlaybackState()
        data class Error(val message: String) : PlaybackState()
    }

    sealed class SyncState {
        data object Idle : SyncState()
        data object Connected : SyncState()
        data object Disconnected : SyncState()
        data class Synced(val offset: Double) : SyncState()
        data object RoomClosed : SyncState()
    }

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _videoUrl = MutableStateFlow("")
    val videoUrl: StateFlow<String> = _videoUrl.asStateFlow()

    private val _roomCode = MutableStateFlow("")
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

    private val _syncOffset = MutableStateFlow(0.0)
    val syncOffset: StateFlow<Double> = _syncOffset.asStateFlow()

    fun initialize(roomId: String, userId: String, isHost: Boolean, baseUrl: String) {
        this.roomId = roomId
        this.userId = userId
        this.isHost = isHost
        _roomCode.value = roomId

        AppLogger.i("Player", "初始化 roomId=$roomId userId=$userId isHost=$isHost baseUrl=$baseUrl")
        exoPlayer.init(getApplication())

        val wsBase = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        val wsUrl = "$wsBase/ws/$roomId"

        wsManager.onMessage = { msg -> handleWsMessage(msg) }
        wsManager.onDisconnect = {
            AppLogger.w("Player", "WebSocket 断开")
            _syncState.value = SyncState.Disconnected
        }

        AppLogger.i("Player", "WebSocket 连接中: $wsUrl")
        wsManager.connect(wsUrl, userId)
        _syncState.value = SyncState.Connected

        startProgressUpdates()
    }

    fun play() {
        AppLogger.d("Player", "本地操作: play pos=${exoPlayer.getCurrentPosition()}")
        exoPlayer.play()
        _playbackState.value = PlaybackState.Playing
        lastLocalActionTime = System.currentTimeMillis()

        wsManager.send("play", mapOf(
            "position" to (exoPlayer.getCurrentPosition() / 1000.0)
        ))
    }

    fun pause() {
        AppLogger.d("Player", "本地操作: pause pos=${exoPlayer.getCurrentPosition()}")
        exoPlayer.pause()
        _playbackState.value = PlaybackState.Paused
        lastLocalActionTime = System.currentTimeMillis()

        wsManager.send("pause", mapOf(
            "position" to (exoPlayer.getCurrentPosition() / 1000.0)
        ))
    }

    fun seekTo(positionMs: Long) {
        AppLogger.d("Player", "本地操作: seek ${positionMs}ms")
        exoPlayer.seekTo(positionMs)
        _currentPosition.value = positionMs
        lastLocalActionTime = System.currentTimeMillis()

        wsManager.send("seek", mapOf(
            "position" to (positionMs / 1000.0)
        ))
    }

    fun changeVideo(url: String) {
        if (!isHost) return
        AppLogger.i("Player", "切换视频: $url")
        _videoUrl.value = url
        exoPlayer.setVideoUrl(url)
        _playbackState.value = PlaybackState.Loading

        wsManager.send("change_video", mapOf("videoUrl" to url))
    }

    fun requestSync() {
        AppLogger.d("Player", "手动同步请求")
        wsManager.send("sync_request", mapOf(
            "position" to (exoPlayer.getCurrentPosition() / 1000.0)
        ))
    }

    fun disconnect() {
        progressJob?.cancel()
        cooldownJob?.cancel()
        wsManager.disconnect()
        exoPlayer.release()
    }

    private fun startProgressUpdates() {
        progressJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                _currentPosition.value = exoPlayer.getCurrentPosition()
                _duration.value = exoPlayer.getDuration()
                delay(250)
            }
        }
    }

    private fun handleWsMessage(msg: Map<String, Any>) {
        val type = msg["type"] as? String ?: return
        val payload = msg["payload"] as? Map<*, *> ?: emptyMap<String, Any>()

        when (type) {
            "welcome" -> {
                val role = payload["role"] as? String ?: "guest"
                isHost = role == "host"
                AppLogger.i("Player", "WS welcome: role=$role")
            }

            "room_state" -> {
                val url = payload["videoUrl"] as? String ?: ""
                val position = ((payload["position"] as? Number)?.toDouble() ?: 0.0) * 1000
                val isPlaying = payload["isPlaying"] as? Boolean ?: false
                AppLogger.i("Player", "WS room_state: video=${url.take(50)} pos=$position isPlaying=$isPlaying")

                if (url.isNotEmpty()) {
                    _videoUrl.value = url
                    exoPlayer.setVideoUrl(url, position.toLong())
                    if (isPlaying) {
                        exoPlayer.play()
                        _playbackState.value = PlaybackState.Playing
                    } else {
                        _playbackState.value = PlaybackState.Paused
                    }
                }
            }

            "play" -> {
                val position = ((payload["position"] as? Number)?.toDouble() ?: 0.0) * 1000
                AppLogger.d("Player", "WS play: pos=$position")
                if (abs(exoPlayer.getCurrentPosition() - position.toLong()) > 500) {
                    exoPlayer.seekTo(position.toLong())
                }
                exoPlayer.play()
                _playbackState.value = PlaybackState.Playing
            }

            "pause" -> {
                val position = ((payload["position"] as? Number)?.toDouble() ?: 0.0) * 1000
                AppLogger.d("Player", "WS pause: pos=$position")
                if (abs(exoPlayer.getCurrentPosition() - position.toLong()) > 500) {
                    exoPlayer.seekTo(position.toLong())
                }
                exoPlayer.pause()
                _playbackState.value = PlaybackState.Paused
            }

            "seek" -> {
                val targetMs = ((payload["position"] as? Number)?.toDouble() ?: 0.0) * 1000
                if (cooldownActive()) {
                    AppLogger.d("Player", "WS seek 忽略 (冷静期): target=${targetMs}ms")
                    return
                }
                AppLogger.d("Player", "WS seek: target=${targetMs}ms")
                exoPlayer.seekTo(targetMs.toLong())
                _currentPosition.value = targetMs.toLong()
            }

            "correct" -> {
                if (cooldownActive()) {
                    AppLogger.d("Player", "WS correct 忽略 (冷静期)")
                    return
                }
                val targetMs = ((payload["targetPosition"] as? Number)?.toDouble() ?: 0.0) * 1000
                val currentMs = exoPlayer.getCurrentPosition()
                val diff = abs(currentMs - targetMs.toLong())
                if (diff > 500) {
                    AppLogger.d("Player", "WS correct: target=${targetMs}ms diff=${diff}ms")
                    exoPlayer.seekTo(targetMs.toLong())
                    _currentPosition.value = targetMs.toLong()
                }
                val isPlaying = payload["isPlaying"] as? Boolean
                if (isPlaying == true) {
                    exoPlayer.play()
                    _playbackState.value = PlaybackState.Playing
                }
            }

            "sync_response" -> {
                val offset = (payload["offset"] as? Number)?.toDouble() ?: 0.0
                AppLogger.d("Player", "WS sync_response: offset=$offset")
                _syncOffset.value = offset
                _syncState.value = SyncState.Synced(offset)
                if (abs(offset) > 0.5) {
                    val targetMs = ((payload["serverPosition"] as? Number)?.toDouble() ?: 0.0) * 1000
                    exoPlayer.seekTo(targetMs.toLong())
                    _currentPosition.value = targetMs.toLong()
                }
            }

            "video_changed" -> {
                val url = payload["videoUrl"] as? String ?: ""
                AppLogger.i("Player", "WS video_changed: ${url.take(50)}")
                if (url.isNotEmpty()) {
                    _videoUrl.value = url
                    exoPlayer.setVideoUrl(url)
                    _playbackState.value = PlaybackState.Loading
                }
            }

            "room_closed" -> {
                AppLogger.w("Player", "WS room_closed")
                _syncState.value = SyncState.RoomClosed
            }

            "error" -> {
                val msg = payload["message"] as? String ?: "unknown"
                AppLogger.e("Player", "WS error: $msg")
            }

            else -> {
                AppLogger.d("Player", "WS unknown type: $type")
            }
        }
    }

    private fun cooldownActive(): Boolean {
        return System.currentTimeMillis() - lastLocalActionTime < 2000
    }
}
