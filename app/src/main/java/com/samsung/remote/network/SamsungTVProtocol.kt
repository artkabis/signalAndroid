package com.samsung.remote.network

/**
 * Représente les différentes générations de protocoles Samsung TV
 */
enum class SamsungTVGeneration(
    val displayName: String,
    val yearRange: String,
    val description: String
) {
    LEGACY(
        displayName = "Legacy (2011-2015)",
        yearRange = "2011-2015",
        description = "Anciennes TV Samsung - Protocole propriétaire TCP"
    ),

    TRANSITION(
        displayName = "Transition (2014-2016)",
        yearRange = "2014-2016",
        description = "TV Samsung en transition - WebSocket non sécurisé"
    ),

    MODERN(
        displayName = "Moderne (2016+)",
        yearRange = "2016+",
        description = "TV Samsung modernes - WebSocket sécurisé (WSS)"
    ),

    UNKNOWN(
        displayName = "Inconnue",
        yearRange = "N/A",
        description = "Version non identifiée"
    )
}

/**
 * Configuration du protocole pour une TV Samsung
 */
data class SamsungTVProtocol(
    val generation: SamsungTVGeneration,
    val port: Int,
    val useSSL: Boolean,
    val protocol: String,
    val wsPath: String,
    val compatibility: List<String>
) {
    companion object {
        /**
         * Port 55000 - Legacy protocol (2011-2015)
         * Protocole propriétaire Samsung, communication TCP directe
         */
        val LEGACY_TCP = SamsungTVProtocol(
            generation = SamsungTVGeneration.LEGACY,
            port = 55000,
            useSSL = false,
            protocol = "tcp",
            wsPath = "",
            compatibility = listOf(
                "Série D (2011)",
                "Série E (2012)",
                "Série F (2013)",
                "Série H (2014)",
                "Série J (2015)"
            )
        )

        /**
         * Port 8001 - WebSocket (2014-2016)
         * WebSocket non sécurisé, protocole de transition
         */
        val WEBSOCKET = SamsungTVProtocol(
            generation = SamsungTVGeneration.TRANSITION,
            port = 8001,
            useSSL = false,
            protocol = "ws",
            wsPath = "/api/v2/channels/samsung.remote.control",
            compatibility = listOf(
                "Série H tardive (2014)",
                "Série J (2015)",
                "Série K (2016)"
            )
        )

        /**
         * Port 8002 - WebSocket Secure (2016+)
         * WebSocket sécurisé avec TLS, protocole moderne
         */
        val WEBSOCKET_SECURE = SamsungTVProtocol(
            generation = SamsungTVGeneration.MODERN,
            port = 8002,
            useSSL = true,
            protocol = "wss",
            wsPath = "/api/v2/channels/samsung.remote.control",
            compatibility = listOf(
                "Série K tardive (2016)",
                "Série M (2017)",
                "Série N (2018)",
                "Série Q/R (2019)",
                "Série Q/T (2020)",
                "Série AU/Q/QN (2021)",
                "Série Q/QN/S (2022)",
                "Série Q/QN/S (2023+)"
            )
        )

        /**
         * Retourne tous les protocoles connus dans l'ordre de préférence
         * (du plus moderne au plus ancien)
         */
        fun getAllProtocols(): List<SamsungTVProtocol> = listOf(
            WEBSOCKET_SECURE,
            WEBSOCKET,
            LEGACY_TCP
        )

        /**
         * Retourne le protocole basé sur le port
         */
        fun fromPort(port: Int): SamsungTVProtocol? = when (port) {
            55000 -> LEGACY_TCP
            8001 -> WEBSOCKET
            8002 -> WEBSOCKET_SECURE
            else -> null
        }
    }

    /**
     * Construit l'URL WebSocket complète pour ce protocole
     */
    fun buildWebSocketUrl(ip: String, deviceName: String): String {
        return if (protocol == "tcp") {
            "$protocol://$ip:$port"
        } else {
            "$protocol://$ip:$port$wsPath?name=${java.net.URLEncoder.encode(deviceName, "UTF-8")}"
        }
    }

    /**
     * Retourne une description détaillée du protocole
     */
    fun getDetailedDescription(): String {
        return buildString {
            appendLine("Génération: ${generation.displayName}")
            appendLine("Années: ${generation.yearRange}")
            appendLine("Port: $port")
            appendLine("Protocole: ${protocol.uppercase()}")
            appendLine("SSL/TLS: ${if (useSSL) "Oui" else "Non"}")
            if (wsPath.isNotEmpty()) {
                appendLine("Chemin WS: $wsPath")
            }
            appendLine("Compatible avec:")
            compatibility.forEach { appendLine("  - $it") }
        }
    }
}

