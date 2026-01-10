package com.samsung.remote.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.samsung.remote.model.SamsungTV
import com.samsung.remote.util.DebugLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TVDiscoveryService(private val context: Context) {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    companion object {
        private const val TAG = "TVDiscoveryService"
        private const val SERVICE_TYPE = "_samsung-remote._tcp."

        // Alternative service types to try
        private val ALTERNATIVE_SERVICE_TYPES = listOf(
            "_smartview._tcp.",  // Samsung SmartView
            "_dial._tcp.",       // DIAL protocol
            "_googlecast._tcp.", // Chromecast (some Samsung TVs support this)
            "_airplay._tcp."     // AirPlay (some Samsung TVs support this)
        )
    }

    fun discoverTVs(): Flow<SamsungTV> = callbackFlow {
        DebugLogger.i(TAG, "=== Démarrage de la découverte NSD ===")
        DebugLogger.d(TAG, "Type de service recherché: $SERVICE_TYPE")
        DebugLogger.d(TAG, "Services alternatifs: ${ALTERNATIVE_SERVICE_TYPES.joinToString()}")
        val discoveredTVs = mutableSetOf<String>()
        var servicesFoundCount = 0
        var servicesResolvedCount = 0
        var servicesFailedCount = 0

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                val errorMsg = "Échec du démarrage de la découverte - Code erreur: $errorCode"
                Log.e(TAG, errorMsg)
                DebugLogger.e(TAG, errorMsg)
                DebugLogger.e(TAG, "Vérifiez les permissions réseau et que le Wi-Fi est activé")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                val errorMsg = "Échec de l'arrêt de la découverte - Code erreur: $errorCode"
                Log.e(TAG, errorMsg)
                DebugLogger.e(TAG, errorMsg)
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                val msg = "Découverte NSD démarrée pour: $serviceType"
                Log.d(TAG, msg)
                DebugLogger.i(TAG, msg)
                DebugLogger.d(TAG, "Recherche de TVs Samsung sur le réseau local...")
                DebugLogger.d(TAG, "Assurez-vous que votre téléphone et TV sont sur le même Wi-Fi")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                val msg = "Découverte NSD arrêtée"
                Log.d(TAG, msg)
                DebugLogger.i(TAG, msg)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                servicesFoundCount++
                val serviceName = serviceInfo?.serviceName ?: "unknown"
                val serviceType = serviceInfo?.serviceType ?: "unknown"

                DebugLogger.i(TAG, "📡 Service #$servicesFoundCount trouvé: $serviceName")
                DebugLogger.d(TAG, "  Type: $serviceType")

                // Check if it matches our target or alternatives
                val isTargetService = serviceType.contains("samsung", ignoreCase = true) ||
                                     serviceType == SERVICE_TYPE
                val isAlternativeService = ALTERNATIVE_SERVICE_TYPES.any {
                    serviceType.contains(it.replace(".", ""), ignoreCase = true)
                }

                if (isTargetService) {
                    DebugLogger.i(TAG, "  ✓ Service Samsung détecté!")
                } else if (isAlternativeService) {
                    DebugLogger.i(TAG, "  ⚠ Service alternatif compatible détecté")
                } else {
                    DebugLogger.d(TAG, "  ℹ Service non-Samsung (ignoré)")
                }

                serviceInfo?.let {
                    DebugLogger.d(TAG, "  → Tentative de résolution du service...")
                    try {
                        nsdManager.resolveService(it, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            servicesFailedCount++
                            val errorMsg = "❌ Échec résolution de ${serviceInfo?.serviceName} - Code: $errorCode"
                            Log.e(TAG, errorMsg)
                            DebugLogger.w(TAG, errorMsg)

                            when (errorCode) {
                                NsdManager.FAILURE_ALREADY_ACTIVE -> {
                                    DebugLogger.w(TAG, "  → Résolution déjà en cours pour ce service")
                                }
                                NsdManager.FAILURE_INTERNAL_ERROR -> {
                                    DebugLogger.e(TAG, "  → Erreur interne NSD")
                                }
                                NsdManager.FAILURE_MAX_LIMIT -> {
                                    DebugLogger.e(TAG, "  → Trop de requêtes NSD en cours (limite atteinte)")
                                }
                                else -> {
                                    DebugLogger.e(TAG, "  → Code d'erreur inconnu: $errorCode")
                                }
                            }
                            DebugLogger.d(TAG, "Stats: Trouvés=$servicesFoundCount, Résolus=$servicesResolvedCount, Échecs=$servicesFailedCount")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                            servicesResolvedCount++
                            serviceInfo?.let { info ->
                                val host = info.host?.hostAddress
                                val port = info.port
                                val name = info.serviceName
                                val serviceType = info.serviceType

                                DebugLogger.i(TAG, "✅ Service résolu (#$servicesResolvedCount): $name")
                                DebugLogger.d(TAG, "  → Type: $serviceType")
                                DebugLogger.d(TAG, "  → IP: ${host ?: "null"}")
                                DebugLogger.d(TAG, "  → Port: $port")
                                DebugLogger.d(TAG, "  → Host: ${info.host}")

                                // Log attributes if available
                                try {
                                    val attributes = info.attributes
                                    if (attributes.isNotEmpty()) {
                                        DebugLogger.d(TAG, "  → Attributs TXT:")
                                        attributes.forEach { (key, value) ->
                                            DebugLogger.d(TAG, "    • $key = ${value?.let { String(it) } ?: "null"}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    DebugLogger.d(TAG, "  → Aucun attribut TXT disponible")
                                }

                                if (host != null) {
                                    if (!discoveredTVs.contains(host)) {
                                        discoveredTVs.add(host)
                                        val tv = SamsungTV(
                                            name = name,
                                            ip = host,
                                            port = port
                                        )
                                        val successMsg = "🎉 TV Samsung découverte: $name à $host:$port"
                                        Log.d(TAG, successMsg)
                                        DebugLogger.i(TAG, successMsg)
                                        DebugLogger.d(TAG, "Stats: Trouvés=$servicesFoundCount, Résolus=$servicesResolvedCount, Échecs=$servicesFailedCount")
                                        trySend(tv)
                                    } else {
                                        DebugLogger.d(TAG, "  ⚠ TV déjà découverte (IP en double): $host")
                                    }
                                } else {
                                    DebugLogger.w(TAG, "  ❌ Adresse IP nulle pour $name")
                                }
                            }
                        }
                    })
                    } catch (e: IllegalArgumentException) {
                        DebugLogger.e(TAG, "  ❌ Erreur lors de la résolution: ${e.message}")
                        DebugLogger.e(TAG, "  → Possible doublon ou service invalide")
                    } catch (e: Exception) {
                        DebugLogger.e(TAG, "  ❌ Exception lors de la résolution", e)
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                val msg = "Service perdu: ${serviceInfo?.serviceName}"
                Log.d(TAG, msg)
                DebugLogger.d(TAG, msg)
            }
        }

        try {
            DebugLogger.d(TAG, "Initialisation du NsdManager...")
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            DebugLogger.i(TAG, "NsdManager.discoverServices() appelé avec succès")
        } catch (e: Exception) {
            val errorMsg = "Exception lors du démarrage de la découverte"
            Log.e(TAG, errorMsg, e)
            DebugLogger.e(TAG, errorMsg, e)
            close(e)
        }

        awaitClose {
            DebugLogger.i(TAG, "=== Fermeture de la découverte NSD ===")
            DebugLogger.i(TAG, "Statistiques finales:")
            DebugLogger.i(TAG, "  • Services trouvés: $servicesFoundCount")
            DebugLogger.i(TAG, "  • Services résolus: $servicesResolvedCount")
            DebugLogger.i(TAG, "  • Échecs de résolution: $servicesFailedCount")
            DebugLogger.i(TAG, "  • TVs découvertes: ${discoveredTVs.size}")

            if (servicesFoundCount == 0) {
                DebugLogger.w(TAG, "⚠️ Aucun service mDNS trouvé sur le réseau")
                DebugLogger.w(TAG, "Causes possibles:")
                DebugLogger.w(TAG, "  1. La TV n'est pas allumée ou pas en réseau")
                DebugLogger.w(TAG, "  2. Le routeur bloque le multicast (mDNS)")
                DebugLogger.w(TAG, "  3. La TV utilise un autre protocole (SSDP)")
                DebugLogger.w(TAG, "  4. Pare-feu sur le téléphone ou la TV")
            } else if (discoveredTVs.isEmpty()) {
                DebugLogger.w(TAG, "⚠️ Services trouvés mais aucune TV Samsung identifiée")
                DebugLogger.w(TAG, "Essayez de vérifier manuellement l'IP de votre TV")
            }

            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
                DebugLogger.d(TAG, "Découverte NSD arrêtée proprement")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery", e)
                DebugLogger.e(TAG, "Erreur lors de l'arrêt de la découverte", e)
            }
        }
    }

    fun discoverTVsSimple(onTVFound: (SamsungTV) -> Unit) {
        // Fallback: Try common Samsung TV IPs on the local network
        // This is a simplified version that scans common IPs
        val commonTVName = "Samsung TV"

        // Try to find TVs by scanning the local network
        // In a real implementation, you would scan the network or use SSDP
        // For now, we'll create a placeholder that can be manually configured

        // Example: User could manually enter their TV's IP
        // This would be handled by the UI
    }
}
