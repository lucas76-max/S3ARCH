package com.s3arch.app.data.vpn

/** Calculs d'adressage du VPN, sans dependance Android (donc testables). */
object VpnAddresses {

    /**
     * Premiere IPv4 libre du pool VPN de la Freebox.
     *
     * La box fournit `ip_start` / `ip_end` et la liste des reservations. L'application
     * propose une adresse libre (`ip_reservation`) a la creation de l'utilisateur VPN ;
     * si la box la refuse, elle attribue elle-meme une adresse du pool.
     */
    fun nextFreeIp(start: String, end: String, reserved: Set<String>): String? =
        freeIps(start, end, reserved, limit = 1).firstOrNull()

    /**
     * Plusieurs premieres adresses libres du pool.
     *
     * La box peut refuser une reservation donnee (`internal_error`) sans expliquer
     * pourquoi : disposer de deux ou trois candidates permet de reessayer sur une
     * autre adresse au lieu d'abandonner la creation du VPN.
     */
    fun freeIps(
        start: String,
        end: String,
        reserved: Set<String>,
        limit: Int = 3,
    ): List<String> {
        fun octets(ip: String): List<Int>? =
            ip.trim().split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 }
                ?.takeIf { parts -> parts.all { it in 0..255 } }

        val from = octets(start) ?: return emptyList()
        val to = octets(end) ?: return emptyList()
        fun asLong(parts: List<Int>): Long = parts.fold(0L) { acc, value -> acc * 256 + value }

        var current = asLong(from)
        val last = asLong(to)
        if (last < current || limit <= 0) return emptyList()
        val free = mutableListOf<String>()
        while (current <= last && free.size < limit) {
            val ip = listOf(24, 16, 8, 0)
                .joinToString(".") { shift -> ((current shr shift) and 0xFF).toString() }
            if (ip !in reserved) free += ip
            current++
        }
        return free
    }
}
