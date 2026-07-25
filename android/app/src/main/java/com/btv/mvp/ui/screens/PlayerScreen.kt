package com.btv.mvp.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
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
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val videoUrl by viewModel.videoUrl.collectAsState()
    val syncOffset by viewModel.syncOffset.collectAsState()

    var showVideoDialog by remember { mutableStateOf(false) }
    var showSyncResult by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initialize(roomId, userId, isHost, "http://10.0.2.2:8000")
    }

    LaunchedEffect(syncState) {
        if (syncState is PlayerViewModel.SyncState.Synced) {
            showSyncResult = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.disconnect() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("房间: $roomId") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.disconnect()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isHost) {
                        IconButton(onClick = { showVideoDialog = true }) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = "切换视频")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            player = viewModel.exoPlayer.getPlayer()
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (videoUrl.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHost) "请输入视频链接" else "等待房主添加视频...",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (playbackState is PlayerViewModel.PlaybackState.Loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = formatDuration(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { fraction ->
                            val targetMs = (fraction * duration).toLong()
                            viewModel.seekTo(targetMs)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.requestSync() }) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "同步",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (playbackState is PlayerViewModel.PlaybackState.Playing) {
                            IconButton(onClick = { viewModel.pause() }) {
                                Icon(
                                    Icons.Default.PauseCircle,
                                    contentDescription = "暂停",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            IconButton(onClick = { viewModel.play() }) {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = "播放",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    when (val state = syncState) {
                        is PlayerViewModel.SyncState.Connected -> {
                            Text(
                                text = "已连接",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                        is PlayerViewModel.SyncState.Disconnected -> {
                            Text(
                                text = "连接断开",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    if (showSyncResult && syncOffset != 0.0) {
        val offsetText = if (kotlin.math.abs(syncOffset) > 0.5) {
            "偏差 ${String.format("%.1f", syncOffset)}秒，已自动纠正"
        } else {
            "同步正常，偏差 ${String.format("%.1f", syncOffset)}秒"
        }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(3000)
            showSyncResult = false
        }
        AlertDialog(
            onDismissRequest = { showSyncResult = false },
            title = { Text("同步结果") },
            text = { Text(offsetText) },
            confirmButton = {
                TextButton(onClick = { showSyncResult = false }) {
                    Text("确定")
                }
            }
        )
    }

    if (showVideoDialog && isHost) {
        var videoInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showVideoDialog = false },
            title = { Text("修改视频链接") },
            text = {
                OutlinedTextField(
                    value = videoInput,
                    onValueChange = { videoInput = it },
                    label = { Text("视频URL") },
                    placeholder = { Text("https://example.com/video.mp4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (videoInput.isNotBlank()) {
                            viewModel.changeVideo(videoInput)
                            showVideoDialog = false
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVideoDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
