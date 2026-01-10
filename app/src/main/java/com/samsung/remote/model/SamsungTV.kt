package com.samsung.remote.model

data class SamsungTV(
    val name: String,
    val ip: String,
    val port: Int = 8002,
    val macAddress: String? = null,
    val model: String? = null
) {
    fun getWebSocketUrl(): String {
        // Samsung Smart TV WebSocket API endpoint
        // Format: ws://IP:PORT/api/v2/channels/samsung.remote.control
        // Port 8001 = HTTP WebSocket (older TVs)
        // Port 8002 = HTTPS WebSocket (newer TVs, 2016+)
        return "ws://$ip:$port/api/v2/channels/samsung.remote.control"
    }

    fun getWebSocketUrlSecure(): String {
        // For TVs that require secure WebSocket (wss://)
        return "wss://$ip:$port/api/v2/channels/samsung.remote.control"
    }
}
