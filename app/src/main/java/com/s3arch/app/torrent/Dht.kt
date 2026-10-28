package com.s3arch.app.torrent

import com.s3arch.app.util.AppLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom

/**
 * Nœud DHT minimal (BEP 5) : il ne sert qu'a **trouver des pairs** pour un magnet.
 *
 * Aucune annonce n'est publiee (S3ARCH ne se declare pas comme source), ce qui suffit
 * pour demarrer un telechargement sans tracker ou quand tous les trackers sont muets.
 */
class Dht(
    private val infoHash: ByteArray,
    private val peerId: ByteArray,
) {
    @Volatile private var running = true
    private val nodeId = ByteArray(20).also { SecureRandom().nextBytes(it) }
    private val socket: DatagramSocket? = runCatching {
        DatagramSocket().apply { soTimeout = 900 }
    }.onFailure { AppLog.warn("DHT", "Socket indisponible : ${it.message}") }.getOrNull()

    private val nodes = LinkedHashMap<PeerAddress, ByteArray>()
    private val queried = HashSet<PeerAddress>()
    private var counter = 0

    /** Cherche des pairs pendant au plus [maxMillis] et les remonte par paquets. */
    fun search(maxMillis: Long, onPeers: (List<PeerAddress>) -> Unit): Int {
        val datagram = socket ?: return 0
        val found = LinkedHashSet<PeerAddress>()
        bootstrap().forEach { nodes.putIfAbsent(it, ByteArray(0)) }
        val deadline = System.currentTimeMillis() + maxMillis
        var lastRound = 0L
        val buffer = ByteArray(4096)
        while (running && System.currentTimeMillis() < deadline && found.size < 120) {
            val now = System.currentTimeMillis()
            if (now - lastRound > 350) {
                sendRound(datagram)
                lastRound = now
            }
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                datagram.receive(packet)
                val fresh = handle(packet.data.copyOf(packet.length))
                if (fresh.isNotEmpty()) {
                    val unseen = fresh.filter { found.add(it) }
                    if (unseen.isNotEmpty()) onPeers(unseen)
                }
            } catch (_: SocketTimeoutException) {
                // Rien recu : on renvoie une salve au tour suivant.
            } catch (e: Exception) {
                if (!running) break
                AppLog.warn("DHT", "Paquet ignoré : ${e.message}")
            }
        }
        return found.size
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
    }

    private fun sendRound(socket: DatagramSocket) {
        val pending = nodes.keys.filter { it !in queried }.take(8)
        if (pending.isEmpty()) {
            // Plus rien a interroger : on relance les amorces.
            bootstrap().forEach { nodes.putIfAbsent(it, ByteArray(0)) }
            return
        }
        pending.forEach { address ->
            queried += address
            val message = query(
                query = "get_peers",
                extra = mapOf("info_hash" to BValue.of(infoHash)),
            )
            runCatching {
                val bytes = Bencode.encode(message)
                socket.send(
                    DatagramPacket(
                        bytes,
                        bytes.size,
                        InetAddress.getByName(address.host),
                        address.port,
                    ),
                )
            }
        }
    }

    private fun query(query: String, extra: Map<String, BValue>): BValue {
        counter++
        val args = LinkedHashMap<String, BValue>()
        args["id"] = BValue.of(nodeId)
        args.putAll(extra)
        return BValue.dict(
            mapOf(
                "t" to BValue.of(counter.toString()),
                "y" to BValue.of("q"),
                "q" to BValue.of(query),
                "a" to BValue.dict(args),
            ),
        )
    }

    private fun handle(data: ByteArray): List<PeerAddress> {
        val dict = Bencode.decodeOrNull(data) as? BValue.BDict ?: return emptyList()
        if (dict.str("y") != "r") return emptyList()
        val response = dict.dict("r") ?: return emptyList()

        val peers = mutableListOf<PeerAddress>()
        response.list("values")?.items.orEmpty().forEach { item ->
            val compact = (item as? BValue.BStr)?.bytes ?: return@forEach
            compactPeer(compact)?.let { peers += it }
        }
        (response["nodes"] as? BValue.BStr)?.bytes?.let { raw ->
            var index = 0
            while (index + 26 <= raw.size && nodes.size < 400) {
                val id = raw.copyOfRange(index, index + 20)
                val host = ipv4(raw, index + 20)
                val port = portAt(raw, index + 24)
                if (host != null && port > 0) {
                    nodes.putIfAbsent(PeerAddress(host, port), id)
                }
                index += 26
            }
        }
        return peers
    }

    private fun compactPeer(bytes: ByteArray): PeerAddress? {
        if (bytes.size != 6) return null
        val host = ipv4(bytes, 0) ?: return null
        val port = portAt(bytes, 4)
        return if (port > 0) PeerAddress(host, port) else null
    }

    private fun ipv4(bytes: ByteArray, offset: Int): String? {
        if (offset + 4 > bytes.size) return null
        return (bytes[offset].toInt() and 0xFF).toString() + "." +
            (bytes[offset + 1].toInt() and 0xFF) + "." +
            (bytes[offset + 2].toInt() and 0xFF) + "." +
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun portAt(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun bootstrap(): List<PeerAddress> = BOOTSTRAP

    companion object {
        private val BOOTSTRAP = listOf(
            PeerAddress("router.bittorrent.com", 6881),
            PeerAddress("dht.transmissionbt.com", 6881),
            PeerAddress("router.utorrent.com", 6881),
            PeerAddress("dht.libtorrent.org", 25401),
        )

        /** Identifiant de pair derive : evite de dependre du hasard pour les tests. */
        fun peerIdFor(seed: String): ByteArray {
            val digest = java.security.MessageDigest.getInstance("SHA-1")
                .digest(seed.toByteArray(Charsets.UTF_8))
            val id = ByteArray(20)
            "-S3150-".toByteArray(Charsets.US_ASCII).copyInto(id)
            digest.copyInto(id, 7, 0, 13)
            return id
        }
    }
}
