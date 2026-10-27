package com.s3arch.app.data.vpn

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Corps envoyes a `POST /vpn/user/` pour un utilisateur WireGuard.
 *
 * L'objet `VPNUser` documente impose trois champs pour le type `wireguard` :
 * `login`, `type` et `ip_reservation` (l'adresse doit appartenir au pool VPN).
 * `conf_wireguard` porte le keepalive et l'usage facultatif d'une cle pre-partagee.
 *
 * Certains firmwares refusent cette forme minimale avec un `internal_error`
 * (« Impossible de creer cet objet »). On reessaie donc avec les variantes
 * acceptees par les differentes versions de Freebox OS, sans jamais perdre
 * `type` ni `ip_reservation` : c'est leur absence qui rend la requete invalide.
 */
internal object VpnUserRequests {

    /** Intervalle de keepalive recommande derriere un NAT. */
    const val KEEPALIVE = 25

    fun payloads(login: String, password: String, ip: String): List<JsonObject> = listOf(
        // Forme minimale documentee : pas de mot de passe (WireGuard n'en utilise pas).
        buildJsonObject {
            put("login", login)
            put("type", "wireguard")
            put("ip_reservation", ip)
            put("conf_wireguard", wireGuardConf())
        },
        // Firmware qui exige malgre tout un mot de passe (8 a 32 caracteres).
        buildJsonObject {
            put("login", login)
            put("type", "wireguard")
            put("ip_reservation", ip)
            put("password", password)
            put("conf_wireguard", wireGuardConf())
        },
        buildJsonObject {
            put("login", login)
            put("type", "wireguard")
            put("ip_reservation", ip)
            put("password", password)
        },
        // Dernier repli : uniquement les champs obligatoires.
        buildJsonObject {
            put("login", login)
            put("type", "wireguard")
            put("ip_reservation", ip)
        },
    )

    private fun wireGuardConf(): JsonObject = buildJsonObject {
        put("keepalive", KEEPALIVE)
        put("psk", false)
    }
}
