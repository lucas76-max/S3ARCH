package com.s3arch.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Recherche generique au format Torznab (utilise par C411, TR4KER et V3X).
 *
 * URL construite : <base>?t=search&apikey=<cle>&q=<terme>&limit=100&extended=1
 */
class TorznabClient {

    suspend fun search(
        tracker: Tracker,
        baseUrl: String,
        apiKey: String,
        query: String,
    ): List<TorrentResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw TrackerException("Cle API ${tracker.label} manquante")

        val base = baseUrl.trim().ifBlank { tracker.defaultUrl }
        val httpUrl = base.toHttpUrlOrNull()
            ?: throw TrackerException("URL ${tracker.label} invalide : $base")

        val builder = httpUrl.newBuilder()
        if (httpUrl.queryParameter("t") == null) builder.addQueryParameter("t", "search")
        if (httpUrl.queryParameter("apikey") == null) builder.addQueryParameter("apikey", apiKey.trim())
        if (query.isNotBlank() && httpUrl.queryParameter("q") == null) {
            builder.addQueryParameter("q", query.trim())
        }
        if (httpUrl.queryParameter("extended") == null) builder.addQueryParameter("extended", "1")
        if (httpUrl.queryParameter("limit") == null) builder.addQueryParameter("limit", "100")

        val request = Request.Builder()
            .url(builder.build())
            .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
            .get()
            .build()

        val response = Network.http.newCall(request).execute()
        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw TrackerException(
                    when (resp.code) {
                        401, 403 -> "${tracker.label} : cle API refusee (${resp.code})"
                        404 -> "${tracker.label} : endpoint introuvable (verifiez l'URL Torznab)"
                        else -> "${tracker.label} : erreur HTTP ${resp.code}"
                    },
                )
            }
            if (body.isBlank()) return@use emptyList()
            val trimmed = body.trimStart()
            if (!trimmed.startsWith("<")) {
                throw TrackerException("${tracker.label} : reponse inattendue (URL Torznab incorrecte ?)")
            }
            parseItems(body, tracker)
        }
    }

    /** Verifie la cle API et l'URL Torznab avec une vraie recherche de test. */
    suspend fun testConnection(tracker: Tracker, baseUrl: String, apiKey: String): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw TrackerException("${tracker.label} : cle API vide")
            val base = baseUrl.trim().ifBlank { tracker.defaultUrl }
            val httpUrl = base.toHttpUrlOrNull()
                ?: throw TrackerException("${tracker.label} : URL invalide")
            val builder = httpUrl.newBuilder()
            if (httpUrl.queryParameter("t") == null) builder.addQueryParameter("t", "search")
            if (httpUrl.queryParameter("apikey") == null) builder.addQueryParameter("apikey", apiKey.trim())
            if (httpUrl.queryParameter("q") == null) builder.addQueryParameter("q", "test")
            if (httpUrl.queryParameter("limit") == null) builder.addQueryParameter("limit", "5")
            val request = Request.Builder().url(builder.build()).get().build()
            val response = Network.http.newCall(request).execute()
            response.use {
                if (it.code == 401 || it.code == 403) {
                    throw TrackerException("${tracker.label} : cle API refusee")
                }
                if (!it.isSuccessful) throw TrackerException("${tracker.label} : HTTP ${it.code}")
                val body = it.body?.string().orEmpty()
                if (body.contains("<error")) {
                    val detail = Regex("description=\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.getOrNull(1)
                    throw TrackerException("${tracker.label} : ${detail ?: "cle API refusee"}")
                }
                val count = Regex("<item>").findAll(body).count()
                "${tracker.label} : connexion OK ($count resultats de test)"
            }
        }

    private fun parseItems(xml: String, tracker: Tracker): List<TorrentResult> {
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
                                val attrName = parser.getAttributeValue(null, "name")
                                val attrValue = parser.getAttributeValue(null, "value")
                                if (!attrName.isNullOrBlank() && attrValue != null) {
                                    attrs[attrName.lowercase()] = attrValue
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
                                buildResult(v, a.orEmpty(), enclosure, tracker)?.let { results.add(it) }
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
                throw TrackerException("${tracker.label} : flux Torznab illisible (${e.message ?: "XML invalide"})")
            }
        }
        return results
    }

    private fun buildResult(
        values: Map<String, String>,
        attrs: Map<String, String>,
        enclosure: String?,
        tracker: Tracker,
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
        val magnet = attrs["magneturl"] ?: link?.takeIf { it.startsWith("magnet:") }
        val downloadUrl = enclosure?.takeIf { it.isNotBlank() }
            ?: link?.takeIf { it.startsWith("http") && (it.contains(".torrent") || it.contains("download")) }
            ?: guid?.takeIf { it.startsWith("http") && it.contains(".torrent") }
            ?: magnet
            ?: ""

        val id = guid?.takeIf { it.isNotBlank() }
            ?: attrs["infohash"]
            ?: "$title-$size"

        return TorrentResult(
            id = id,
            title = title,
            sizeBytes = size,
            seeders = seeders,
            leechers = leechers,
            tracker = tracker,
            downloadUrl = downloadUrl,
            magnet = magnet,
            category = values["category"]?.trim()?.takeIf { it.isNotBlank() },
            publishDate = values["pubdate"]?.trim(),
        )
    }

    private companion object {
        val WANTED_TAGS = setOf("title", "size", "link", "guid", "pubdate", "category", "contentlength")
    }
}

class TrackerException(message: String) : Exception(message)
