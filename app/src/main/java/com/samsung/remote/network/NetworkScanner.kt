package com.samsung.remote.network

import android.content.Context
import android.net.wifi.WifiManager
import com.samsung.remote.model.SamsungTV
import com.samsung.remote.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Scanner réseau pour détecter les TV Samsung en scannant les ports 8001/8002
 */
class NetworkScanner(private val context: Context) {

    companion object {
        private const val TAG = "NetworkScanner"
        private const val SAMSUNG_PORT_WS = 8001
        private const val SAMSUNG_PORT_WSS = 8002
        private const val SOCKET_TIMEOUT_MS = 500 // Timeout rapide pour le scan
        private const val MAX_CONCURRENT_SCANS = 20 // Limiter les connexions simultanées
    }

    /**
     * Récupère l'adresse IP locale de l'appareil
     */
    private fun getLocalIpAddress(): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress

            if (ipInt == 0) return null

            return String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                (ipInt shr 8) and 0xff,
                (ipInt shr 16) and 0xff,
                (ipInt shr 24) and 0xff
            )
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Erreur récupération IP locale: ${e.message}")
            return null
        }
    }

    /**
     * Vérifie si un port est ouvert sur une IP donnée
     */
    private suspend fun isPortOpen(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), SOCKET_TIMEOUT_MS)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Vérifie si une IP a un port Samsung TV ouvert
     */
    private suspend fun checkSamsungPorts(ip: String): Int? = withContext(Dispatchers.IO) {
        // Vérifier port 8002 (WSS) en priorité
        if (isPortOpen(ip, SAMSUNG_PORT_WSS)) {
            DebugLogger.d(TAG, "Port $SAMSUNG_PORT_WSS ouvert sur $ip")
            return@withContext SAMSUNG_PORT_WSS
        }

        // Puis vérifier port 8001 (WS)
        if (isPortOpen(ip, SAMSUNG_PORT_WS)) {
            DebugLogger.d(TAG, "Port $SAMSUNG_PORT_WS ouvert sur $ip")
            return@withContext SAMSUNG_PORT_WS
        }

        null
    }

    /**
     * Résout le nom d'hôte d'une IP
     */
    private suspend fun resolveHostname(ip: String): String? = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(ip)
            val hostname = address.canonicalHostName
            if (hostname != ip && hostname.isNotEmpty()) {
                hostname
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Scanne le réseau local pour trouver des TV Samsung
     */
    suspend fun scanNetwork(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<SamsungTV> = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "=== Démarrage du scan réseau ===")

        val localIp = getLocalIpAddress()
        if (localIp == null) {
            DebugLogger.e(TAG, "Impossible de récupérer l'IP locale")
            return@withContext emptyList()
        }

        DebugLogger.i(TAG, "IP locale: $localIp")

        // Extraire le préfixe réseau (ex: 192.168.1)
        val networkPrefix = localIp.substringBeforeLast(".")
        DebugLogger.i(TAG, "Préfixe réseau: $networkPrefix.x")

        val foundDevices = mutableListOf<SamsungTV>()
        val totalHosts = 254 // Scan 1-254

        DebugLogger.i(TAG, "Scan de $totalHosts adresses IP...")

        // Scanner par lots pour éviter trop de connexions simultanées
        val ipsToScan = (1..254).map { "$networkPrefix.$it" }

        ipsToScan.chunked(MAX_CONCURRENT_SCANS).forEachIndexed { chunkIndex, chunk ->
            val scannedCount = chunkIndex * MAX_CONCURRENT_SCANS
            onProgress(scannedCount, totalHosts)

            // Scanner le chunk en parallèle
            val results = chunk.map { ip ->
                async {
                    val port = checkSamsungPorts(ip)
                    if (port != null) {
                        val hostname = resolveHostname(ip)
                        val deviceName = hostname ?: "Samsung TV ($ip)"
                        DebugLogger.i(TAG, "✓ TV Samsung trouvée: $deviceName à $ip:$port")
                        SamsungTV(name = deviceName, ip = ip, port = port)
                    } else {
                        null
                    }
                }
            }.awaitAll()

            // Ajouter les résultats trouvés
            results.filterNotNull().forEach { tv ->
                foundDevices.add(tv)
            }
        }

        onProgress(totalHosts, totalHosts)

        DebugLogger.i(TAG, "=== Scan terminé: ${foundDevices.size} TV(s) trouvée(s) ===")
        foundDevices.toList()
    }

    /**
     * Scanne une plage d'IP spécifique (pour un scan plus rapide)
     */
    suspend fun scanIpRange(
        networkPrefix: String,
        startRange: Int = 1,
        endRange: Int = 50,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<SamsungTV> = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "Scan rapide: $networkPrefix.$startRange-$endRange")

        val foundDevices = mutableListOf<SamsungTV>()
        val totalHosts = endRange - startRange + 1

        val ipsToScan = (startRange..endRange).map { "$networkPrefix.$it" }

        ipsToScan.chunked(MAX_CONCURRENT_SCANS).forEachIndexed { chunkIndex, chunk ->
            val scannedCount = chunkIndex * MAX_CONCURRENT_SCANS
            onProgress(scannedCount, totalHosts)

            val results = chunk.map { ip ->
                async {
                    val port = checkSamsungPorts(ip)
                    if (port != null) {
                        val hostname = resolveHostname(ip)
                        val deviceName = hostname ?: "Samsung TV ($ip)"
                        SamsungTV(name = deviceName, ip = ip, port = port)
                    } else {
                        null
                    }
                }
            }.awaitAll()

            results.filterNotNull().forEach { foundDevices.add(it) }
        }

        onProgress(totalHosts, totalHosts)

        DebugLogger.i(TAG, "Scan rapide terminé: ${foundDevices.size} TV(s) trouvée(s)")
        foundDevices.toList()
    }
}
