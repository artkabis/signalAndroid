package com.samsung.remote.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInfoHelper {

    data class NetworkInfo(
        val isConnected: Boolean,
        val isWifi: Boolean,
        val ssid: String?,
        val ipAddress: String?,
        val networkType: String,
        val linkSpeed: Int? = null
    )

    fun getNetworkInfo(context: Context): NetworkInfo {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        var isConnected = false
        var isWifi = false
        var networkType = "NONE"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            if (capabilities != null) {
                isConnected = true
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        isWifi = true
                        networkType = "WIFI"
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        networkType = "CELLULAR"
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        networkType = "ETHERNET"
                    }
                    else -> {
                        networkType = "OTHER"
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            isConnected = networkInfo?.isConnected == true
            @Suppress("DEPRECATION")
            networkType = networkInfo?.typeName ?: "NONE"
            isWifi = networkType.equals("WIFI", ignoreCase = true)
        }

        // Get SSID
        var ssid: String? = null
        var linkSpeed: Int? = null
        if (isWifi) {
            try {
                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager.connectionInfo
                ssid = wifiInfo?.ssid?.replace("\"", "")
                linkSpeed = wifiInfo?.linkSpeed
            } catch (e: Exception) {
                DebugLogger.w("NetworkInfoHelper", "Cannot get WiFi SSID: ${e.message}")
            }
        }

        // Get IP address
        val ipAddress = getLocalIpAddress()

        return NetworkInfo(
            isConnected = isConnected,
            isWifi = isWifi,
            ssid = ssid,
            ipAddress = ipAddress,
            networkType = networkType,
            linkSpeed = linkSpeed
        )
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    // We're looking for IPv4 addresses that are not loopback
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.e("NetworkInfoHelper", "Error getting local IP", e)
        }
        return null
    }

    fun logNetworkInfo(context: Context, tag: String = "NetworkInfo") {
        val info = getNetworkInfo(context)

        DebugLogger.i(tag, "=== Informations Réseau ===")
        DebugLogger.i(tag, "Connecté: ${if (info.isConnected) "OUI" else "NON"}")
        DebugLogger.i(tag, "Type: ${info.networkType}")

        if (info.isWifi) {
            DebugLogger.i(tag, "Wi-Fi SSID: ${info.ssid ?: "Inconnu"}")
            info.linkSpeed?.let {
                DebugLogger.d(tag, "Vitesse liaison: $it Mbps")
            }
        }

        DebugLogger.i(tag, "Adresse IP: ${info.ipAddress ?: "Non disponible"}")

        if (!info.isConnected) {
            DebugLogger.e(tag, "⚠️ Aucune connexion réseau détectée")
        } else if (!info.isWifi) {
            DebugLogger.w(tag, "⚠️ Pas connecté au Wi-Fi - La découverte de TV nécessite le Wi-Fi")
        } else if (info.ssid == null || info.ssid == "<unknown ssid>") {
            DebugLogger.w(tag, "⚠️ SSID Wi-Fi inconnu - Vérifiez les permissions de localisation")
        } else {
            DebugLogger.i(tag, "✓ Réseau Wi-Fi OK")
        }

        DebugLogger.i(tag, "=== Fin Informations Réseau ===")
    }
}
