package com.s3arch.app.data.vpn

/** Calculs d'adressage du VPN, sans dependance Android (donc testables). */
object VpnAddresses {

    /**
     * Premiere IPv4 libre du pool VPN de la Freebox.
     *
     * La box fournit `ip_start` / `ip_end` et la liste des reservations ; l'utilisateur
     * WireGuard cree par l'application a besoin d'une adresse explicite (`ip_reservation`
     * est obligatoire pour ce type d'utilisateur).
     */
    fun nextFreeIp(start: String, end: String, reserved: Set<String>): String? {
        fun octets(ip: String): List<Int>? =
            ip.trim().split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 }
                ?.takeIf { parts -> parts.all { it in 0..255 } }

        val from = octets(start) ?: return null
        val to = octets(end) ?: return null
        fun asLong(parts: List<Int>): Long = parts.fold(0L) { acc, value -> acc * 256 + value }

        var current = asLong(from)
        val last = asLong(to)
        if (last < current) return null
        while (current <= last) {
            val ip = listOf(24, 16, 8, 0)
                .joinToString(".") { shift -> ((current shr shift) and 0xFF).toString() }
            if (ip !in reserved) return ip
            current++
        }
        return null
    }
}
