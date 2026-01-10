package com.samsung.remote.model

data class SamsungTV(
    val name: String,
    val ip: String,
    val port: Int = 8002,
    val macAddress: String? = null,
    val model: String? = null
) {
    fun getWebSocketUrl(): String {
        return "ws://$ip:$port/api/v2/channels/samsung.remote.control"
    }
}