/**
 * Résultat de la détection de protocole
 */
data class ProtocolDetectionResult(
    val ip: String,
    val hostname: String?,
    val detectedProtocols: List<SamsungTVProtocol>,
    val recommendedProtocol: SamsungTVProtocol?,
    val detectionTimeMs: Long
) {
    /**
     * Retourne un rapport détaillé de la détection
     */
    fun getDetectionReport(): String {
        return buildString {
            appendLine("=== Rapport de détection TV Samsung ===")
            appendLine("IP: $ip")
            if (hostname != null) {
                appendLine("Nom d'hôte: $hostname")
            }
            appendLine("Temps de détection: ${detectionTimeMs}ms")
            appendLine()

            if (detectedProtocols.isEmpty()) {
                appendLine("❌ AUCUN PROTOCOLE DÉTECTÉ")
                appendLine()
                appendLine("Stratégie recommandée:")
                appendLine("1. Vérifier que la TV est allumée")
                appendLine("2. Vérifier que TV et téléphone sont sur le même réseau")
                appendLine("3. Activer 'Notification d'accès' dans les paramètres TV:")
                appendLine("   Menu → Général → Gestionnaire de périphériques externes")
                appendLine("4. Essayer une connexion manuelle par IP")
            } else {
                appendLine("✓ ${detectedProtocols.size} PROTOCOLE(S) DÉTECTÉ(S)")
                appendLine()

                detectedProtocols.forEach { protocol ->
                    appendLine("Port ${protocol.port} - ${protocol.generation.displayName}")
                    appendLine("  ${protocol.protocol.uppercase()} ${if (protocol.useSSL) "(Sécurisé)" else "(Non sécurisé)"}")
                }
                appendLine()

                recommendedProtocol?.let { recommended ->
                    appendLine("🎯 PROTOCOLE RECOMMANDÉ")
                    appendLine("Port: ${recommended.port}")
                    appendLine("Type: ${recommended.protocol.uppercase()}")
                    appendLine("Génération: ${recommended.generation.displayName}")
                    appendLine("Années: ${recommended.generation.yearRange}")
                    appendLine()

                    appendLine("Stratégie de connexion:")
                    when (recommended.generation) {
                        SamsungTVGeneration.MODERN -> {
                            appendLine("1. Utiliser WebSocket Sécurisé (WSS) sur port 8002")
                            appendLine("2. Authentification par token persistant")
                            appendLine("3. Keep-alive automatique toutes les 30s")
                            appendLine("4. Reconnexion automatique en cas de déconnexion")
                        }
                        SamsungTVGeneration.TRANSITION -> {
                            appendLine("1. Utiliser WebSocket (WS) sur port 8001")
                            appendLine("2. Authentification simplifiée")
                            appendLine("3. Fallback vers port 8002 si échec")
                        }
                        SamsungTVGeneration.LEGACY -> {
                            appendLine("1. Utiliser protocole TCP propriétaire sur port 55000")
                            appendLine("2. Implémentation legacy requise")
                            appendLine("3. Authentification différente")
                            appendLine("⚠️  Support limité - Envisager mise à jour TV")
                        }
                        SamsungTVGeneration.UNKNOWN -> {
                            appendLine("1. Tester tous les protocoles disponibles")
                            appendLine("2. Commencer par le plus moderne (8002)")
                            appendLine("3. Fallback progressif vers protocoles plus anciens")
                        }
                    }
                    appendLine()
                    appendLine("Compatible avec: ${recommended.compatibility.joinToString(", ")}")
                } ?: run {
                    appendLine("⚠️  Aucun protocole recommandé")
                    appendLine("Plusieurs protocoles détectés - Test manuel requis")
                }
            }
        }
    }

    /**
     * Retourne un résumé court pour l'interface utilisateur
     */
    fun getShortSummary(): String {
        return when {
            detectedProtocols.isEmpty() ->
                "Aucun protocole détecté sur $ip"

            recommendedProtocol != null ->
                "${recommendedProtocol.generation.displayName} (Port ${recommendedProtocol.port})"

            else ->
                "${detectedProtocols.size} protocole(s) détecté(s)"
        }
    }
}
