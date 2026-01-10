package com.samsung.remote.network

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.samsung.remote.model.RemoteKey
import okhttp3.*
import java.util.concurrent.TimeUnit

class SamsungWebSocketClient(
    private val tv: com.samsung.remote.model.SamsungTV,
    private val deviceName: String = "AndroidRemote"
) {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "SamsungWebSocket"
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

        val url = if (token != null) {
            "${tv.getWebSocketUrl()}?name=$nameEncoded&token=$token"
        } else {
            "${tv.getWebSocketUrl()}?name=$nameEncoded"
        }

        Log.d(TAG, "Connecting to: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                listener?.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message received: $text")
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                listener?.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                listener?.onError(t.message ?: "Unknown error")
            }
        })
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
                        listener?.onAuthSuccess()
                    } else {
                        listener?.onAuthRequired()
                    }
                }
                "ms.channel.unauthorized" -> {
                    listener?.onAuthRequired()
                }
                "ms.error" -> {
                    val data = response["data"] as? Map<*, *>
                    val message = data?.get("message") as? String
                    listener?.onError(message ?: "Unknown error from TV")
                }
            }
        } catch (e: Exception) {
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
