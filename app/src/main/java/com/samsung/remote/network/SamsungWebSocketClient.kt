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
    private var authTimeoutRunnable: Runnable? = null

    companion object {
        private const val TAG = "SamsungWebSocket"
        private const val KEEP_ALIVE_INTERVAL = 30000L // 30 seconds
        private const val AUTH_TIMEOUT = 10000L // 10 seconds - wait for TV popup
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
                // Log le message complet pour le debug (sans troncature)
                DebugLogger.d(TAG, "← Message WebSocket reçu:")
                DebugLogger.d(TAG, text)
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                DebugLogger.w(TAG, "⚠ WebSocket en cours de fermeture: $code / $reason")
                stopKeepAlive()
                cancelAuthTimeout()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                DebugLogger.w(TAG, "WebSocket fermé: $code / $reason")
                stopKeepAlive()
                cancelAuthTimeout()
                listener?.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                DebugLogger.e(TAG, "❌ Erreur WebSocket: ${t.message}", t)
                stopKeepAlive()
                cancelAuthTimeout()
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

            DebugLogger.i(TAG, "📩 Event: $event")

            when (event) {
                "ms.channel.connect" -> {
                    val data = response["data"] as? Map<*, *>

                    // Logger toutes les données reçues pour debug
                    DebugLogger.d(TAG, "═══ Données ms.channel.connect ═══")
                    data?.forEach { (key, value) ->
                        DebugLogger.d(TAG, "  $key: $value")
                    }
                    DebugLogger.d(TAG, "═════════════════════════════════")

                    // Vérifier plusieurs emplacements possibles pour le token
                    var token: String? = data?.get("token") as? String

                    // Pour certaines TV, le token peut être dans clients[0].attributes.token
                    if (token == null) {
                        val clients = data?.get("clients") as? List<*>
                        if (clients != null && clients.isNotEmpty()) {
                            val firstClient = clients[0] as? Map<*, *>
                            val attributes = firstClient?.get("attributes") as? Map<*, *>
                            token = attributes?.get("token") as? String
                            if (token != null) {
                                DebugLogger.d(TAG, "Token trouvé dans clients[0].attributes.token")
                            }
                        }
                    }

                    // Pour certaines TV, le token peut être dans data.id (utilisé comme token)
                    if (token == null) {
                        val id = data?.get("id") as? String
                        if (id != null) {
                            DebugLogger.d(TAG, "Pas de token explicite - utilisation de l'ID comme token potentiel")
                            DebugLogger.d(TAG, "ID de session: $id")
                        }
                    }

                    if (token != null && token.isNotEmpty()) {
                        // Save token for future connections
                        cancelAuthTimeout()
                        prefsManager?.saveAuthToken(token)
                        DebugLogger.i(TAG, "✓ Token d'authentification reçu et sauvegardé")
                        DebugLogger.i(TAG, "  Token: ${token.take(20)}...${token.takeLast(10)} (${token.length} chars)")
                        listener?.onAuthSuccess()
                    } else {
                        DebugLogger.w(TAG, "⚠ Aucun token dans ms.channel.connect")
                        DebugLogger.i(TAG, "  → Envoi d'une demande de pairing explicite...")

                        // Send explicit pairing request
                        sendPairingRequest()

                        // Wait 10 seconds for TV to show popup before asking for PIN
                        DebugLogger.i(TAG, "  → Attente de ${AUTH_TIMEOUT/1000}s pour le popup TV...")
                        scheduleAuthTimeout()
                    }
                }

                "ms.channel.ready" -> {
                    // Certaines TV envoient ce message après acceptation
                    DebugLogger.i(TAG, "✓ Canal prêt (ms.channel.ready)")
                    cancelAuthTimeout()
                    val data = response["data"] as? Map<*, *>
                    val token = data?.get("token") as? String

                    if (token != null && token.isNotEmpty()) {
                        prefsManager?.saveAuthToken(token)
                        DebugLogger.i(TAG, "✓ Token reçu dans ms.channel.ready et sauvegardé")
                        listener?.onAuthSuccess()
                    } else {
                        DebugLogger.d(TAG, "ms.channel.ready sans token - connexion établie")
                        // Consider connection established even without explicit token
                        listener?.onAuthSuccess()
                    }
                }

                "ms.channel.clientConnect" -> {
                    // Message quand un client se connecte
                    DebugLogger.i(TAG, "✓ Client connecté (ms.channel.clientConnect)")
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
                    // Logger les données pour debug
                    if (response["data"] != null) {
                        DebugLogger.d(TAG, "  Données: ${response["data"]}")
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "❌ Échec du parsing du message", e)
            DebugLogger.e(TAG, "  Message: $text")
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

    /**
     * Envoie une demande de pairing explicite à la TV
     * Cela peut déclencher l'affichage du popup de pairing sur certaines TV
     */
    private fun sendPairingRequest() {
        try {
            val message = mapOf(
                "method" to "ms.channel.emit",
                "params" to mapOf(
                    "event" to "ms.channel.connect",
                    "to" to "host"
                )
            )

            val json = gson.toJson(message)
            DebugLogger.d(TAG, "→ Envoi demande de pairing explicite")
            DebugLogger.d(TAG, "  Message: $json")
            webSocket?.send(json)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "❌ Erreur lors de l'envoi de la demande de pairing: ${e.message}")
        }
    }

    /**
     * Programme un timeout pour demander le PIN si la TV n'envoie pas de token
     */
    private fun scheduleAuthTimeout() {
        cancelAuthTimeout() // Cancel any existing timeout

        authTimeoutRunnable = Runnable {
            DebugLogger.w(TAG, "⏱ Timeout d'authentification atteint")
            DebugLogger.w(TAG, "  → Aucun popup détecté sur la TV après ${AUTH_TIMEOUT/1000}s")
            DebugLogger.w(TAG, "  → Vérifiez que le contrôle réseau est activé dans les paramètres TV")
            DebugLogger.w(TAG, "  → Pour les TV Transition/Legacy:")
            DebugLogger.w(TAG, "     1. Acceptez la demande de connexion sur l'écran TV")
            DebugLogger.w(TAG, "     2. Le token sera envoyé après validation")
            DebugLogger.w(TAG, "     3. Ou entrez le PIN affiché sur la TV")
            listener?.onAuthRequired()
        }

        keepAliveHandler.postDelayed(authTimeoutRunnable!!, AUTH_TIMEOUT)
        DebugLogger.d(TAG, "⏱ Timeout d'authentification programmé: ${AUTH_TIMEOUT/1000}s")
    }

    /**
     * Annule le timeout d'authentification si la TV répond
     */
    private fun cancelAuthTimeout() {
        authTimeoutRunnable?.let {
            keepAliveHandler.removeCallbacks(it)
            authTimeoutRunnable = null
            DebugLogger.d(TAG, "⏱ Timeout d'authentification annulé")
        }
    }

    fun disconnect() {
        stopKeepAlive()
        cancelAuthTimeout()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    fun isConnected(): Boolean {
        return webSocket != null
    }
}
