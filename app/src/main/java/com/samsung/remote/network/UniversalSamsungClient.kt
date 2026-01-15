package com.samsung.remote.network

import com.samsung.remote.model.RemoteKey
import com.samsung.remote.model.SamsungTV
import com.samsung.remote.util.DebugLogger
import com.samsung.remote.util.PreferencesManager

/**
 * Client universel qui détecte automatiquement le protocole à utiliser
 * - Port 55000 : Legacy TCP (2011-2014)
 * - Port 8001  : WebSocket (2014-2016)
 * - Port 8002  : WebSocket Secure (2016+)
 */
class UniversalSamsungClient(
    private val tv: SamsungTV,
    private val deviceName: String,
    private val prefsManager: PreferencesManager? = null
) {
    companion object {
        private const val TAG = "UniversalClient"
        private const val PORT_LEGACY = 55000
        private const val PORT_WEBSOCKET = 8001
        private const val PORT_WEBSOCKET_SECURE = 8002
    }

    interface UniversalListener {
        fun onConnected()
        fun onDisconnected()
        fun onError(error: String)
        fun onAuthRequired()
        fun onAuthSuccess()
    }

    private var listener: UniversalListener? = null

    // Clients spécifiques
    private var webSocketClient: SamsungWebSocketClient? = null
    private var legacyClient: SamsungLegacyClient? = null

    private val isLegacyProtocol: Boolean
        get() = tv.port == PORT_LEGACY

    fun setConnectionListener(listener: UniversalListener) {
        this.listener = listener
    }

    /**
     * Connexion avec détection automatique du protocole
     */
    fun connect(token: String? = null) {
        DebugLogger.i(TAG, "=== Détection automatique du protocole ===")
        DebugLogger.i(TAG, "  TV: ${tv.name} (${tv.ip}:${tv.port})")

        when (tv.port) {
            PORT_LEGACY -> {
                DebugLogger.i(TAG, "  → Protocole: Legacy TCP (2011-2014)")
                connectLegacy()
            }
            PORT_WEBSOCKET, PORT_WEBSOCKET_SECURE -> {
                DebugLogger.i(TAG, "  → Protocole: WebSocket (2014+)")
                connectWebSocket(token)
            }
            else -> {
                DebugLogger.w(TAG, "  ⚠ Port inconnu ${tv.port}, tentative WebSocket")
                connectWebSocket(token)
            }
        }
    }

    /**
     * Connexion via WebSocket (ports 8001/8002)
     */
    private fun connectWebSocket(token: String?) {
        webSocketClient = SamsungWebSocketClient(tv, deviceName, prefsManager)
        webSocketClient?.setConnectionListener(object : SamsungWebSocketClient.ConnectionListener {
            override fun onConnected() {
                listener?.onConnected()
            }

            override fun onDisconnected() {
                listener?.onDisconnected()
            }

            override fun onError(error: String) {
                listener?.onError(error)
            }

            override fun onAuthRequired() {
                listener?.onAuthRequired()
            }

            override fun onAuthSuccess() {
                listener?.onAuthSuccess()
            }
        })
        webSocketClient?.connect(token)
    }

    /**
     * Connexion via Legacy TCP (port 55000)
     */
    private fun connectLegacy() {
        legacyClient = SamsungLegacyClient(tv.ip, deviceName)
        legacyClient?.setListener(object : SamsungLegacyClient.LegacyListener {
            override fun onConnected() {
                listener?.onConnected()
            }

            override fun onDisconnected() {
                listener?.onDisconnected()
            }

            override fun onError(error: String) {
                listener?.onError(error)
            }

            override fun onAuthSuccess() {
                // Les TV Legacy n'ont pas de pairing popup
                // Elles acceptent automatiquement la connexion
                listener?.onAuthSuccess()
            }
        })
        legacyClient?.connect()
    }

    /**
     * Envoie une touche (supporte les deux protocoles)
     */
    fun sendKey(key: RemoteKey) {
        when {
            webSocketClient != null -> webSocketClient?.sendKey(key)
            legacyClient != null -> legacyClient?.sendKey(key)
            else -> DebugLogger.w(TAG, "Aucun client actif pour envoyer la touche")
        }
    }

    /**
     * Envoie du texte (supporte les deux protocoles)
     */
    fun sendText(text: String) {
        when {
            webSocketClient != null -> webSocketClient?.sendText(text)
            legacyClient != null -> legacyClient?.sendText(text)
            else -> DebugLogger.w(TAG, "Aucun client actif pour envoyer le texte")
        }
    }

    /**
     * Déconnexion (supporte les deux protocoles)
     */
    fun disconnect() {
        webSocketClient?.disconnect()
        legacyClient?.disconnect()
        webSocketClient = null
        legacyClient = null
    }

    /**
     * Vérifie si connecté (supporte les deux protocoles)
     */
    fun isConnected(): Boolean {
        return when {
            webSocketClient != null -> webSocketClient?.isConnected() ?: false
            legacyClient != null -> legacyClient?.isConnected() ?: false
            else -> false
        }
    }

    /**
     * Obtient le type de protocole utilisé
     */
    fun getProtocolType(): String {
        return when (tv.port) {
            PORT_LEGACY -> "Legacy TCP (2011-2014)"
            PORT_WEBSOCKET -> "WebSocket (2014-2016)"
            PORT_WEBSOCKET_SECURE -> "WebSocket Secure (2016+)"
            else -> "Unknown"
        }
    }
}
