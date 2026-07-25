package com.btv.mvp.data

data class RoomInfo(
    val roomId: String,
    val userId: String,
    val isHost: Boolean,
    val videoUrl: String,
    val currentPos: Float,
    val isPlaying: Boolean
)

data class WsMessage(
    val type: String,
    val payload: Map<String, Any> = emptyMap()
)

data class SyncResponse(
    val serverPosition: Double,
    val offset: Double
)
