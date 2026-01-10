package com.samsung.remote.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object NetworkScanner {

    private const val TAG = "NetworkScanner"
    private const val PING_TIMEOUT_MS = 500
    private const val SOCKET_TIMEOUT_MS = 200

    data class ActiveHost(
        val ip: String,
        val hostname: String?,
        val respondsToPing: Boolean,
        val hasSamsungPort: Boolean = false,
        val detectedPort: Int? = null  // The specific Samsung port that was detected (8001 or 8002)
    )

    /**
     * Scanne le sous-réseau local pour trouver les hôtes actifs
     */
    suspend fun scanLocalNetwork(context: Context): List<ActiveHost> = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "=== Démarrage du scan réseau ===")

        val networkInfo = NetworkInfoHelper.getNetworkInfo(context)
        val localIp = networkInfo.ipAddress

        if (localIp == null) {
            DebugLogger.e(TAG, "Impossible de scanner : aucune adresse IP locale")
            return@withContext emptyList()
        }

        DebugLogger.i(TAG, "IP locale : $localIp")

        // Extract subnet (e.g., 192.168.1.x from 192.168.1.107)
        val subnet = localIp.substringBeforeLast(".")
        DebugLogger.i(TAG, "Sous-réseau détecté : $subnet.x")
        DebugLogger.i(TAG, "Scan de 254 adresses IP en cours...")

        val activeHosts = mutableListOf<ActiveHost>()
        var scannedCount = 0
        var activeCount = 0

        // Scan all IPs in parallel (1-254)
        val results = (1..254).map { lastOctet ->
            async(Dispatchers.IO) {
                val ip = "$subnet.$lastOctet"
                scannedCount++

                if (scannedCount % 50 == 0) {
                    DebugLogger.d(TAG, "Progression : $scannedCount/254 adresses scannées...")
                }

                val host = pingHost(ip)
                if (host != null) {
                    activeCount++
                    DebugLogger.i(TAG, "🟢 Hôte actif trouvé : $ip ${host.hostname?.let { "($it)" } ?: ""}")

                    // Check for Samsung TV ports
                    val samsungPort = checkSamsungPorts(ip)
                    if (samsungPort != null) {
                        DebugLogger.i(TAG, "  ✓ Port Samsung $samsungPort détecté sur $ip - Probable TV Samsung!")
                    }

                    host.copy(
                        hasSamsungPort = samsungPort != null,
                        detectedPort = samsungPort
                    )
                } else {
                    null
                }
            }
        }.awaitAll().filterNotNull()

        activeHosts.addAll(results)

        DebugLogger.i(TAG, "=== Scan terminé ===")
        DebugLogger.i(TAG, "Résultats :")
        DebugLogger.i(TAG, "  • Adresses scannées : 254")
        DebugLogger.i(TAG, "  • Hôtes actifs : ${activeHosts.size}")
        DebugLogger.i(TAG, "  • Possibles TVs Samsung : ${activeHosts.count { it.hasSamsungPort }}")

        if (activeHosts.isNotEmpty()) {
            DebugLogger.i(TAG, "Liste des hôtes actifs :")
            activeHosts.forEach { host ->
                val tvIndicator = if (host.hasSamsungPort) " [TV?]" else ""
                DebugLogger.i(TAG, "  • ${host.ip}${host.hostname?.let { " - $it" } ?: ""}$tvIndicator")
            }
        }

        activeHosts
    }

    /**
     * Ping un hôte pour vérifier s'il est actif
     */
    private suspend fun pingHost(ip: String): ActiveHost? = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(ip)
            if (address.isReachable(PING_TIMEOUT_MS)) {
                val hostname = try {
                    address.canonicalHostName.takeIf { it != ip }
                } catch (e: Exception) {
                    null
                }
                ActiveHost(ip, hostname, true)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Vérifie si les ports Samsung sont ouverts (8001, 8002)
     * Retourne le port détecté ou null si aucun port Samsung n'est ouvert
     */
    private suspend fun checkSamsungPorts(ip: String): Int? = withContext(Dispatchers.IO) {
        // Samsung TV ports in order of preference (8002 for newer TVs, 8001 for older)
        val samsungPorts = listOf(8001, 8002, 8080)

        for (port in samsungPorts) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), SOCKET_TIMEOUT_MS)
                    DebugLogger.d(TAG, "  → Port $port ouvert sur $ip")
                    return@withContext port
                }
            } catch (e: Exception) {
                // Port closed or timeout, try next
            }
        }
        null
    }

    /**
     * Scan rapide d'une IP spécifique
     */
    suspend fun checkSingleHost(ip: String): ActiveHost? = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "Vérification de l'hôte : $ip")
        val host = pingHost(ip)

        if (host != null) {
            val samsungPort = checkSamsungPorts(ip)
            DebugLogger.i(TAG, "✓ Hôte $ip est accessible")
            if (samsungPort != null) {
                DebugLogger.i(TAG, "✓ Port Samsung $samsungPort détecté - Probable TV Samsung!")
            }
            return@withContext host.copy(
                hasSamsungPort = samsungPort != null,
                detectedPort = samsungPort
            )
        } else {
            DebugLogger.w(TAG, "✗ Hôte $ip n'est pas accessible")
            return@withContext null
        }
    }
}
