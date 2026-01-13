package com.samsung.remote.network.protocol

import android.util.Base64
import com.google.gson.Gson
import com.samsung.remote.model.RemoteKey
import com.samsung.remote.util.DebugLogger
import okhttp3.WebSocket

/**
 * Protocole moderne pour les TV Samsung 2016+
 * Utilise WebSocket avec la méthode "ms.remote.control"
 */
class ModernWebSocketProtocol(
    private val webSocket: WebSocket?
) : TVRemoteProtocol {

    private val gson = Gson()
    private var isSupported = true

    companion object {
        private const val TAG = "ModernProtocol"
    }

    override fun getProtocolType(): TVRemoteProtocol.ProtocolType {
        return TVRemoteProtocol.ProtocolType.MODERN_WEBSOCKET
    }

    override fun sendKey(key: RemoteKey): Boolean {
        if (webSocket == null) {
            DebugLogger.e(TAG, "WebSocket is null, cannot send key")
            return false
        }

        val message = mapOf(
            "method" to "ms.remote.control",
            "params" to mapOf(
                "Cmd" to "Click",
                "DataOfCmd" to key.keyCode,
                "Option" to "false",
                "TypeOfRemote" to "SendRemoteKey"
            )
        )

        val json = gson.toJson(message)
        DebugLogger.d(TAG, "Sending key (modern protocol): $json")

        return try {
            webSocket.send(json)
            true
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Error sending key: ${e.message}")
            false
        }
    }

    override fun sendText(text: String): Boolean {
        if (webSocket == null || text.isEmpty()) return false

        val encodedText = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
        val message = mapOf(
            "method" to "ms.remote.control",
            "params" to mapOf(
                "Cmd" to "SendInputString",
                "DataOfCmd" to encodedText,
                "TypeOfRemote" to "SendInputString"
            )
        )

        val json = gson.toJson(message)
        DebugLogger.d(TAG, "Sending text (modern protocol): $text")

        return try {
            webSocket.send(json)
            true
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Error sending text: ${e.message}")
            false
        }
    }

    override fun isSupported(): Boolean {
        return isSupported
    }

    override fun getProtocolName(): String {
        return "Modern WebSocket (2016+)"
    }

    /**
     * Marque ce protocole comme non supporté par la TV
     */
    fun markAsUnsupported() {
        isSupported = false
        DebugLogger.w(TAG, "Modern protocol marked as unsupported by TV")
    }
}
