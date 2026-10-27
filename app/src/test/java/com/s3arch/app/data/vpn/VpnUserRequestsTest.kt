package com.s3arch.app.data.vpn

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'API `POST /vpn/user/` refuse un utilisateur WireGuard sans `type` ni
 * `ip_reservation` : c'est exactement ce qui provoquait l'`internal_error`
 * « Impossible de creer cet objet ». Chaque variante doit donc conserver ces
 * deux champs, et l'adresse doit appartenir au pool de la box.
 */
class VpnUserRequestsTest {

    private val payloads = VpnUserRequests.payloads(
        login = "s3arch",
        password = "motdepasse24caracteres",
        ip = "192.168.27.65",
    )

    @Test
    fun `toutes les variantes declarent le type wireguard et la reservation`() {
        assertTrue(payloads.isNotEmpty())
        payloads.forEach { payload ->
            val obj = payload.jsonObject
            assertEquals("s3arch", obj["login"]?.jsonPrimitive?.content)
            assertEquals("wireguard", obj["type"]?.jsonPrimitive?.content)
            assertEquals("192.168.27.65", obj["ip_reservation"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `la premiere variante suit l'objet VPNUser documente`() {
        val first = payloads.first().jsonObject
        assertEquals("25", first["conf_wireguard"]?.jsonObject?.get("keepalive")?.jsonPrimitive?.content)
        assertEquals("false", first["conf_wireguard"]?.jsonObject?.get("psk")?.jsonPrimitive?.content)
        assertNull(first["password"])
    }

    @Test
    fun `une variante fournit un mot de passe de secours`() {
        assertTrue(
            payloads.any { it.jsonObject["password"]?.jsonPrimitive?.content == "motdepasse24caracteres" },
        )
    }

    @Test
    fun `les adresses candidates sont prises dans le pool et evitent les reservations`() {
        assertEquals(
            listOf("192.168.27.65", "192.168.27.66", "192.168.27.67"),
            VpnAddresses.freeIps("192.168.27.65", "192.168.27.95", emptySet(), limit = 3),
        )
        assertEquals(
            listOf("192.168.27.67", "192.168.27.68"),
            VpnAddresses.freeIps(
                start = "192.168.27.65",
                end = "192.168.27.95",
                reserved = setOf("192.168.27.65", "192.168.27.66"),
                limit = 2,
            ),
        )
    }
}
