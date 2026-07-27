package com.btv.mvp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.btv.mvp.data.AppLogger
import com.btv.mvp.data.FullDiagReport
import com.btv.mvp.data.PrefsManager
import com.btv.mvp.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (roomId: String, userId: String, isHost: Boolean) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val roomCode by viewModel.roomCode.collectAsState()

    val baseUrl = remember { mutableStateOf(PrefsManager.serverUrl) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showDiagDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var diagReport by remember { mutableStateOf<FullDiagReport?>(null) }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is HomeViewModel.UiState.RoomCreated -> {
                onNavigateToPlayer(state.roomId, state.userId, true)
            }
            is HomeViewModel.UiState.RoomJoined -> {
                onNavigateToPlayer(state.roomId, state.userId, false)
            }
            is HomeViewModel.UiState.DiagCompleted -> {
                diagReport = state.report
                showDiagDialog = true
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BTV Sync") },
                actions = {
                    IconButton(onClick = { showLogDialog = true }) {
                        Icon(Icons.Default.BugReport, contentDescription = "查看日志")
                    }
                    IconButton(onClick = { showServerDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "服务器设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "同步观影助手",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { viewModel.createRoom(baseUrl.value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = uiState !is HomeViewModel.UiState.Loading
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建房间", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = roomCode,
                onValueChange = { viewModel.updateRoomCode(it) },
                label = { Text("输入房间码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii
                ),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp,
                    letterSpacing = 8.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.joinRoom(baseUrl.value, roomCode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = roomCode.length == 6 && uiState !is HomeViewModel.UiState.Loading
            ) {
                Text("加入房间", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.runDiagnostics(baseUrl.value) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is HomeViewModel.UiState.Loading
            ) {
                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("网络诊断", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState is HomeViewModel.UiState.Loading) {
                CircularProgressIndicator()
            }

            if (uiState is HomeViewModel.UiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (uiState as HomeViewModel.UiState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showServerDialog) {
        var serverUrl by remember { mutableStateOf(baseUrl.value) }
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("服务器地址") },
            text = {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("http://IP:端口") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    baseUrl.value = serverUrl.trimEnd('/')
                    PrefsManager.serverUrl = baseUrl.value
                    showServerDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDiagDialog && diagReport != null) {
        val report = diagReport!!
        AlertDialog(
            onDismissRequest = { showDiagDialog = false },
            title = { Text("网络诊断: ${report.host}:${report.port}") },
            text = {
                Column {
                    Text(
                        "状态: ${report.overallHealth}",
                        fontWeight = FontWeight.Bold,
                        color = when (report.overallHealth) {
                            "全部通过" -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    report.results.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (r.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (r.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(r.step, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "${r.detail} (${r.durationMs}ms)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    if (showLogDialog) {
        var logEntries by remember { mutableStateOf(AppLogger.logs.reversed()) }
        val clipboardManager = LocalClipboardManager.current
        LaunchedEffect(showLogDialog) {
            logEntries = AppLogger.logs.reversed()
        }
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("应用日志 (${logEntries.size})")
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val text = AppLogger.logs.joinToString("\n") { it.formatted() }
                        clipboardManager.setText(AnnotatedString(text))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制", fontSize = 12.sp)
                    }
                    TextButton(onClick = { AppLogger.clear(); logEntries = emptyList() }) {
                        Text("清空", fontSize = 12.sp)
                    }
                }
            },
            text = {
                if (logEntries.isEmpty()) {
                    Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.height(400.dp)) {
                        items(logEntries) { entry ->
                            val color = when (entry.level) {
                                AppLogger.Level.ERROR -> Color(0xFFEF5350)
                                AppLogger.Level.WARN -> Color(0xFFFFA726)
                                AppLogger.Level.INFO -> MaterialTheme.colorScheme.onSurface
                                AppLogger.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = entry.formatted(),
                                style = MaterialTheme.typography.bodySmall,
                                color = color,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
