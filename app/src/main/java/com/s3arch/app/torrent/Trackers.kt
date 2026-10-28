package com.s3arch.app.torrent

import com.s3arch.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Adresse d'un pair BitTorrent. */
data class PeerAddress(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
}

data class TrackerResponse(
    val peers: List<PeerAddress>,
    val intervalSeconds: Int,
    val failure: String? = null,
)

/** Annonce HTTP (BEP 3) aupres des trackers du torrent. */
class TrackerClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun announce(
        url: String,
        infoHash: ByteArray,
        peerId: ByteArray,
        port: Int,
        downloaded: Long = 0L,
        left: Long = 0L,
        uploaded: Long = 0L,
        event: String? = null,
    ): TrackerResponse = withContext(Dispatchers.IO) {
        val separator = if (url.contains('?')) '&' else '?'
        val query = buildString {
            append("info_hash=").append(percent(infoHash))
            append("&peer_id=").append(percent(peerId))
            append("&port=").append(port)
            append("&uploaded=").append(uploaded)
            append("&downloaded=").append(downloaded)
            append("&left=").append(left)
            append("&compact=1&numwant=100&key=").append(percent(peerId.take(8).toByteArray()))
            if (event != null) append("&event=").append(event)
        }
        val request = Request.Builder()
            .url(url + separator + query)
            .header("User-Agent", "S3ARCH/1.5")
            .get()
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 200) {
                throw TrackerException("Tracker HTTP ${response.code}")
            }
            response.body?.bytes() ?: throw TrackerException("Réponse de tracker vide")
        }
        val dict = Bencode.decodeOrNull(body) as? BValue.BDict
            ?: throw TrackerException("Réponse de tracker illisible")
        dict.str("failure reason")?.let { reason ->
            return@withContext TrackerResponse(emptyList(), 60, reason)
        }
        val peers = mutableListOf<PeerAddress>()
        when (val raw = dict["peers"]) {
            is BValue.BStr -> peers += parseCompact(raw.bytes, 6)
            is BValue.BList -> raw.items.forEach { item ->
                val peer = item as? BValue.BDict ?: return@forEach
                val host = peer.str("ip") ?: return@forEach
                val port = peer.int("port")?.toInt() ?: return@forEach
                if (port in 1..65535) peers += PeerAddress(host, port)
            }
            else -> Unit
        }
        (dict["peers6"] as? BValue.BStr)?.let { peers += parseCompact(it.bytes, 18) }
        if (peers.isEmpty()) AppLog.info("Torrent", "Tracker sans pair : ${redact(url)}")
        TrackerResponse(
            peers = peers.distinct(),
            intervalSeconds = dict.int("interval")?.toInt()?.coerceIn(30, 3600) ?: 120,
        )
    }

    private fun parseCompact(raw: ByteArray, stride: Int): List<PeerAddress> {
        if (raw.isEmpty() || raw.size % stride != 0) return emptyList()
        val result = mutableListOf<PeerAddress>()
        var index = 0
        while (index + stride <= raw.size) {
            val host = if (stride == 6) {
                (raw[index].toInt() and 0xFF).toString() + "." +
                    (raw[index + 1].toInt() and 0xFF) + "." +
                    (raw[index + 2].toInt() and 0xFF) + "." +
                    (raw[index + 3].toInt() and 0xFF)
            } else {
                buildString {
                    for (offset in 0 until 8) {
                        if (length > 0) append(':')
                        append(Integer.toHexString(((raw[index + offset * 2].toInt() and 0xFF) shl 8) or
                            (raw[index + offset * 2 + 1].toInt() and 0xFF)))
                    }
                }
            }
            val portOffset = index + stride - 2
            val port = ((raw[portOffset].toInt() and 0xFF) shl 8) or (raw[portOffset + 1].toInt() and 0xFF)
            if (port > 0) result += PeerAddress(host, port)
            index += stride
        }
        return result
    }

    /** Ne journalise jamais la passkey d'un tracker prive. */
    private fun redact(url: String): String = url.substringBefore('?')

    companion object {
        private const val HEX = "0123456789ABCDEF"

        fun percent(bytes: ByteArray): String {
            val builder = StringBuilder(bytes.size * 3)
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xFF
                val safe = value in '0'.code..'9'.code ||
                    value in 'a'.code..'z'.code ||
                    value in 'A'.code..'Z'.code ||
                    value == '-'.code || value == '_'.code || value == '.'.code || value == '~'.code
                if (safe) {
                    builder.append(value.toChar())
                } else {
                    builder.append('%').append(HEX[value ushr 4]).append(HEX[value and 0x0F])
                }
            }
            return builder.toString()
        }

        fun randomPeerId(): ByteArray {
            val prefix = "-S3150-".toByteArray(Charsets.US_ASCII)
            val random = ByteArray(20)
            java.security.SecureRandom().nextBytes(random)
            prefix.copyInto(random, 0)
            return random
        }

        fun normalizeHash(value: String): String = value.lowercase(Locale.ROOT)
    }
}

class TrackerException(message: String) : Exception(message)
