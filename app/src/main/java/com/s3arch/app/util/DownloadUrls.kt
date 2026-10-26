package com.s3arch.app.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

/**
 * Normalise les liens fournis par les flux Torznab avant de les envoyer a la Freebox.
 *
 * Beaucoup de trackers renvoient des URL relatives, des espaces non encodes, des entites
 * HTML ou des liens magnets incomplets : la Freebox les refuse alors avec `invalid_url`.
 */
object DownloadUrls {

    private val ABSOLUTE = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val ANY_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    fun sanitize(raw: String?, base: String? = null): String {
        val text = raw
            ?.replace("&amp;", "&")
            ?.replace("\r", "")
            ?.replace("\n", "")
            ?.trim()
            ?.trim('"', '\'', '<', '>')
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) return ""

        if (text.startsWith("magnet:", ignoreCase = true)) {
            val magnet = text.replace(" ", "%20")
            return if (magnet.contains("xt=urn:bt", ignoreCase = true)) magnet else ""
        }

        // Tout autre schema (javascript:, data:, file:) n'a aucun sens ici.
        if (ANY_SCHEME.containsMatchIn(text) && !ABSOLUTE.containsMatchIn(text)) return ""

        // ftp n'est pas representable par HttpUrl mais reste accepte par la Freebox.
        if (text.startsWith("ftp://", ignoreCase = true)) return text

        val absolute = when {
            ABSOLUTE.containsMatchIn(text) -> text
            text.startsWith("//") -> "https:$text"
            else -> resolveRelative(text, base)
        }
        if (absolute.isBlank()) return ""

        return absolute.replace(" ", "%20").toHttpUrlOrNull()?.toString().orEmpty()
    }

    fun isMagnet(value: String?): Boolean = value?.startsWith("magnet:", ignoreCase = true) == true

    fun isHttp(value: String?): Boolean =
        value != null && (value.startsWith("http://", true) || value.startsWith("https://", true))

    private fun resolveRelative(relative: String, base: String?): String {
        if (base.isNullOrBlank()) return ""
        val baseUrl = base.toHttpUrlOrNull() ?: return ""
        runCatching {
            val resolved = URI(baseUrl.toString()).resolve(relative).toString()
            if (ABSOLUTE.containsMatchIn(resolved)) return resolved
        }
        val defaultPort = if (baseUrl.scheme == "https") 443 else 80
        val port = if (baseUrl.port != defaultPort) ":${baseUrl.port}" else ""
        val origin = "${baseUrl.scheme}://${baseUrl.host}$port"
        return if (relative.startsWith("/")) "$origin$relative" else "$origin/$relative"
    }
}
