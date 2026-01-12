package com.samsung.remote.network

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.samsung.remote.model.RemoteKey
import com.samsung.remote.util.DebugLogger
import okhttp3.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class SamsungWebSocketClient(
    private val tv: com.samsung.remote.model.SamsungTV,
    private var deviceName: String = "AndroidRemote"
) {

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient
    private val gson = Gson()

    companion object {
        private const val TAG = "SamsungWebSocket"
    }

    init {
        // Create OkHttpClient with support for self-signed certificates (Samsung TVs use self-signed certs)
        client = try {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Erreur lors de la configuration SSL: ${e.message}")
            // Fallback to regular client
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }
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
        DebugLogger.i(TAG, "=== Démarrage de la connexion WebSocket ===")
        val nameEncoded = Base64.encodeToString(deviceName.toByteArray(), Base64.NO_WRAP)

        val url = if (token != null) {
            "${tv.getWebSocketUrl()}?name=$nameEncoded&token=$token"
        } else {
            "${tv.getWebSocketUrl()}?name=$nameEncoded"
        }

        val protocol = if (tv.port == 8002) "wss:// (sécurisé)" else "ws:// (non-sécurisé)"
        DebugLogger.i(TAG, "URL WebSocket:")
        DebugLogger.i(TAG, "  • Protocole: $protocol")
        DebugLogger.i(TAG, "  • URL complète: $url")
        DebugLogger.i(TAG, "  • IP TV: ${tv.ip}")
        DebugLogger.i(TAG, "  • Port: ${tv.port}")
        DebugLogger.i(TAG, "  • Nom appareil: $deviceName (encodé: $nameEncoded)")
        if (token != null) {
            DebugLogger.d(TAG, "  • Token fourni: ${token.take(20)}...")
        } else {
            DebugLogger.d(TAG, "  • Pas de token (nouvelle connexion)")
        }

        Log.d(TAG, "Connecting to: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        DebugLogger.d(TAG, "→ Création de la connexion WebSocket...")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                DebugLogger.i(TAG, "✅ WebSocket ouvert avec succès!")
                DebugLogger.d(TAG, "  • Code réponse HTTP: ${response.code}")
                DebugLogger.d(TAG, "  • Message: ${response.message}")
                Log.d(TAG, "WebSocket opened")
                listener?.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                DebugLogger.i(TAG, "📨 Message reçu de la TV:")
                DebugLogger.d(TAG, "  • Contenu: $text")
                Log.d(TAG, "Message received: $text")
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                DebugLogger.w(TAG, "⚠ WebSocket en cours de fermeture:")
                DebugLogger.w(TAG, "  • Code: $code")
                DebugLogger.w(TAG, "  • Raison: $reason")
                Log.d(TAG, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                DebugLogger.w(TAG, "✗ WebSocket fermé:")
                DebugLogger.w(TAG, "  • Code: $code")
                DebugLogger.w(TAG, "  • Raison: $reason")
                Log.d(TAG, "WebSocket closed: $code / $reason")
                listener?.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                DebugLogger.e(TAG, "❌ Échec de la connexion WebSocket!")
                DebugLogger.e(TAG, "  • Erreur: ${t.message}")
                DebugLogger.e(TAG, "  • Type: ${t.javaClass.simpleName}")
                response?.let {
                    DebugLogger.e(TAG, "  • Code HTTP: ${it.code}")
                    DebugLogger.e(TAG, "  • Message HTTP: ${it.message}")
                }
                if (t is java.net.ConnectException) {
                    DebugLogger.e(TAG, "  → Impossible de joindre la TV. Vérifiez:")
                    DebugLogger.e(TAG, "    1. L'IP est correcte (${tv.ip})")
                    DebugLogger.e(TAG, "    2. Le port est correct (${tv.port})")
                    DebugLogger.e(TAG, "    3. La TV est allumée")
                    DebugLogger.e(TAG, "    4. La TV est sur le même réseau")
                } else if (t is java.net.SocketTimeoutException) {
                    DebugLogger.e(TAG, "  → Timeout - La TV ne répond pas")
                }
                Log.e(TAG, "WebSocket error", t)
                listener?.onError(t.message ?: "Unknown error")
            }
        })
        DebugLogger.d(TAG, "Connexion WebSocket lancée")
    }

    private fun handleMessage(text: String) {
        DebugLogger.d(TAG, "→ Traitement du message...")
        try {
            val response = gson.fromJson(text, Map::class.java)
            val event = response["event"] as? String

            DebugLogger.i(TAG, "Événement TV: $event")

            when (event) {
                "ms.channel.connect" -> {
                    DebugLogger.i(TAG, "→ Événement de connexion reçu")
                    val data = response["data"] as? Map<*, *>
                    DebugLogger.d(TAG, "  • Données complètes: $data")

                    val token = data?.get("token") as? String
                    val id = data?.get("id") as? String
                    val clients = data?.get("clients") as? List<*>

                    DebugLogger.i(TAG, "  • ID de connexion: $id")
                    DebugLogger.i(TAG, "  • Nombre de clients connectés: ${clients?.size ?: 0}")

                    // Log detailed info about existing clients
                    if (clients != null && clients.isNotEmpty()) {
                        DebugLogger.w(TAG, "  ⚠ ATTENTION: ${clients.size} client(s) déjà connecté(s):")
                        clients.forEachIndexed { index, client ->
                            val clientMap = client as? Map<*, *>
                            val clientId = clientMap?.get("id") as? String
                            val attributes = clientMap?.get("attributes") as? Map<*, *>
                            val clientNameBase64 = attributes?.get("name") as? String
                            val clientName = try {
                                if (clientNameBase64 != null) {
                                    String(Base64.decode(clientNameBase64, Base64.NO_WRAP))
                                } else null
                            } catch (e: Exception) {
                                clientNameBase64
                            }
                            val isHost = clientMap?.get("isHost") as? Boolean
                            val connectTime = clientMap?.get("connectTime") as? Number

                            DebugLogger.w(TAG, "    Client #${index + 1}:")
                            DebugLogger.w(TAG, "      - ID: $clientId")
                            DebugLogger.w(TAG, "      - Nom: $clientName")
                            DebugLogger.w(TAG, "      - Est hôte: $isHost")
                            DebugLogger.w(TAG, "      - Temps de connexion: $connectTime")
                        }
                        DebugLogger.w(TAG, "  → Ces clients peuvent bloquer l'affichage du PIN")
                        DebugLogger.w(TAG, "  → Solution: Aller dans les paramètres TV > Gestionnaire de périphériques externes")
                        DebugLogger.w(TAG, "  → et supprimer les anciens appareils 'AndroidRemote'")
                    }

                    if (token != null) {
                        DebugLogger.i(TAG, "✅ Token reçu: ${token.take(20)}...")
                        DebugLogger.i(TAG, "Connexion autorisée!")
                        // Save token for future connections
                        listener?.onAuthSuccess()
                    } else {
                        DebugLogger.i(TAG, "🔐 Aucun token - Authentification requise")
                        DebugLogger.i(TAG, "→ Un PIN devrait apparaître sur la TV maintenant")

                        if (clients != null && clients.isNotEmpty()) {
                            DebugLogger.w(TAG, "⚠ MAIS: ${clients.size} clients déjà connectés peuvent empêcher le PIN")
                            DebugLogger.w(TAG, "→ Vérifiez si 'AndroidRemote' est déjà dans la liste des appareils de la TV")
                        }

                        listener?.onAuthRequired()
                    }
                }
                "ms.channel.clientConnect" -> {
                    DebugLogger.i(TAG, "→ Événement: Nouveau client connecté")
                    val data = response["data"] as? Map<*, *>
                    DebugLogger.d(TAG, "  • Données: $data")

                    val clientId = data?.get("id") as? String
                    val attributes = data?.get("attributes") as? Map<*, *>
                    val clientNameBase64 = attributes?.get("name") as? String
                    val clientName = try {
                        if (clientNameBase64 != null) {
                            String(Base64.decode(clientNameBase64, Base64.NO_WRAP))
                        } else null
                    } catch (e: Exception) {
                        clientNameBase64
                    }
                    DebugLogger.i(TAG, "  • Client ID: $clientId")
                    DebugLogger.i(TAG, "  • Client Nom: $clientName")
                }
                "ms.channel.clientDisconnect" -> {
                    DebugLogger.i(TAG, "→ Événement: Client déconnecté")
                    val data = response["data"] as? Map<*, *>
                    val clientId = data?.get("id") as? String
                    DebugLogger.i(TAG, "  • Client ID déconnecté: $clientId")
                }
                "ms.channel.unauthorized" -> {
                    DebugLogger.w(TAG, "⚠ Non autorisé - Authentification requise")
                    listener?.onAuthRequired()
                }
                "ms.error" -> {
                    val data = response["data"] as? Map<*, *>
                    val message = data?.get("message") as? String
                    DebugLogger.e(TAG, "❌ Erreur de la TV: $message")
                    listener?.onError(message ?: "Unknown error from TV")
                }
                else -> {
                    DebugLogger.w(TAG, "⚠ Événement inconnu: $event")
                    DebugLogger.d(TAG, "  • Données complètes: $response")
                }
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "❌ Erreur lors du parsing du message", e)
            Log.e(TAG, "Failed to parse message", e)
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
        Log.d(TAG, "Sending key: $json")
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
            Log.d(TAG, "Sending text (direct): $text")
            webSocket?.send(json)
        } else {
            // Méthode 2 : Envoyer caractère par caractère (fallback)
            sendTextCharByChar(text)
        }
    }

    /**
     * Envoie le texte caractère par caractère
     * Méthode plus lente mais compatible avec toutes les TV
     */
    private fun sendTextCharByChar(text: String) {
        Log.d(TAG, "Sending text char by char: $text")

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
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    fun isConnected(): Boolean {
        return webSocket != null
    }
}
