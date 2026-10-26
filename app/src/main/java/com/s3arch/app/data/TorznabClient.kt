package com.s3arch.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.s3arch.app.util.AppLog
import com.s3arch.app.util.DownloadUrls
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Recherche generique au format Torznab (C411, TR4KER, V3X et trackers tiers).
 *
 * URL construite : <base>?t=search&apikey=<cle>&q=<terme>&limit=100&extended=1
 */
class TorznabClient {

    suspend fun search(source: TrackerSource, query: String): List<TorrentResult> =
        withContext(Dispatchers.IO) {
            if (source.apiKey.isBlank()) throw TrackerException("Cle API ${source.label} manquante")

            val httpUrl = source.url.trim().toHttpUrlOrNull()
                ?: throw TrackerException("URL ${source.label} invalide : ${source.url}")

            val builder = httpUrl.newBuilder()
            if (httpUrl.queryParameter("t") == null) builder.addQueryParameter("t", "search")
            if (httpUrl.queryParameter("apikey") == null) builder.addQueryParameter("apikey", source.apiKey.trim())
            if (query.isNotBlank() && httpUrl.queryParameter("q") == null) {
                builder.addQueryParameter("q", query.trim())
            }
            if (httpUrl.queryParameter("extended") == null) builder.addQueryParameter("extended", "1")
            if (httpUrl.queryParameter("limit") == null) builder.addQueryParameter("limit", "100")

            val built = builder.build()
            AppLog.info("Tracker", "${source.label} : recherche « $query » → ${AppLog.redact(built.toString())}")

            val request = Request.Builder()
                .url(built)
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .get()
                .build()

            val response = Network.http.newCall(request).execute()
            response.use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val message = when (resp.code) {
                        401, 403 -> "${source.label} : cle API refusee (${resp.code})"
                        404 -> "${source.label} : endpoint introuvable (verifiez l'URL Torznab)"
                        else -> "${source.label} : erreur HTTP ${resp.code}"
                    }
                    AppLog.error("Tracker", message)
                    throw TrackerException(message)
                }
                if (body.isBlank()) {
                    AppLog.warn("Tracker", "${source.label} : reponse vide")
                    return@use emptyList()
                }
                if (!body.trimStart().startsWith("<")) {
                    val message = "${source.label} : reponse inattendue (URL Torznab incorrecte ?)"
                    AppLog.error("Tracker", message)
                    throw TrackerException(message)
                }
                val parsed = parseItems(body, source)
                AppLog.info("Tracker", "${source.label} : ${parsed.size} resultats (HTTP ${resp.code})")
                parsed
            }
        }

    /**
     * Recupere le fichier .torrent depuis le tracker, cote telephone.
     *
     * Envoyer le fichier directement a la Freebox evite les refus « invalid_url » :
     * la Freebox n'a plus besoin d'atteindre le tracker elle-meme.
     */
    suspend fun fetchTorrent(url: String): ByteArray = withContext(Dispatchers.IO) {
        val target = DownloadUrls.sanitize(url)
        if (target.isBlank() || !DownloadUrls.isHttp(target)) {
            throw TrackerException("Lien .torrent inutilisable : $target")
        }
        AppLog.info("Tracker", "Recuperation du .torrent ${AppLog.redact(target)}")

        val request = Request.Builder()
            .url(target)
            .header("Accept", "application/x-bittorrent, application/octet-stream, */*")
            .header("User-Agent", "S3ARCH/1.3 (Android)")
            .get()
            .build()

        val response = Network.http.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val message = "Le tracker a refuse le .torrent (HTTP ${resp.code})"
                AppLog.warn("Tracker", message)
                throw TrackerException(message)
            }
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (bytes.isEmpty()) throw TrackerException("Fichier .torrent vide")
            if (bytes.size > 8 * 1024 * 1024) throw TrackerException("Fichier .torrent trop volumineux")

            val probe = String(bytes.copyOfRange(0, minOf(bytes.size, 4096)), Charsets.ISO_8859_1)
            val looksLikeTorrent = probe.startsWith("d") && probe.contains("4:info")
            if (!looksLikeTorrent) {
                val message = "Le lien ne renvoie pas un .torrent (page HTML ou erreur du tracker)"
                AppLog.warn("Tracker", message)
                throw TrackerException(message)
            }
            AppLog.info("Tracker", "Torrent recupere (${bytes.size} octets)")
            bytes
        }
    }

    /** Verifie la cle API et l'URL Torznab avec une vraie recherche de test. */
    suspend fun testConnection(source: TrackerSource): String = withContext(Dispatchers.IO) {
        if (source.apiKey.isBlank()) throw TrackerException("${source.label} : cle API vide")
        val httpUrl = source.url.trim().toHttpUrlOrNull()
            ?: throw TrackerException("${source.label} : URL invalide")
        val builder = httpUrl.newBuilder()
        if (httpUrl.queryParameter("t") == null) builder.addQueryParameter("t", "search")
        if (httpUrl.queryParameter("apikey") == null) builder.addQueryParameter("apikey", source.apiKey.trim())
        if (httpUrl.queryParameter("q") == null) builder.addQueryParameter("q", "test")
        if (httpUrl.queryParameter("limit") == null) builder.addQueryParameter("limit", "5")
        val request = Request.Builder().url(builder.build()).get().build()
        val response = Network.http.newCall(request).execute()
        response.use {
            if (it.code == 401 || it.code == 403) {
                throw TrackerException("${source.label} : cle API refusee")
            }
            if (!it.isSuccessful) throw TrackerException("${source.label} : HTTP ${it.code}")
            val body = it.body?.string().orEmpty()
            if (body.contains("<error")) {
                val detail = Regex("description=\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
                throw TrackerException("${source.label} : ${detail ?: "cle API refusee"}")
            }
            val count = Regex("<item>").findAll(body).count()
            "${source.label} : connexion OK ($count resultats de test)"
        }
    }

    private fun parseItems(xml: String, source: TrackerSource): List<TorrentResult> {
        val results = mutableListOf<TorrentResult>()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var event = parser.eventType
            var values: MutableMap<String, String>? = null
            var attrs: MutableMap<String, String>? = null
            var enclosure: String? = null
            var currentTag: String? = null

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val rawName = parser.name.orEmpty()
                        val name = rawName.substringAfterLast(':').lowercase()
                        currentTag = name
                        when {
                            name == "item" -> {
                                values = mutableMapOf()
                                attrs = mutableMapOf()
                                enclosure = null
                            }
                            name == "enclosure" && values != null -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (!url.isNullOrBlank()) enclosure = url.trim()
                            }
                            name == "attr" && attrs != null -> {
                                val attrName = parser.getAttributeValue(null, "name")?.lowercase()
                                val attrValue = parser.getAttributeValue(null, "value")
                                if (!attrName.isNullOrBlank() && attrValue != null) {
                                    if (attrName == "category" && attrValue.trim().all { it.isDigit() }) {
                                        // Plusieurs categories possibles : on les accumule.
                                        val previous = attrs["categoryids"]
                                        attrs["categoryids"] = if (previous.isNullOrBlank()) {
                                            attrValue.trim()
                                        } else {
                                            "$previous,${attrValue.trim()}"
                                        }
                                    } else {
                                        attrs[attrName] = attrValue
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val v = values
                        val text = parser.text
                        if (v != null && currentTag != null && !text.isNullOrBlank()) {
                            if (currentTag in WANTED_TAGS) {
                                v[currentTag] = (v[currentTag]?.plus(" ") ?: "") + text.trim()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = parser.name.orEmpty().substringAfterLast(':').lowercase()
                        if (name == "item") {
                            val v = values
                            val a = attrs
                            if (v != null) {
                                buildResult(v, a.orEmpty(), enclosure, source)?.let { results.add(it) }
                            }
                            values = null
                            attrs = null
                            enclosure = null
                        }
                        currentTag = null
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            if (results.isEmpty()) {
                throw TrackerException("${source.label} : flux Torznab illisible (${e.message ?: "XML invalide"})")
            }
        }
        return results
    }

    private fun buildResult(
        values: Map<String, String>,
        attrs: Map<String, String>,
        enclosure: String?,
        source: TrackerSource,
    ): TorrentResult? {
        val title = values["title"]?.trim().orEmpty()
        if (title.isBlank()) return null

        val size = values["size"]?.trim()?.toLongOrNull()
            ?: values["contentlength"]?.trim()?.toLongOrNull()
            ?: attrs["size"]?.toLongOrNull()
            ?: 0L

        val seeders = attrs["seeders"]?.toIntOrNull() ?: 0
        val peers = attrs["peers"]?.toIntOrNull() ?: 0
        val leechers = attrs["leechers"]?.toIntOrNull() ?: (peers - seeders).coerceAtLeast(0)

        val link = values["link"]?.trim()
        val guid = values["guid"]?.trim()
        val magnet = DownloadUrls.sanitize(
            attrs["magneturl"] ?: link?.takeIf { DownloadUrls.isMagnet(it) },
            source.url,
        ).takeIf { it.isNotBlank() }

        val candidate = enclosure?.takeIf { it.isNotBlank() }
            ?: link?.takeIf { it.startsWith("http") && (it.contains(".torrent") || it.contains("download")) }
            ?: guid?.takeIf { it.startsWith("http") && it.contains(".torrent") }
            ?: magnet
            ?: ""
        // Les URL relatives, les espaces et les entites HTML sont normalises ici.
        val downloadUrl = DownloadUrls.sanitize(candidate, source.url).ifBlank { magnet ?: "" }

        val id = guid?.takeIf { it.isNotBlank() }
            ?: attrs["infohash"]
            ?: "$title-$size"

        val categoryIds = attrs["categoryids"]
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()
        val categoryText = values["category"]?.trim()?.takeIf { it.isNotBlank() }

        return TorrentResult(
            id = id,
            title = title,
            sizeBytes = size,
            seeders = seeders,
            leechers = leechers,
            trackerId = source.id,
            trackerLabel = source.label,
            downloadUrl = downloadUrl,
            magnet = magnet,
            category = categoryText,
            publishDate = values["pubdate"]?.trim(),
            mediaType = MediaType.infer(categoryIds, categoryText, title),
        )
    }

    private companion object {
        val WANTED_TAGS = setOf("title", "size", "link", "guid", "pubdate", "category", "contentlength")
    }
}

class TrackerException(message: String) : Exception(message)
