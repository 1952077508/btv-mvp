package com.btv.mvp.ui.screens

import android.graphics.Color as AndroidColor
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.btv.mvp.data.PrefsManager
import com.btv.mvp.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    roomId: String,
    userId: String,
    isHost: Boolean,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val videoUrl by viewModel.videoUrl.collectAsState()
    val syncOffset by viewModel.syncOffset.collectAsState()

    var showVideoDialog by remember { mutableStateOf(false) }
    var showSyncResult by remember { mutableStateOf(false) }
    val disconnected = syncState is PlayerViewModel.SyncState.Disconnected ||
                        syncState is PlayerViewModel.SyncState.RoomClosed
    val textureView = remember { TextureView(ctx).apply { setBackgroundColor(AndroidColor.BLACK) } }

    LaunchedEffect(Unit) {
        viewModel.initialize(roomId, userId, isHost, PrefsManager.serverUrl, textureView)
    }

    LaunchedEffect(syncState) {
        if (syncState is PlayerViewModel.SyncState.Synced) showSyncResult = true
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnect()
            textureView.surfaceTextureListener = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("房间: $roomId") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.disconnect(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isHost && !disconnected) {
                        IconButton(onClick = { showVideoDialog = true }) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = "切换视频")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black)
            ) {
                AndroidView(
                    factory = { textureView.apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } },
                    modifier = Modifier.fillMaxSize()
                )

                if (playbackState is PlayerViewModel.PlaybackState.Idle && videoUrl.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (isHost) "请输入视频链接" else "等待房主添加视频...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(formatDuration(currentPosition), style = MaterialTheme.typography.labelSmall)

                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { fraction ->
                            if (!disconnected) viewModel.seekTo((fraction * duration).toLong())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !disconnected
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.requestSync() }, enabled = !disconnected) {
                            Icon(Icons.Default.Sync, "同步", tint = if (disconnected) Color.Gray else MaterialTheme.colorScheme.primary)
                        }

                        if (playbackState is PlayerViewModel.PlaybackState.Playing) {
                            IconButton(onClick = { viewModel.pause() }) {
                                Icon(Icons.Default.PauseCircle, "暂停", Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(onClick = { viewModel.play() }, enabled = videoUrl.isNotEmpty() && !disconnected) {
                                Icon(Icons.Default.PlayCircle, "播放", Modifier.size(48.dp), tint = if (videoUrl.isEmpty() || disconnected) Color.Gray else MaterialTheme.colorScheme.primary)
                            }
                        }

                        Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
                    }

                    when (syncState) {
                        is PlayerViewModel.SyncState.Connected -> Text("已连接", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
                        is PlayerViewModel.SyncState.Disconnected -> Text("连接断开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
                        is PlayerViewModel.SyncState.RoomClosed -> {
                            Text("房间已关闭", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("返回首页") }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    if (showSyncResult && syncOffset != 0.0) {
        val offsetText = if (kotlin.math.abs(syncOffset) > 0.5) "偏差 ${String.format("%.1f", syncOffset)}秒，已自动纠正" else "同步正常，偏差 ${String.format("%.1f", syncOffset)}秒"
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(3000); showSyncResult = false }
        AlertDialog(onDismissRequest = { showSyncResult = false }, title = { Text("同步结果") }, text = { Text(offsetText) }, confirmButton = { TextButton(onClick = { showSyncResult = false }) { Text("确定") } })
    }

    if (showVideoDialog && isHost) {
        var input by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showVideoDialog = false }, title = { Text("修改视频链接") },
            text = { OutlinedTextField(input, { input = it }, label = { Text("视频URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (input.isNotBlank()) { viewModel.changeVideo(input); showVideoDialog = false } }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showVideoDialog = false }) { Text("取消") } })
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
