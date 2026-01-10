package com.samsung.remote.util

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "samsung_remote_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_TV_NAME = "tv_name"
        private const val KEY_TV_IP = "tv_ip"
        private const val KEY_TV_PORT = "tv_port"
        private const val KEY_AUTH_TOKEN = "auth_token"
    }

    fun saveTV(name: String, ip: String, port: Int) {
        prefs.edit().apply {
            putString(KEY_TV_NAME, name)
            putString(KEY_TV_IP, ip)
            putInt(KEY_TV_PORT, port)
            apply()
        }
    }

    fun getSavedTV(): com.samsung.remote.model.SamsungTV? {
        val name = prefs.getString(KEY_TV_NAME, null)
        val ip = prefs.getString(KEY_TV_IP, null)
        val port = prefs.getInt(KEY_TV_PORT, 8002)

        return if (name != null && ip != null) {
            com.samsung.remote.model.SamsungTV(name, ip, port)
        } else {
            null
        }
    }

    fun saveAuthToken(token: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
