package com.s3arch.app.torrent

import java.util.Locale

/**
 * Lien magnet analysé.
 *
 * `infoHash` est toujours en hexadécimal minuscule : c'est la forme utilisée par les
 * trackers, le DHT et le handshake BitTorrent.
 */
data class MagnetLink(
    val infoHash: String,
    val name: String?,
    val trackers: List<String>,
    val sizeBytes: Long?,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Torrent ${infoHash.take(12)}"

    companion object {

        fun parse(raw: String?): MagnetLink? {
            val text = raw?.trim()?.replace("\n", "")?.replace("\r", "") ?: return null
            if (!text.startsWith("magnet:", ignoreCase = true)) return null
            val query = text.substringAfter('?', "")
            if (query.isBlank()) return null

            val params = mutableMapOf<String, MutableList<String>>()
            query.split('&').forEach { pair ->
                if (pair.isBlank()) return@forEach
                val key = decode(pair.substringBefore('=')).lowercase(Locale.ROOT)
                val value = decode(pair.substringAfter('=', ""))
                params.getOrPut(key) { mutableListOf() } += value
            }

            val hash = params["xt"]
                ?.asSequence()
                ?.mapNotNull { xt ->
                    val value = xt.trim()
                    if (!value.startsWith("urn:btih:", ignoreCase = true)) return@mapNotNull null
                    normalizeHash(value.substringAfterLast(':'))
                }
                ?.firstOrNull()
                ?: return null

            val size = params["xl"]?.firstOrNull()?.toLongOrNull()
            return MagnetLink(
                infoHash = hash,
                name = params["dn"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                trackers = params["tr"].orEmpty().filter { it.isNotBlank() }.distinct(),
                sizeBytes = size,
            )
        }

        /**
         * Accepte les deux encodages de `urn:btih:` : 40 caractères hexadécimaux, ou
         * 32 caractères en base32 (utilisé par certains générateurs de magnets).
         */
        fun normalizeHash(value: String?): String? {
            val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (text.length == 40 && text.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                return text.lowercase(Locale.ROOT)
            }
            if (text.length == 32) {
                val bytes = decodeBase32(text) ?: return null
                return bytes.toHex()
            }
            return null
        }

        private fun decode(value: String): String = runCatching {
            java.net.URLDecoder.decode(value, "UTF-8")
        }.getOrDefault(value)

        private fun decodeBase32(text: String): ByteArray? {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            var buffer = 0
            var bits = 0
            val out = java.io.ByteArrayOutputStream()
            text.uppercase(Locale.ROOT).forEach { char ->
                val index = alphabet.indexOf(char)
                if (index < 0) return null
                buffer = (buffer shl 5) or index
                bits += 5
                if (bits >= 8) {
                    bits -= 8
                    out.write((buffer shr bits) and 0xFF)
                }
            }
            return out.toByteArray().takeIf { it.size == 20 }
        }
    }
}

fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val builder = StringBuilder(size * 2)
    forEach { byte ->
        val value = byte.toInt() and 0xFF
        builder.append(digits[value ushr 4]).append(digits[value and 0x0F])
    }
    return builder.toString()
}

fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0) return null
    val out = ByteArray(length / 2)
    for (index in out.indices) {
        val high = this[index * 2].digitToIntOrNull(16) ?: return null
        val low = this[index * 2 + 1].digitToIntOrNull(16) ?: return null
        out[index] = ((high shl 4) or low).toByte()
    }
    return out
}

/** Vrai si le texte est un lien magnet exploitable. */
fun isMagnetLink(text: String?): Boolean = MagnetLink.parse(text) != null

/**
 * Découpe un bloc de texte collé par l'utilisateur en sources ajoutables.
 *
 * Chaque ligne peut être un magnet ou un lien HTTP(S) ; les lignes vides sont ignorées
 * et un même magnet n'est ajouté qu'une fois (les trackers répètent souvent le lien).
 */
fun parseSourceLines(text: String?): List<String> {
    val lines = text.orEmpty()
        .split('\n', '\r')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return lines.distinct()
}
