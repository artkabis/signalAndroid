package com.samsung.remote.network

import android.util.Base64
import com.samsung.remote.model.RemoteKey
import com.samsung.remote.util.DebugLogger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Client pour le protocole Legacy Samsung (2011-2015)
 * Utilisé par les TV séries D, E, F, H (2011-2014) sur le port 55000
 *
 * Protocole propriétaire TCP Samsung avec encodage base64
 */
class SamsungLegacyClient(
    private val tvIp: String,
    private val deviceName: String = "AndroidRemote",
    private val deviceId: String = "android-remote-app"
) {
    companion object {
        private const val TAG = "SamsungLegacy"
        private const val LEGACY_PORT = 55000
        private const val TIMEOUT_MS = 5000

        // Types de messages Legacy
        private const val MSG_TYPE_AUTH = 0x64.toByte()
        private const val MSG_TYPE_KEY = 0x00.toByte()
        private const val MSG_TYPE_TEXT = 0x01.toByte()
    }

    private var socket: Socket? = null
    private var inputStream: DataInputStream? = null
    private var outputStream: DataOutputStream? = null
    private var isConnected = false

    interface LegacyListener {
        fun onConnected()
        fun onDisconnected()
        fun onError(error: String)
        fun onAuthSuccess()
    }

    private var listener: LegacyListener? = null

    fun setListener(listener: LegacyListener) {
        this.listener = listener
    }

    /**
     * Connexion au protocole Legacy TCP
     */
    fun connect() {
        Thread {
            try {
                DebugLogger.i(TAG, "=== Connexion Legacy TCP ===")
                DebugLogger.i(TAG, "  TV: $tvIp:$LEGACY_PORT")
                DebugLogger.i(TAG, "  Appareil: $deviceName")

                socket = Socket()
                socket?.connect(InetSocketAddress(tvIp, LEGACY_PORT), TIMEOUT_MS)
                socket?.soTimeout = TIMEOUT_MS

                inputStream = DataInputStream(socket?.getInputStream())
                outputStream = DataOutputStream(socket?.getOutputStream())

                DebugLogger.i(TAG, "✓ Socket TCP connecté")

                // Envoyer le message d'authentification
                sendAuthMessage()

                isConnected = true
                listener?.onConnected()

                // La TV Legacy accepte automatiquement après auth
                // Pas de popup de pairing sur les TV anciennes
                listener?.onAuthSuccess()

                DebugLogger.i(TAG, "✓ Authentification Legacy réussie")

            } catch (e: Exception) {
                DebugLogger.e(TAG, "❌ Erreur connexion Legacy: ${e.message}", e)
                listener?.onError(e.message ?: "Connection error")
                disconnect()
            }
        }.start()
    }

    /**
     * Envoie le message d'authentification au format Legacy
     * Format: [Type][AppName_Length][AppName][DeviceId_Length][DeviceId]
     */
    private fun sendAuthMessage() {
        try {
            val appNameBytes = deviceName.toByteArray(StandardCharsets.UTF_8)
            val deviceIdBytes = deviceId.toByteArray(StandardCharsets.UTF_8)

            // Encoder en base64
            val appNameBase64 = Base64.encodeToString(appNameBytes, Base64.NO_WRAP)
            val deviceIdBase64 = Base64.encodeToString(deviceIdBytes, Base64.NO_WRAP)

            // Construire le message
            val appNameB64Bytes = appNameBase64.toByteArray(StandardCharsets.UTF_8)
            val deviceIdB64Bytes = deviceIdBase64.toByteArray(StandardCharsets.UTF_8)

            val message = ByteArray(1 + 2 + appNameB64Bytes.size + 2 + deviceIdB64Bytes.size)
            var offset = 0

            // Type de message (AUTH)
            message[offset++] = MSG_TYPE_AUTH

            // Longueur du nom de l'app (2 bytes, big-endian)
            message[offset++] = (appNameB64Bytes.size shr 8).toByte()
            message[offset++] = (appNameB64Bytes.size and 0xFF).toByte()

            // Nom de l'app (base64)
            System.arraycopy(appNameB64Bytes, 0, message, offset, appNameB64Bytes.size)
            offset += appNameB64Bytes.size

            // Longueur du device ID (2 bytes, big-endian)
            message[offset++] = (deviceIdB64Bytes.size shr 8).toByte()
            message[offset++] = (deviceIdB64Bytes.size and 0xFF).toByte()

            // Device ID (base64)
            System.arraycopy(deviceIdB64Bytes, 0, message, offset, deviceIdB64Bytes.size)

            DebugLogger.d(TAG, "→ Envoi message d'authentification Legacy (${message.size} bytes)")
            outputStream?.write(message)
            outputStream?.flush()

        } catch (e: Exception) {
            DebugLogger.e(TAG, "❌ Erreur envoi auth Legacy: ${e.message}")
            throw e
        }
    }

    /**
     * Envoie une touche au format Legacy
     * Format: [Type][Key_Length][Key]
     */
    fun sendKey(key: RemoteKey) {
        if (!isConnected) {
            DebugLogger.w(TAG, "⚠ Pas connecté, impossible d'envoyer la touche")
            return
        }

        Thread {
            try {
                val keyCodeBytes = key.keyCode.toByteArray(StandardCharsets.UTF_8)
                val keyCodeBase64 = Base64.encodeToString(keyCodeBytes, Base64.NO_WRAP)
                val keyCodeB64Bytes = keyCodeBase64.toByteArray(StandardCharsets.UTF_8)

                val message = ByteArray(1 + 2 + keyCodeB64Bytes.size)
                var offset = 0

                // Type de message (KEY)
                message[offset++] = MSG_TYPE_KEY

                // Longueur de la touche (2 bytes, big-endian)
                message[offset++] = (keyCodeB64Bytes.size shr 8).toByte()
                message[offset++] = (keyCodeB64Bytes.size and 0xFF).toByte()

                // Touche (base64)
                System.arraycopy(keyCodeB64Bytes, 0, message, offset, keyCodeB64Bytes.size)

                DebugLogger.d(TAG, "→ Envoi touche Legacy: ${key.keyCode}")
                outputStream?.write(message)
                outputStream?.flush()

            } catch (e: Exception) {
                DebugLogger.e(TAG, "❌ Erreur envoi touche Legacy: ${e.message}")
            }
        }.start()
    }

    /**
     * Envoie du texte au format Legacy
     * Format: [Type][Text_Length][Text]
     */
    fun sendText(text: String) {
        if (!isConnected) {
            DebugLogger.w(TAG, "⚠ Pas connecté, impossible d'envoyer le texte")
            return
        }

        Thread {
            try {
                val textBytes = text.toByteArray(StandardCharsets.UTF_8)
                val textBase64 = Base64.encodeToString(textBytes, Base64.NO_WRAP)
                val textB64Bytes = textBase64.toByteArray(StandardCharsets.UTF_8)

                val message = ByteArray(1 + 2 + textB64Bytes.size)
                var offset = 0

                // Type de message (TEXT)
                message[offset++] = MSG_TYPE_TEXT

                // Longueur du texte (2 bytes, big-endian)
                message[offset++] = (textB64Bytes.size shr 8).toByte()
                message[offset++] = (textB64Bytes.size and 0xFF).toByte()

                // Texte (base64)
                System.arraycopy(textB64Bytes, 0, message, offset, textB64Bytes.size)

                DebugLogger.d(TAG, "→ Envoi texte Legacy: \"$text\"")
                outputStream?.write(message)
                outputStream?.flush()

            } catch (e: Exception) {
                DebugLogger.e(TAG, "❌ Erreur envoi texte Legacy: ${e.message}")
            }
        }.start()
    }

    /**
     * Déconnexion
     */
    fun disconnect() {
        try {
            isConnected = false
            inputStream?.close()
            outputStream?.close()
            socket?.close()
            listener?.onDisconnected()
            DebugLogger.i(TAG, "Déconnecté du protocole Legacy")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Erreur lors de la déconnexion Legacy: ${e.message}")
        }
    }

    fun isConnected(): Boolean = isConnected
}
