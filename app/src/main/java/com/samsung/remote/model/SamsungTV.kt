package com.samsung.remote.model

import com.samsung.remote.network.SamsungTVGeneration
import com.samsung.remote.network.SamsungTVProtocol

data class SamsungTV(
    val name: String,
    val ip: String,
    val port: Int = 8002,
    val macAddress: String? = null,
    val model: String? = null,
    val generation: SamsungTVGeneration? = null,
    val detectedProtocols: List<SamsungTVProtocol>? = null
) {
    fun getWebSocketUrl(): String {
        // Déterminer le protocole basé sur le port
        val protocol = when (port) {
            8002 -> "wss"  // WebSocket Secure
            8001 -> "ws"   // WebSocket
            else -> "ws"   // Fallback
        }
        return "$protocol://$ip:$port/api/v2/channels/samsung.remote.control"
    }

    /**
     * Retourne une description enrichie de la TV avec info de génération
     */
    fun getDetailedDescription(): String {
        return buildString {
            append(name)
            generation?.let {
                append(" [${it.displayName}]")
            }
        }
    }

    /**
     * Retourne un résumé des protocoles détectés
     */
    fun getProtocolSummary(): String? {
        return detectedProtocols?.let { protocols ->
            if (protocols.isEmpty()) {
                null
            } else {
                protocols.joinToString(", ") {
                    "${it.generation.displayName} (${it.port})"
                }
            }
        }
    }
}
