package com.samsung.remote.network.protocol

import com.samsung.remote.model.RemoteKey

/**
 * Interface pour les différents protocoles de communication avec les TV Samsung
 */
interface TVRemoteProtocol {

    /**
     * Type de protocole
     */
    enum class ProtocolType {
        MODERN_WEBSOCKET,      // TV 2016+ : ws://IP:8001 avec ms.remote.control
        J_SERIES_WEBSOCKET,    // TV 2014-2015 : ws://IP:8001 avec format alternatif
        LEGACY_TCP             // TV pré-2014 : TCP IP:55000
    }

    /**
     * Retourne le type de protocole
     */
    fun getProtocolType(): ProtocolType

    /**
     * Envoie une commande de touche à la TV
     * @param key La touche à envoyer
     * @return true si la commande a été envoyée avec succès
     */
    fun sendKey(key: RemoteKey): Boolean

    /**
     * Envoie du texte à la TV
     * @param text Le texte à envoyer
     * @return true si le texte a été envoyé avec succès
     */
    fun sendText(text: String): Boolean

    /**
     * Vérifie si le protocole est supporté par la TV
     * Retourne true si la TV accepte ce protocole
     */
    fun isSupported(): Boolean

    /**
     * Nom du protocole pour le debug
     */
    fun getProtocolName(): String
}
