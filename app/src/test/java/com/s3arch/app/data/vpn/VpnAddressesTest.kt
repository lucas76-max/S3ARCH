package com.s3arch.app.data.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le VPN de la Freebox exige une adresse explicite pour un utilisateur WireGuard :
 * une erreur ici ferait echouer la creation du VPN des la premiere proposition.
 */
class VpnAddressesTest {

    @Test
    fun `prend la premiere adresse libre du pool`() {
        assertEquals(
            "192.168.27.65",
            VpnAddresses.nextFreeIp("192.168.27.65", "192.168.27.95", emptySet()),
        )
    }

    @Test
    fun `saute les adresses deja reservees`() {
        assertEquals(
            "192.168.27.67",
            VpnAddresses.nextFreeIp(
                start = "192.168.27.65",
                end = "192.168.27.95",
                reserved = setOf("192.168.27.65", "192.168.27.66"),
            ),
        )
    }

    @Test
    fun `franchit correctement la limite d'octet`() {
        assertEquals(
            "192.168.28.0",
            VpnAddresses.nextFreeIp(
                start = "192.168.27.254",
                end = "192.168.28.10",
                reserved = setOf("192.168.27.254", "192.168.27.255"),
            ),
        )
    }

    @Test
    fun `renvoie null quand le pool est plein`() {
        assertNull(
            VpnAddresses.nextFreeIp(
                start = "192.168.27.65",
                end = "192.168.27.66",
                reserved = setOf("192.168.27.65", "192.168.27.66"),
            ),
        )
    }

    @Test
    fun `renvoie null sur une plage invalide`() {
        assertNull(VpnAddresses.nextFreeIp("", "192.168.27.95", emptySet()))
        assertNull(VpnAddresses.nextFreeIp("192.168.27.95", "192.168.27.65", emptySet()))
        assertNull(VpnAddresses.nextFreeIp("192.168.27.300", "192.168.27.400", emptySet()))
    }
}
