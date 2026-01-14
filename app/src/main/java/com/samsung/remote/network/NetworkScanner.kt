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
 * Scanner réseau intelligent pour détecter les TV Samsung multi-générations
 * Support: Legacy (2011-2015), Transition (2014-2016), Moderne (2016+)
 */
class NetworkScanner(private val context: Context) {

    companion object {
        private const val TAG = "NetworkScanner"

        // Ports Samsung TV par génération
        private const val SAMSUNG_PORT_LEGACY = 55000  // 2011-2015: TCP propriétaire
        private const val SAMSUNG_PORT_WS = 8001       // 2014-2016: WebSocket
        private const val SAMSUNG_PORT_WSS = 8002      // 2016+: WebSocket Secure

        private const val SOCKET_TIMEOUT_MS = 800 // Timeout pour détection multi-port
        private const val MAX_CONCURRENT_SCANS = 15 // Limiter les connexions simultanées

        // Ordre de préférence des ports (du plus moderne au plus ancien)
        private val SAMSUNG_PORTS = listOf(SAMSUNG_PORT_WSS, SAMSUNG_PORT_WS, SAMSUNG_PORT_LEGACY)
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
     * Détecte tous les protocoles Samsung disponibles sur une IP
     * Retourne le résultat complet de la détection
     */
    private suspend fun detectSamsungProtocols(ip: String): ProtocolDetectionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        DebugLogger.d(TAG, "🔍 Détection protocoles Samsung sur $ip...")

        val detectedProtocols = mutableListOf<SamsungTVProtocol>()

        // Tester chaque port dans l'ordre de préférence
        for (port in SAMSUNG_PORTS) {
            if (isPortOpen(ip, port)) {
                val protocol = SamsungTVProtocol.fromPort(port)
                if (protocol != null) {
                    detectedProtocols.add(protocol)
                    DebugLogger.i(TAG, "✓ Port $port ouvert sur $ip - ${protocol.generation.displayName}")
                }
            }
        }

        // Résoudre le nom d'hôte
        val hostname = resolveHostname(ip)

        // Déterminer le protocole recommandé (le plus moderne détecté)
        val recommendedProtocol = detectedProtocols.firstOrNull()

        val detectionTime = System.currentTimeMillis() - startTime

        val result = ProtocolDetectionResult(
            ip = ip,
            hostname = hostname,
            detectedProtocols = detectedProtocols,
            recommendedProtocol = recommendedProtocol,
            detectionTimeMs = detectionTime
        )

        // Logger le rapport détaillé
        if (detectedProtocols.isNotEmpty()) {
            DebugLogger.i(TAG, "🎯 TV Samsung détectée sur $ip")
            DebugLogger.d(TAG, result.getShortSummary())

            // Log détaillé de la stratégie recommandée
            recommendedProtocol?.let { protocol ->
                DebugLogger.i(TAG, "📋 Stratégie recommandée pour $ip:")
                DebugLogger.i(TAG, "  → Utiliser ${protocol.protocol.uppercase()} sur port ${protocol.port}")
                DebugLogger.i(TAG, "  → Génération: ${protocol.generation.displayName}")
                DebugLogger.i(TAG, "  → SSL: ${if (protocol.useSSL) "Oui" else "Non"}")

                when (protocol.generation) {
                    SamsungTVGeneration.MODERN -> {
                        DebugLogger.i(TAG, "  → Méthode: WebSocket Sécurisé (WSS)")
                        DebugLogger.i(TAG, "  → Auth: Token persistant")
                        DebugLogger.i(TAG, "  → Features: Keep-alive + Auto-reconnect")
                    }
                    SamsungTVGeneration.TRANSITION -> {
                        DebugLogger.i(TAG, "  → Méthode: WebSocket (WS)")
                        DebugLogger.i(TAG, "  → Auth: Simplifiée")
                        DebugLogger.w(TAG, "  → Note: TV ancienne - Envisager fallback vers 8002")
                    }
                    SamsungTVGeneration.LEGACY -> {
                        DebugLogger.w(TAG, "  → Méthode: TCP Legacy (protocole propriétaire)")
                        DebugLogger.w(TAG, "  → Note: TV très ancienne (${protocol.generation.yearRange})")
                        DebugLogger.w(TAG, "  → Support limité - Mise à jour TV recommandée")
                    }
                    else -> {
                        DebugLogger.w(TAG, "  → Protocole non identifié")
                    }
                }
            }

            // Si plusieurs protocoles détectés, logger l'info
            if (detectedProtocols.size > 1) {
                DebugLogger.i(TAG, "ℹ️  TV multi-protocole détectée ($ip):")
                detectedProtocols.forEach { protocol ->
                    DebugLogger.d(TAG, "  - Port ${protocol.port}: ${protocol.generation.displayName}")
                }
                DebugLogger.i(TAG, "  → Utilisation du protocole le plus moderne (${recommendedProtocol?.port})")
            }
        }

        result
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
     * Scanne le réseau local pour trouver des TV Samsung avec détection multi-protocole
     */
    suspend fun scanNetwork(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<SamsungTV> = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "╔════════════════════════════════════════════════════════════╗")
        DebugLogger.i(TAG, "║     SCAN RÉSEAU SAMSUNG TV - MODE MULTI-GÉNÉRATION        ║")
        DebugLogger.i(TAG, "╚════════════════════════════════════════════════════════════╝")

        val localIp = getLocalIpAddress()
        if (localIp == null) {
            DebugLogger.e(TAG, "❌ Impossible de récupérer l'IP locale")
            return@withContext emptyList()
        }

        DebugLogger.i(TAG, "📱 IP locale: $localIp")

        // Extraire le préfixe réseau (ex: 192.168.1)
        val networkPrefix = localIp.substringBeforeLast(".")
        DebugLogger.i(TAG, "🌐 Préfixe réseau: $networkPrefix.x")
        DebugLogger.i(TAG, "🔍 Ports scannés: ${SAMSUNG_PORTS.joinToString(", ")}")
        DebugLogger.i(TAG, "  - Port 55000: Legacy (2011-2015) - TCP propriétaire")
        DebugLogger.i(TAG, "  - Port 8001: Transition (2014-2016) - WebSocket")
        DebugLogger.i(TAG, "  - Port 8002: Moderne (2016+) - WebSocket Secure")

        val foundDevices = mutableListOf<SamsungTV>()
        val totalHosts = 254 // Scan 1-254

        DebugLogger.i(TAG, "")
        DebugLogger.i(TAG, "🚀 Démarrage du scan de $totalHosts adresses IP...")

        // Scanner par lots pour éviter trop de connexions simultanées
        val ipsToScan = (1..254).map { "$networkPrefix.$it" }

        ipsToScan.chunked(MAX_CONCURRENT_SCANS).forEachIndexed { chunkIndex, chunk ->
            val scannedCount = chunkIndex * MAX_CONCURRENT_SCANS
            onProgress(scannedCount, totalHosts)

            // Scanner le chunk en parallèle avec détection multi-protocole
            val results = chunk.map { ip ->
                async {
                    val detection = detectSamsungProtocols(ip)
                    if (detection.detectedProtocols.isNotEmpty()) {
                        val protocol = detection.recommendedProtocol!!
                        val deviceName = detection.hostname ?: "Samsung TV ($ip)"

                        // Créer SamsungTV avec informations enrichies
                        val tv = SamsungTV(
                            name = deviceName,
                            ip = ip,
                            port = protocol.port,
                            generation = protocol.generation,
                            detectedProtocols = detection.detectedProtocols
                        )

                        // Logger le rapport complet de détection
                        DebugLogger.i(TAG, "")
                        DebugLogger.i(TAG, "═══════════════════════════════════════════════")
                        val reportLines = detection.getDetectionReport().lines()
                        reportLines.forEach { line ->
                            DebugLogger.i(TAG, line)
                        }
                        DebugLogger.i(TAG, "═══════════════════════════════════════════════")

                        tv
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

        DebugLogger.i(TAG, "")
        DebugLogger.i(TAG, "╔════════════════════════════════════════════════════════════╗")
        DebugLogger.i(TAG, "║              RÉSULTAT DU SCAN RÉSEAU                       ║")
        DebugLogger.i(TAG, "╚════════════════════════════════════════════════════════════╝")
        DebugLogger.i(TAG, "")

        if (foundDevices.isEmpty()) {
            DebugLogger.w(TAG, "❌ AUCUNE TV SAMSUNG DÉTECTÉE")
            DebugLogger.w(TAG, "")
            DebugLogger.w(TAG, "Causes possibles:")
            DebugLogger.w(TAG, "  1. TV éteinte ou en veille profonde")
            DebugLogger.w(TAG, "  2. TV sur un réseau différent")
            DebugLogger.w(TAG, "  3. Paramètre 'Notification d'accès' désactivé sur la TV")
            DebugLogger.w(TAG, "  4. Pare-feu bloquant les connexions")
            DebugLogger.w(TAG, "")
            DebugLogger.w(TAG, "Solutions:")
            DebugLogger.w(TAG, "  → Vérifier Menu TV → Général → Gestionnaire de périphériques")
            DebugLogger.w(TAG, "  → Activer 'Notification d'accès'")
            DebugLogger.w(TAG, "  → Utiliser 'IP Manuelle' si vous connaissez l'IP de la TV")
        } else {
            DebugLogger.i(TAG, "✓ ${foundDevices.size} TV SAMSUNG DÉTECTÉE(S)")
            DebugLogger.i(TAG, "")
            foundDevices.forEach { tv ->
                DebugLogger.i(TAG, "📺 ${tv.getDetailedDescription()}")
                DebugLogger.i(TAG, "  IP: ${tv.ip}:${tv.port}")
                tv.getProtocolSummary()?.let { summary ->
                    DebugLogger.i(TAG, "  Protocoles: $summary")
                }
            }
        }

        DebugLogger.i(TAG, "")
        DebugLogger.i(TAG, "╚════════════════════════════════════════════════════════════╝")

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
        DebugLogger.i(TAG, "🔍 Scan rapide: $networkPrefix.$startRange-$endRange")
        DebugLogger.i(TAG, "Ports testés: ${SAMSUNG_PORTS.joinToString(", ")}")

        val foundDevices = mutableListOf<SamsungTV>()
        val totalHosts = endRange - startRange + 1

        val ipsToScan = (startRange..endRange).map { "$networkPrefix.$it" }

        ipsToScan.chunked(MAX_CONCURRENT_SCANS).forEachIndexed { chunkIndex, chunk ->
            val scannedCount = chunkIndex * MAX_CONCURRENT_SCANS
            onProgress(scannedCount, totalHosts)

            val results = chunk.map { ip ->
                async {
                    val detection = detectSamsungProtocols(ip)
                    if (detection.detectedProtocols.isNotEmpty()) {
                        val protocol = detection.recommendedProtocol!!
                        val deviceName = detection.hostname ?: "Samsung TV ($ip)"
                        SamsungTV(
                            name = deviceName,
                            ip = ip,
                            port = protocol.port,
                            generation = protocol.generation,
                            detectedProtocols = detection.detectedProtocols
                        )
                    } else {
                        null
                    }
                }
            }.awaitAll()

            results.filterNotNull().forEach { foundDevices.add(it) }
        }

        onProgress(totalHosts, totalHosts)

        DebugLogger.i(TAG, "✓ Scan rapide terminé: ${foundDevices.size} TV(s) trouvée(s)")
        foundDevices.toList()
    }
}
