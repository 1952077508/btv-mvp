package com.btv.mvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.btv.mvp.data.AppLogger
import com.btv.mvp.data.PrefsManager
import com.btv.mvp.ui.BTVApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsManager.init(applicationContext)
        AppLogger.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            BTVApp()
        }
    }
}
