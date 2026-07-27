package com.btv.mvp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.btv.mvp.data.AuthManager
import com.btv.mvp.data.PrefsManager
import com.btv.mvp.ui.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: (isAdmin: Boolean) -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    val baseUrl = PrefsManager.serverUrl

    if (AuthManager.isLoggedIn) {
        LaunchedEffect(Unit) { onLoginSuccess(AuthManager.isAdmin) }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("BTV Sync", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(if (isRegister) "注册新账号" else "登录", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isRegister) viewModel.register(baseUrl, username, password)
                else viewModel.login(baseUrl, username, password)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = username.length >= 2 && password.length >= 3 && uiState !is LoginViewModel.UiState.Loading
        ) {
            if (uiState is LoginViewModel.UiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(if (isRegister) "注册" else "登录")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = { isRegister = !isRegister }) {
            Text(if (isRegister) "已有账号？去登录" else "没有账号？去注册")
        }

        if (uiState is LoginViewModel.UiState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text((uiState as LoginViewModel.UiState.Error).message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}
