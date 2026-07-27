package com.btv.mvp.data

import android.content.Context
import androidx.core.content.edit

object PrefsManager {
    private const val PREF_NAME = "btv_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val DEFAULT_URL = "http://10.0.2.2:8000"

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit { putString(KEY_SERVER_URL, value) }
}
