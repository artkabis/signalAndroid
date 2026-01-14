package com.samsung.remote.network

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.samsung.remote.model.RemoteKey
import com.samsung.remote.util.DebugLogger
import com.samsung.remote.util.PreferencesManager
import okhttp3.*
import java.util.concurrent.TimeUnit

class SamsungWebSocketClient(
    private val tv: com.samsung.remote.model.SamsungTV,
    private val deviceName: String = "AndroidRemote",
    private val prefsManager: PreferencesManager? = null
) {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val keepAliveHandler = Handler(Looper.getMainLooper())
    private var keepAliveRunnable: Runnable? = null

    companion object {
        private const val TAG = "SamsungWebSocket"
        private const val KEEP_ALIVE_INTERVAL = 30000L // 30 seconds
    }

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onError(error: String)
        fun onAuthRequired()
        fun onAuthSuccess()
    }

    private var listener: ConnectionListener? = null

    fun setConnectionListener(listener: ConnectionListener) {
        this.listener = listener
    }

    fun connect(token: String? = null) {
        val nameEncoded = Base64.encodeToString(deviceName.toByteArray(), Base64.NO_WRAP)

        // Use provided token, or try to load saved token
        val authToken = token ?: prefsManager?.getAuthToken()

        val url = if (authToken != null) {
            DebugLogger.d(TAG, "✓ Utilisation du token d'authentification sauvegardé")
            "${tv.getWebSocketUrl()}?name=$nameEncoded&token=$authToken"
        } else {
            DebugLogger.d(TAG, "Pas de token disponible, nouveau pairing requis")
            "${tv.getWebSocketUrl()}?name=$nameEncoded"
        }

        DebugLogger.i(TAG, "Connexion à: ${tv.name} (${tv.ip}:${tv.port})")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                DebugLogger.i(TAG, "✓ WebSocket ouvert avec succès")
                startKeepAlive()
                listener?.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                DebugLogger.d(TAG, "← Message reçu: ${text.take(100)}${if (text.length > 100) "..." else ""}")
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                DebugLogger.w(TAG, "⚠ WebSocket en cours de fermeture: $code / $reason")
                stopKeepAlive()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                DebugLogger.w(TAG, "WebSocket fermé: $code / $reason")
                stopKeepAlive()
                listener?.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                DebugLogger.e(TAG, "❌ Erreur WebSocket: ${t.message}", t)
                stopKeepAlive()
                listener?.onError(t.message ?: "Unknown error")
            }
        })
    }

    private fun startKeepAlive() {
        stopKeepAlive() // Stop any existing keep-alive first

        keepAliveRunnable = object : Runnable {
            override fun run() {
                if (isConnected()) {
                    DebugLogger.v(TAG, "♥ Keep-alive ping (connexion active)")
                    // Send a simple ping to keep connection alive
                    webSocket?.let { ws ->
                        try {
                            // OkHttp will handle ping/pong automatically
                            // We just log that the connection is still active
                        } catch (e: Exception) {
                            DebugLogger.e(TAG, "❌ Échec du keep-alive", e)
                        }
                    }
                    keepAliveHandler.postDelayed(this, KEEP_ALIVE_INTERVAL)
                }
            }
        }

        keepAliveHandler.postDelayed(keepAliveRunnable!!, KEEP_ALIVE_INTERVAL)
        DebugLogger.d(TAG, "Keep-alive démarré (intervalle: ${KEEP_ALIVE_INTERVAL/1000}s)")
    }

    private fun stopKeepAlive() {
        keepAliveRunnable?.let {
            keepAliveHandler.removeCallbacks(it)
            keepAliveRunnable = null
            DebugLogger.d(TAG, "Keep-alive arrêté")
        }
    }

    private fun handleMessage(text: String) {
        try {
            val response = gson.fromJson(text, Map::class.java)
            val event = response["event"] as? String

            when (event) {
                "ms.channel.connect" -> {
                    val data = response["data"] as? Map<*, *>
                    val token = data?.get("token") as? String

                    if (token != null) {
                        // Save token for future connections
                        prefsManager?.saveAuthToken(token)
                        DebugLogger.i(TAG, "✓ Token d'authentification reçu et sauvegardé")
                        listener?.onAuthSuccess()
                    } else {
                        DebugLogger.w(TAG, "⚠ Aucun token reçu, authentification requise")
                        listener?.onAuthRequired()
                    }
                }
                "ms.channel.unauthorized" -> {
                    DebugLogger.w(TAG, "⚠ Non autorisé - nouvelle authentification requise")
                    listener?.onAuthRequired()
                }
                "ms.error" -> {
                    val data = response["data"] as? Map<*, *>
                    val message = data?.get("message") as? String
                    DebugLogger.e(TAG, "❌ Erreur de la TV: ${message ?: "Inconnue"}")
                    listener?.onError(message ?: "Unknown error from TV")
                }
                else -> {
                    DebugLogger.d(TAG, "Event non géré: $event")
                }
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "❌ Échec du parsing du message", e)
        }
    }

    fun sendKey(key: RemoteKey) {
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
        DebugLogger.d(TAG, "→ Envoi touche: ${key.keyCode}")
        webSocket?.send(json)
    }

    /**
     * Envoie du texte à la TV Samsung
     * Utilise la commande SendInputString (plus rapide et efficace)
     */
    fun sendText(text: String, useDirectMethod: Boolean = true) {
        if (text.isEmpty()) return

        if (useDirectMethod) {
            // Méthode 1 : SendInputString (Samsung 2016+)
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
            DebugLogger.i(TAG, "→ Envoi texte (SendInputString): \"$text\"")
            webSocket?.send(json)
        } else {
            // Méthode 2 : Envoyer caractère par caractère (fallback)
            DebugLogger.d(TAG, "→ Envoi texte (char par char fallback): \"$text\"")
            sendTextCharByChar(text)
        }
    }

    /**
     * Envoie le texte caractère par caractère
     * Méthode plus lente mais compatible avec toutes les TV
     */
    private fun sendTextCharByChar(text: String) {
        DebugLogger.d(TAG, "Envoi caractère par caractère (${text.length} chars): \"$text\"")

        text.forEach { char ->
            val keyCode = when (char.uppercaseChar()) {
                ' ' -> "KEY_SPACE"
                'A' -> "KEY_A"
                'B' -> "KEY_B"
                'C' -> "KEY_C"
                'D' -> "KEY_D"
                'E' -> "KEY_E"
                'F' -> "KEY_F"
                'G' -> "KEY_G"
                'H' -> "KEY_H"
                'I' -> "KEY_I"
                'J' -> "KEY_J"
                'K' -> "KEY_K"
                'L' -> "KEY_L"
                'M' -> "KEY_M"
                'N' -> "KEY_N"
                'O' -> "KEY_O"
                'P' -> "KEY_P"
                'Q' -> "KEY_Q"
                'R' -> "KEY_R"
                'S' -> "KEY_S"
                'T' -> "KEY_T"
                'U' -> "KEY_U"
                'V' -> "KEY_V"
                'W' -> "KEY_W"
                'X' -> "KEY_X"
                'Y' -> "KEY_Y"
                'Z' -> "KEY_Z"
                '0' -> "KEY_0"
                '1' -> "KEY_1"
                '2' -> "KEY_2"
                '3' -> "KEY_3"
                '4' -> "KEY_4"
                '5' -> "KEY_5"
                '6' -> "KEY_6"
                '7' -> "KEY_7"
                '8' -> "KEY_8"
                '9' -> "KEY_9"
                else -> null
            }

            if (keyCode != null) {
                val message = mapOf(
                    "method" to "ms.remote.control",
                    "params" to mapOf(
                        "Cmd" to "Click",
                        "DataOfCmd" to keyCode,
                        "Option" to "false",
                        "TypeOfRemote" to "SendRemoteKey"
                    )
                )
                webSocket?.send(gson.toJson(message))

                // Petit délai pour éviter de surcharger la TV
                Thread.sleep(50)
            }
        }
    }

    fun disconnect() {
        stopKeepAlive()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    fun isConnected(): Boolean {
        return webSocket != null
    }
}
