package com.samsung.remote.network.protocol

import android.util.Base64
import com.google.gson.Gson
import com.samsung.remote.model.RemoteKey
import com.samsung.remote.util.DebugLogger
import okhttp3.WebSocket

/**
 * Protocole pour les TV Samsung J-Series (2014-2015)
 * Ces TV utilisent WebSocket mais avec un format de message différent
 *
 * Formats testés :
 * 1. CallCommon method (ancienne API)
 * 2. Direct key code sans wrapper
 * 3. Format simplifié
 */
class JSeriesWebSocketProtocol(
    private val webSocket: WebSocket?
) : TVRemoteProtocol {

    private val gson = Gson()
    private var currentFormat = MessageFormat.CALL_COMMON

    companion object {
        private const val TAG = "JSeriesProtocol"
    }

    private enum class MessageFormat {
        CALL_COMMON,     // Méthode CallCommon
        DIRECT_KEY,      // Clé directe
        SIMPLIFIED       // Format simplifié
    }

    override fun getProtocolType(): TVRemoteProtocol.ProtocolType {
        return TVRemoteProtocol.ProtocolType.J_SERIES_WEBSOCKET
    }

    override fun sendKey(key: RemoteKey): Boolean {
        if (webSocket == null) {
            DebugLogger.e(TAG, "WebSocket is null, cannot send key")
            return false
        }

        // Essayer différents formats jusqu'à en trouver un qui fonctionne
        return when (currentFormat) {
            MessageFormat.CALL_COMMON -> sendKeyCallCommon(key)
            MessageFormat.DIRECT_KEY -> sendKeyDirect(key)
            MessageFormat.SIMPLIFIED -> sendKeySimplified(key)
        }
    }

    /**
     * Format CallCommon (API plus ancienne Samsung)
     */
    private fun sendKeyCallCommon(key: RemoteKey): Boolean {
        val message = mapOf(
            "method" to "ms.channel.emit",
            "params" to mapOf(
                "event" to "ed.edenApp.get",
                "to" to "host",
                "data" to mapOf(
                    "key" to key.keyCode
                )
            )
        )

        val json = gson.toJson(message)
        DebugLogger.d(TAG, "Sending key (CallCommon format): $json")

        return try {
            webSocket!!.send(json)
            true
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Error with CallCommon format: ${e.message}")
            // Essayer le format suivant
            currentFormat = MessageFormat.DIRECT_KEY
            false
        }
    }

    /**
     * Format avec clé directe (sans wrapper ms.remote.control)
     */
    private fun sendKeyDirect(key: RemoteKey): Boolean {
        val message = mapOf(
            "Cmd" to "Click",
            "DataOfCmd" to key.keyCode,
            "Option" to "false",
            "TypeOfRemote" to "SendRemoteKey"
        )

        val json = gson.toJson(message)
        DebugLogger.d(TAG, "Sending key (Direct format): $json")

        return try {
            webSocket!!.send(json)
            true
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Error with Direct format: ${e.message}")
            // Essayer le format suivant
            currentFormat = MessageFormat.SIMPLIFIED
            false
        }
    }

    /**
     * Format simplifié (juste la clé)
     */
    private fun sendKeySimplified(key: RemoteKey): Boolean {
        val message = mapOf(
            "key" to key.keyCode
        )

        val json = gson.toJson(message)
        DebugLogger.d(TAG, "Sending key (Simplified format): $json")

        return try {
            webSocket!!.send(json)
            true
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Error with Simplified format: ${e.message}")
            // Retourner au premier format
            currentFormat = MessageFormat.CALL_COMMON
            false
        }
    }

    override fun sendText(text: String): Boolean {
        if (webSocket == null || text.isEmpty()) return false

        // Pour les TV J-Series, envoyer caractère par caractère
        DebugLogger.d(TAG, "Sending text char by char: $text")

        text.forEach { char ->
            val keyCode = charToKeyCode(char)
            if (keyCode != null) {
                val key = RemoteKey.entries.find { it.keyCode == keyCode }
                if (key != null) {
                    sendKey(key)
                    Thread.sleep(50) // Délai entre les caractères
                }
            }
        }

        return true
    }

    /**
     * Convertit un caractère en code de touche
     */
    private fun charToKeyCode(char: Char): String? {
        return when (char.uppercaseChar()) {
            ' ' -> "KEY_SPACE"
            in '0'..'9' -> "KEY_$char"
            in 'A'..'Z' -> "KEY_${char.uppercaseChar()}"
            else -> null
        }
    }

    override fun isSupported(): Boolean {
        // Le protocole J-Series est toujours considéré comme supporté
        // car la connexion WebSocket fonctionne
        return true
    }

    override fun getProtocolName(): String {
        return "J-Series WebSocket (2014-2015) - Format: $currentFormat"
    }

    /**
     * Change le format de message à utiliser
     */
    fun tryNextFormat() {
        currentFormat = when (currentFormat) {
            MessageFormat.CALL_COMMON -> MessageFormat.DIRECT_KEY
            MessageFormat.DIRECT_KEY -> MessageFormat.SIMPLIFIED
            MessageFormat.SIMPLIFIED -> MessageFormat.CALL_COMMON
        }
        DebugLogger.i(TAG, "Switching to format: $currentFormat")
    }
}
