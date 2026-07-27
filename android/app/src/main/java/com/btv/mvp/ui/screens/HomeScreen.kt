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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.btv.mvp.data.*
import com.btv.mvp.ui.viewmodel.HomeViewModel
import com.btv.mvp.ui.viewmodel.RoomHistoryEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (roomId: String, userId: String, isHost: Boolean) -> Unit,
    onNavToAdmin: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val roomCode by viewModel.roomCode.collectAsState()
    val roomHistory by viewModel.roomHistory.collectAsState()

    val baseUrl = remember { mutableStateOf(PrefsManager.serverUrl) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showDiagDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var diagReport by remember { mutableStateOf<FullDiagReport?>(null) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is HomeViewModel.UiState.RoomCreated -> onNavigateToPlayer(state.roomId, state.userId, true)
            is HomeViewModel.UiState.RoomJoined -> onNavigateToPlayer(state.roomId, state.userId, false)
            is HomeViewModel.UiState.DiagCompleted -> { diagReport = state.report; showDiagDialog = true }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AuthManager.username ?: "BTV Sync") },
                actions = {
                    if (AuthManager.isAdmin) IconButton(onClick = onNavToAdmin) { Icon(Icons.Default.Dashboard, "管理") }
                    IconButton(onClick = { showLogDialog = true }) { Icon(Icons.Default.BugReport, "日志") }
                    IconButton(onClick = { showServerDialog = true }) { Icon(Icons.Default.Settings, "设置") }
                    IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "退出") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.height(24.dp))
                Text("同步观影助手", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp))
            }

            item {
                Button(
                    onClick = { viewModel.createRoom(baseUrl.value) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = uiState !is HomeViewModel.UiState.Loading
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("创建房间", fontSize = 16.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            item {
                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { viewModel.updateRoomCode(it) },
                    label = { Text("输入房间码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 22.sp, letterSpacing = 6.sp)
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                Button(
                    onClick = { viewModel.joinRoom(baseUrl.value, roomCode) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled = roomCode.length == 6 && uiState !is HomeViewModel.UiState.Loading
                ) {
                    Text("加入房间", fontSize = 15.sp)
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.runDiagnostics(baseUrl.value) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState !is HomeViewModel.UiState.Loading
                ) {
                    Icon(Icons.Default.NetworkCheck, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("网络诊断", fontSize = 13.sp)
                }
            }

            if (uiState is HomeViewModel.UiState.Loading) item { Spacer(Modifier.height(8.dp)); CircularProgressIndicator() }
            if (uiState is HomeViewModel.UiState.Error) item { Spacer(Modifier.height(8.dp)); Text((uiState as HomeViewModel.UiState.Error).message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

            if (roomHistory.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("历史房间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }

                items(roomHistory) { entry ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.roomId, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp)
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(entry.roomId)) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.ContentCopy, "复制", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text("${if (entry.role == "host") "房主" else "访客"} · ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.joinedAt))}",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                if (entry.role == "host") viewModel.createRoom(baseUrl.value)
                                else viewModel.joinRoom(baseUrl.value, entry.roomId)
                            }) { Icon(Icons.Default.Login, "加入", tint = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = { viewModel.deleteHistory(entry.roomId) }) { Icon(Icons.Default.Delete, "删除", Modifier.size(18.dp), tint = Color.Gray) }
                        }
                    }
                }
            }
        }
    }

    // Server dialog
    if (showServerDialog) {
        var url by remember { mutableStateOf(baseUrl.value) }
        AlertDialog(onDismissRequest = { showServerDialog = false }, title = { Text("服务器地址") },
            text = { OutlinedTextField(url, { url = it }, label = { Text("http://IP:端口") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { baseUrl.value = url.trimEnd('/'); PrefsManager.serverUrl = baseUrl.value; showServerDialog = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showServerDialog = false }) { Text("取消") } })
    }

    // Diag dialog
    if (showDiagDialog && diagReport != null) {
        val r = diagReport!!
        AlertDialog(onDismissRequest = { showDiagDialog = false }, title = { Text("网络诊断: ${r.host}:${r.port}") }, text = {
            Column {
                Text("状态: ${r.overallHealth}", fontWeight = FontWeight.Bold, color = if (r.overallHealth == "全部通过") Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                r.results.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (d.success) Icons.Default.CheckCircle else Icons.Default.Cancel, null, Modifier.size(16.dp), tint = if (d.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(d.step, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("${d.detail} (${d.durationMs}ms)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }, confirmButton = { TextButton(onClick = { showDiagDialog = false }) { Text("关闭") } })
    }

    // Log dialog
    if (showLogDialog) {
        var logEntries by remember { mutableStateOf(AppLogger.logs.reversed()) }
        val clip = LocalClipboardManager.current
        LaunchedEffect(showLogDialog) { logEntries = AppLogger.logs.reversed() }
        AlertDialog(onDismissRequest = { showLogDialog = false }, title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("日志 (${logEntries.size})")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { clip.setText(AnnotatedString(AppLogger.logs.joinToString("\n") { it.formatted() })) }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp)); Spacer(Modifier.width(2.dp)); Text("复制", fontSize = 12.sp)
                }
                TextButton(onClick = { AppLogger.clear(); logEntries = emptyList() }) { Text("清空", fontSize = 12.sp) }
            }
        }, text = {
            if (logEntries.isEmpty()) Text("暂无日志")
            else LazyColumn(Modifier.height(380.dp)) {
                items(logEntries) { e ->
                    Text(e.formatted(), style = MaterialTheme.typography.bodySmall, color = when (e.level) {
                        AppLogger.Level.ERROR -> Color(0xFFEF5350); AppLogger.Level.WARN -> Color(0xFFFFA726)
                        else -> MaterialTheme.colorScheme.onSurface
                    }, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }, confirmButton = { TextButton(onClick = { showLogDialog = false }) { Text("关闭") } })
    }
}
