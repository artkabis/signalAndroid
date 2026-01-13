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
        // Port 8001 = ws:// (HTTP WebSocket, older TVs)
        // Port 8002 = wss:// (HTTPS WebSocket Secure, newer TVs 2016+)
        return if (port == 8002) {
            "wss://$ip:$port/api/v2/channels/samsung.remote.control"
        } else {
            "ws://$ip:$port/api/v2/channels/samsung.remote.control"
        }
    }
}
