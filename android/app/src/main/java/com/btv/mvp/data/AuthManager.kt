package com.btv.mvp.data

import android.content.Context
import androidx.core.content.edit

object AuthManager {
    private const val PREF_NAME = "btv_auth"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_IS_ADMIN = "is_admin"

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    val isLoggedIn: Boolean get() = prefs.getString(KEY_TOKEN, null) != null
    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val username: String? get() = prefs.getString(KEY_USERNAME, null)
    val isAdmin: Boolean get() = prefs.getBoolean(KEY_IS_ADMIN, false)

    fun saveSession(token: String, userId: String, username: String, isAdmin: Boolean) {
        prefs.edit {
            putString(KEY_TOKEN, token)
            putString(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putBoolean(KEY_IS_ADMIN, isAdmin)
        }
    }

    fun logout() {
        prefs.edit { clear() }
    }
}
