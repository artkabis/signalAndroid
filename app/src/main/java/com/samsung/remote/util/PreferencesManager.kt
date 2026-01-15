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
        private const val KEY_DEVICE_UUID = "device_uuid"
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

    /**
     * Obtient ou génère un UUID unique pour cet appareil
     * Format: 4 caractères hexadécimaux (par ex: "a3f2")
     */
    fun getOrCreateDeviceUUID(): String {
        var uuid = prefs.getString(KEY_DEVICE_UUID, null)

        if (uuid == null) {
            // Générer un UUID court (8 caractères hex = 4 bytes)
            uuid = java.util.UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString(KEY_DEVICE_UUID, uuid).apply()
            DebugLogger.i("PreferencesManager", "✓ UUID d'appareil généré: $uuid")
        }

        return uuid
    }

    /**
     * Génère le nom complet de l'appareil pour le pairing TV
     * Format: "AndroidRemote-{uuid}" (par ex: "AndroidRemote-a3f2b1c4")
     */
    fun getDeviceName(): String {
        val uuid = getOrCreateDeviceUUID()
        return "AndroidRemote-$uuid"
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
