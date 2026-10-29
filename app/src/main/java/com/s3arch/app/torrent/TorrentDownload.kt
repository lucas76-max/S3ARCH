package com.s3arch.app.torrent

import com.s3arch.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Telechargement d'un torrent deja connu (metadonnees disponibles).
 *
 * Le picking est sequentiel : c'est le comportement qui permet de relire un media
 * pendant qu'il se telecharge, et il evite de garder une carte de rarete en memoire
 * pour des torrents de plusieurs dizaines de milliers de pieces.
 */
internal class TorrentDownload(
    private val meta: TorrentMeta,
    private val storage: TorrentStorage,
    private val trackerClient: TrackerClient,
    private val peerId: ByteArray,
    private val donePieces: BooleanArray,
    /** Continue a envoyer les pieces aux autres pairs une fois le fichier complet. */
    private val seeding: Boolean,
    private val onProgress: (doneBytes: Long, rateBps: Long, peers: Int, uploadedBytes: Long) -> Unit,
    private val onComplete: (doneBytes: Long) -> Unit,
) {
    private class PieceJob(val index: Int, val size: Int, firstWire: PeerWire) {
        val data = ByteArray(size)
        val totalBlocks = (size + BLOCK_SIZE - 1) / BLOCK_SIZE
        val blocks = BooleanArray(totalBlocks)
        var receivedBlocks = 0
        val createdAt = System.currentTimeMillis()
        /**
         * Pairs qui travaillent sur cette piece : un seul en temps normal, plusieurs
         * en fin de telechargement (le premier bloc recu gagne).
         */
        val wires = mutableSetOf(firstWire)
        /** Blocs deja demandes, pair par pair : chacun a son propre compteur. */
        private val requests = HashMap<PeerWire, BooleanArray>()
        private val lastBlock = HashMap<PeerWire, Long>()

        fun requestsOf(wire: PeerWire): BooleanArray =
            requests.getOrPut(wire) { BooleanArray(totalBlocks) }

        fun received(wire: PeerWire, block: Int) {
            blocks[block] = true
            requestsOf(wire)[block] = false
            lastBlock[wire] = System.currentTimeMillis()
        }

        /** Blocs demandes a ce pair et pas encore arrives. */
        fun pending(wire: PeerWire): Int {
            val asked = requestsOf(wire)
            var count = 0
            for (block in asked.indices) if (asked[block] && !blocks[block]) count++
            return count
        }

        fun silenceOf(wire: PeerWire): Long =
            System.currentTimeMillis() - (lastBlock[wire] ?: createdAt)
    }

    private val lock = Any()
    private val inFlight = HashMap<Int, PieceJob>()
    /** Pieces encore manquantes : sert a detecter la fin de telechargement. */
    private val missingLeft = AtomicInteger(donePieces.count { !it })
    private val wires = LinkedHashMap<PeerAddress, PeerWire>()
    private val knownPeers = LinkedHashSet<PeerAddress>()
    /** Pieces possedees par chaque pair, pour ne pas lui demander l'impossible. */
    private val peerBits = java.util.concurrent.ConcurrentHashMap<PeerWire, BooleanArray>()
    /**
     * Pairs muets : ils restent connectes sans jamais envoyer un bloc. Sans cette
     * liste, on se reconnecte en boucle au meme pair et le torrent ne depasse plus
     * quelques pour cent.
     */
    private val deadUntil = java.util.concurrent.ConcurrentHashMap<PeerAddress, Long>()

    @Volatile private var finished = false
    @Volatile private var lastError: String? = null
    @Volatile private var uploadedBytes = 0L
    private var lastTickAt = System.currentTimeMillis()
    private var lastTickBytes = 0L
    private var rateBps = 0L

    /** Limite de pieces simultanees : un gros torrent ne doit pas saturer la memoire. */
    private val maxPeers = (96L * 1024 * 1024 / meta.pieceLength.coerceAtLeast(1L))
        .toInt()
        .coerceIn(4, 28)

    /** Budget memoire global : pieces en vol, tous pairs confondus. */
    private val pieceBudget = (96L * 1024 * 1024 / meta.pieceLength.coerceAtLeast(1L))
        .toInt()
        .coerceIn(6, 64)

    /**
     * Dernieres pieces : la meme piece est demandee a plusieurs pairs en meme temps.
     * Sans cela, le dernier pour cent depend d'un seul pair et avance a la vitesse de
     * sa latence, piece par piece.
     */
    private val endgameThreshold = (meta.pieceCount / 50).coerceIn(8, 64)

    private fun inEndgame(): Boolean = missingLeft.get() <= endgameThreshold

    /** Un pair muet libere plus vite sa piece quand il n'en reste que quelques-unes. */
    private fun idleTimeout(): Long = if (inEndgame()) ENDGAME_IDLE_MS else PIECE_IDLE_MS

    private fun completedBytes(): Long {
        var total = 0L
        for (index in donePieces.indices) {
            if (donePieces[index]) total += meta.pieceSize(index)
        }
        return total
    }

    private fun remainingBytes(): Long = (meta.totalLength - completedBytes()).coerceAtLeast(0L)

    suspend fun run() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val discovery = scope.launch { discoverPeers(scope) }
        try {
            var lastTick = 0L
            // Apres completion, la boucle ne continue que pour le partage.
            while ((!finished || seeding) && lastError == null && currentCoroutineContext().isActive) {
                val batch = synchronized(lock) {
                    val nowMs = System.currentTimeMillis()
                    knownPeers
                        .filter { it !in wires && (deadUntil[it] ?: 0L) <= nowMs }
                        .take((maxPeers - wires.size).coerceAtLeast(0))
                }
                batch.forEach { address ->
                    synchronized(lock) {
                        if (wires.size >= maxPeers || address in wires) return@synchronized
                        val wire = PeerWire(address, meta.infoHash, peerId, PeerListener())
                        wires[address] = wire
                        scope.launch { wire.run() }
                    }
                }
                val now = System.currentTimeMillis()
                if (now - lastTick >= 1_000) {
                    // Une piece confiee a un pair qui vient de tomber doit repartir
                    // chez quelqu'un d'autre, sinon la file se bloque pour toujours.
                    val stalled = mutableListOf<PeerWire>()
                    val toAssign = mutableListOf<PeerWire>()
                    synchronized(lock) {
                        deadUntil.entries.removeIf { it.value <= now }
                        wires.entries.removeIf { it.value.isClosed }
                        val idleLimit = idleTimeout()
                        val iterator = inFlight.entries.iterator()
                        while (iterator.hasNext()) {
                            val job = iterator.next().value
                            job.wires.removeIf { it.isClosed }
                            if (job.wires.isEmpty()) {
                                iterator.remove()
                                continue
                            }
                            // Chaque pair est juge separement : en fin de telechargement
                            // deux pairs travaillent sur la meme piece, et seul le muet
                            // doit etre remplace.
                            val mute = job.wires.filter { job.silenceOf(it) >= idleLimit }
                            mute.forEach { wire ->
                                job.wires.remove(wire)
                                deadUntil[wire.address] = now + PEER_RETRY_MS
                            }
                            stalled += mute
                            if (job.wires.isEmpty()) iterator.remove()
                        }
                        if (!finished) toAssign += wires.values.filter { !it.isClosed }
                    }
                    // Hors verrou : fermer ou interroger un socket ne doit pas figer
                    // les autres pairs.
                    stalled.forEach { runCatching { it.close("pair muet") } }
                    toAssign.forEach { wire -> assign(wire, peerBits[wire]) }
                    lastTick = now
                    val done = completedBytes()
                    val elapsed = (now - lastTickAt).coerceAtLeast(1L)
                    rateBps = ((done - lastTickBytes) * 1000L / elapsed).coerceAtLeast(0L)
                    lastTickBytes = done
                    lastTickAt = now
                    onProgress(done, rateBps, synchronized(lock) { wires.size }, uploadedBytes)
                }
                delay(600)
            }
            if (lastError != null) throw TorrentException(lastError ?: "Torrent interrompu")
        } finally {
            discovery.cancel()
            scope.cancel()
            synchronized(lock) {
                wires.values.forEach { runCatching { it.close("fin de session") } }
                wires.clear()
                inFlight.clear()
            }
        }
    }

    private suspend fun discoverPeers(scope: CoroutineScope) {
        val dht = Dht(meta.infoHash, peerId)
        scope.launch {
            runCatching {
                dht.search(90_000) { fresh ->
                    synchronized(lock) { knownPeers += fresh }
                }
            }
        }
        var lastAnnounce = 0L
        try {
            while (!finished || seeding) {
                val now = System.currentTimeMillis()
                if (now - lastAnnounce >= 45_000) {
                    lastAnnounce = now
                    meta.trackers.forEach { url ->
                        scope.launch {
                            val response = runCatching {
                                trackerClient.announce(
                                    url = url,
                                    infoHash = meta.infoHash,
                                    peerId = peerId,
                                    port = 6881,
                                    left = remainingBytes(),
                                    downloaded = completedBytes(),
                                )
                            }.getOrNull()
                            response?.peers?.let { peers ->
                                synchronized(lock) { knownPeers += peers }
                            }
                        }
                    }
                }
                delay(1_500)
            }
        } finally {
            dht.stop()
        }
    }

    private fun assign(wire: PeerWire, bits: BooleanArray?) {
        val jobs = mutableListOf<PieceJob>()
        synchronized(lock) {
            if (finished || wire.isClosed) return
            if (inFlight.values.count { it.wires.contains(wire) } >= MAX_PIECES_PER_PEER) return
            val fresh = pickIndex(bits)
            if (fresh != null && inFlight.size < pieceBudget) {
                val created = PieceJob(fresh, meta.pieceSize(fresh), wire)
                inFlight[fresh] = created
                jobs += created
            } else if (inEndgame()) {
                // Dernieres pieces : on les demande aussi a d'autres pairs, le premier
                // bloc recu gagne. C'est ce qui evite de ramper a 98 %.
                val duplicate = pickDuplicate(wire, bits)
                val job = duplicate?.let { inFlight[it] }
                if (job != null && job.wires.add(wire)) jobs += job
            }
        }
        // Les requetes partent hors du verrou : une ecriture socket lente bloquait
        // sinon tous les autres pairs en meme temps.
        jobs.forEach { job -> requestNextBlocks(job, wire) }
    }

    /**
     * Premiere piece manquante que ce pair possede reellement.
     *
     * Un pair qui ne possede aucune piece manquante ne recoit rien : lui confier une
     * piece qu'il n'a pas le laissait muet, puis le faisait blacklister une minute,
     * ce qui vidait l'essaim au moment ou il ne reste que quelques pieces.
     */
    private fun pickIndex(bits: BooleanArray?): Int? {
        for (index in donePieces.indices) {
            if (donePieces[index] || inFlight.containsKey(index)) continue
            if (bits == null || (index < bits.size && bits[index])) return index
        }
        return null
    }

    /** En fin de telechargement : une piece deja en vol que ce pair possede aussi. */
    private fun pickDuplicate(wire: PeerWire, bits: BooleanArray?): Int? {
        if (bits == null) return null
        for (job in inFlight.values) {
            if (job.wires.contains(wire)) continue
            val index = job.index
            if (index < bits.size && bits[index]) return index
        }
        return null
    }

    /** Demande les blocs qui manquent a cette piece, pour ce pair precisement. */
    private fun requestNextBlocks(job: PieceJob, wire: PeerWire) {
        if (wire.isClosed) return
        val asked = job.requestsOf(wire)
        var pending = job.pending(wire)
        for (block in 0 until job.totalBlocks) {
            if (pending >= MAX_BLOCKS_IN_FLIGHT) break
            if (job.blocks[block] || asked[block]) continue
            val begin = block * BLOCK_SIZE
            val length = minOf(BLOCK_SIZE, job.size - begin)
            if (runCatching { wire.request(job.index, begin, length) }.isFailure) return
            asked[block] = true
            pending++
        }
    }

    private fun handleBlock(wire: PeerWire, index: Int, begin: Int, data: ByteArray) {
        var completed: PieceJob? = null
        var pending: PieceJob? = null
        synchronized(lock) {
            val job = inFlight[index] ?: return
            if (!job.wires.contains(wire)) return
            val blockIndex = begin / BLOCK_SIZE
            if (blockIndex !in 0 until job.totalBlocks || job.blocks[blockIndex]) return
            val length = minOf(data.size, job.size - begin)
            if (length <= 0) return
            data.copyInto(job.data, begin, 0, length)
            job.received(wire, blockIndex)
            job.receivedBlocks++
            if (job.receivedBlocks < job.totalBlocks) pending = job
            if (job.receivedBlocks == job.totalBlocks) completed = job
        }
        // Requete suivante hors verrou, comme dans assign().
        pending?.let { requestNextBlocks(it, wire) }
        completed?.let { finishPiece(it, wire) }
    }

    private fun finishPiece(job: PieceJob, from: PeerWire) {
        val peers = mutableListOf<PeerWire>()
        var closeAll = false
        synchronized(lock) {
            if (inFlight[job.index] === job) inFlight.remove(job.index)
            if (finished) return
            if (!meta.matches(job.index, job.data)) {
                AppLog.warn("Torrent", "Pièce ${job.index} invalide reçue de ${from.address}")
                deadUntil[from.address] = System.currentTimeMillis() + PEER_RETRY_MS
                from.close("pièce invalide")
                return
            }
            val written = runCatching {
                storage.write(job.index.toLong() * meta.pieceLength, job.data, job.data.size)
            }
            if (written.isFailure) {
                lastError = "Écriture impossible : ${written.exceptionOrNull()?.message}"
                AppLog.error("Torrent", lastError ?: "écriture")
                return
            }
            donePieces[job.index] = true
            missingLeft.decrementAndGet()
            peers += job.wires
            if (donePieces.all { it }) {
                if (!finished) {
                    finished = true
                    val done = completedBytes()
                    AppLog.info("Torrent", "Terminé : $done octets (partage : $seeding)")
                    onComplete(done)
                    onProgress(done, 0L, wires.size, uploadedBytes)
                }
                // En partage, les connexions restent ouvertes pour servir les pairs.
                if (!seeding) closeAll = true
            }
        }
        // Annonce et fermeture hors verrou : ecrire sur un socket lent ne doit pas
        // bloquer la fin des pieces des autres pairs.
        peers.forEach { wire -> runCatching { wire.sendHave(job.index) } }
        if (closeAll) {
            peers.forEach { runCatching { it.close("torrent terminé") } }
        } else {
            // Le pair enchaine tout de suite sur une autre piece : sans cela il restait
            // inactif jusqu'au prochain battement, ce qui hachait le debit.
            peers.forEach { wire -> if (!wire.isClosed) assign(wire, peerBits[wire]) }
        }
    }

    private fun release(wire: PeerWire) {
        synchronized(lock) {
            val iterator = inFlight.entries.iterator()
            while (iterator.hasNext()) {
                val job = iterator.next().value
                job.wires.remove(wire)
                if (job.wires.isEmpty()) iterator.remove()
            }
        }
    }

    private inner class PeerListener : PeerWire.Listener {
        private var bits: BooleanArray? = null
        private var interested = false

        /** Un pair ne debloque nos requetes que si l'on s'est declare interesse. */
        private fun ensureInterested(wire: PeerWire) {
            if (interested) return
            interested = true
            runCatching { wire.sendInterested() }
        }

        override fun onReady(wire: PeerWire, metadataSize: Int, utMetadataId: Int) {
            // En partage, le pair doit savoir ce que nous avons pour nous demander.
            if (donePieces.any { it }) runCatching { wire.sendBitfield(donePieces) }
            ensureInterested(wire)
        }

        override fun onBitfield(wire: PeerWire, bits: BooleanArray) {
            this.bits = bits
            peerBits[wire] = bits
            ensureInterested(wire)
            assign(wire, bits)
        }

        override fun onHave(wire: PeerWire, index: Int) {
            val array = bits ?: BooleanArray(meta.pieceCount).also {
                bits = it
                peerBits[wire] = it
            }
            if (index in array.indices) array[index] = true
            ensureInterested(wire)
            if (index in donePieces.indices && !donePieces[index]) assign(wire, array)
        }

        override fun onUnchoke(wire: PeerWire) = assign(wire, bits)

        override fun onChoke(wire: PeerWire) = release(wire)

        override fun onBlock(wire: PeerWire, index: Int, begin: Int, data: ByteArray) =
            handleBlock(wire, index, begin, data)

        override fun onInterested(wire: PeerWire) {
            // Un pair veut nos pieces : on accepte, sinon il ne demandera jamais rien.
            runCatching { wire.sendUnchoke() }
        }

        override fun onRequest(wire: PeerWire, index: Int, begin: Int, length: Int) {
            serveBlock(wire, index, begin, length)
        }

        override fun onClosed(wire: PeerWire, reason: String?) {
            peerBits.remove(wire)
            synchronized(lock) { wires.remove(wire.address) }
            release(wire)
        }
    }

    /** Sert un bloc demande par un pair, uniquement si la piece est complete. */
    private fun serveBlock(wire: PeerWire, index: Int, begin: Int, length: Int) {
        if (index !in donePieces.indices || !donePieces[index]) return
        val pieceSize = meta.pieceSize(index)
        if (begin < 0 || length <= 0 || begin + length > pieceSize) return
        // Un bloc BitTorrent ne depasse jamais 16 Kio + marge.
        if (length > 128 * 1024) return
        val buffer = ByteArray(length)
        val got = runCatching {
            storage.read(index.toLong() * meta.pieceLength + begin, buffer, length)
        }.getOrDefault(0)
        if (got <= 0) return
        runCatching { wire.sendPiece(index, begin, buffer.copyOf(got)) }
        uploadedBytes += got
    }

    companion object {
        const val BLOCK_SIZE = 16 * 1024
        private const val MAX_BLOCKS_IN_FLIGHT = 8
        /** Pieces qu'un meme pair peut mener de front : c'est ce qui sature le lien. */
        private const val MAX_PIECES_PER_PEER = 3
        /** Sans un seul bloc recu pendant ce delai, la piece repart chez un autre pair. */
        private const val PIECE_IDLE_MS = 25_000L
        /** Meme chose en fin de telechargement : le contournement prime sur la lenteur. */
        private const val ENDGAME_IDLE_MS = 8_000L
        /** Un pair muet n'est pas retente avant ce delai. */
        private const val PEER_RETRY_MS = 60_000L
    }
}

class TorrentException(message: String) : Exception(message)

/**
 * Recupere le bloc `info` d'un magnet aupres de ses pairs (BEP 9).
 *
 * Le metadata est decoupe en morceaux de 16 Kio ; chaque morceau est recolle puis
 * l'ensemble est verifie par SHA-1 : un pair qui renvoie des octets differents est
 * simplement ignore.
 */
class MetadataFetcher(
    private val link: MagnetLink,
    private val peerId: ByteArray,
    private val trackerClient: TrackerClient,
) {
    fun fetch(): TorrentMeta? {
        val hash = link.infoHash.hexToBytes() ?: return null
        val peers = LinkedHashSet<PeerAddress>()
        val lock = Any()
        val dht = Dht(hash, peerId)
        val dhtThread = Thread {
            runCatching {
                dht.search(45_000) { fresh -> synchronized(lock) { peers += fresh } }
            }
        }.apply { isDaemon = true; name = "s3arch-dht" }
        dhtThread.start()

        try {
            val deadline = System.currentTimeMillis() + 90_000
            val tried = HashSet<PeerAddress>()
            var round = 0
            while (System.currentTimeMillis() < deadline) {
                round++
                if (round % 4 == 0) announce(hash, peers, lock)
                val batch = synchronized(lock) { peers.filterNot { it in tried }.take(10) }
                if (batch.isEmpty()) {
                    if (round == 1) announce(hash, peers, lock)
                    Thread.sleep(1_200)
                    continue
                }
                batch.forEach { address ->
                    tried += address
                    val meta = fetchFrom(address, hash, link.trackers)
                    if (meta != null) {
                        AppLog.info("Torrent", "Métadonnées reçues de $address")
                        return meta
                    }
                }
            }
        } finally {
            dht.stop()
        }
        return null
    }

    private fun announce(hash: ByteArray, peers: MutableSet<PeerAddress>, lock: Any) {
        val trackers = link.trackers.ifEmpty { EMPTY }
        val threads = trackers.take(6).map { url ->
            Thread {
                val response = runBlocking {
                    runCatching {
                        trackerClient.announce(url, hash, peerId, 6881, left = 1L)
                    }.getOrNull()
                }
                response?.peers?.let { synchronized(lock) { peers += it } }
            }.apply { isDaemon = true }
        }
        threads.forEach { it.start() }
        threads.forEach { runCatching { it.join(12_000) } }
    }

    private fun fetchFrom(address: PeerAddress, hash: ByteArray, trackers: List<String>): TorrentMeta? {
        val collector = MetadataCollector(hash, trackers)
        val wire = PeerWire(address, hash, peerId, collector)
        collector.wire = wire
        val worker = Thread {
            runCatching { wire.run() }
        }.apply { isDaemon = true }
        worker.start()
        worker.join(25_000)
        wire.close("métadonnées")
        return collector.result()
    }

    private inner class MetadataCollector(
        private val hash: ByteArray,
        private val trackers: List<String>,
    ) : PeerWire.Listener {
        @Volatile var wire: PeerWire? = null
        private var size = 0
        private val pieces = HashMap<Int, ByteArray>()
        private var requested = false
        @Volatile private var output: TorrentMeta? = null

        fun result(): TorrentMeta? = output

        override fun onReady(wire: PeerWire, metadataSize: Int, utMetadataId: Int) {
            if (metadataSize > 0) size = metadataSize
            if (size <= 0 || size > 8 * 1024 * 1024) {
                wire.close("taille de métadonnées inconnue")
                return
            }
            if (!requested) {
                requested = true
                val count = (size + METADATA_PIECE - 1) / METADATA_PIECE
                for (index in 0 until count) wire.requestMetadata(index)
            }
        }

        override fun onExtended(wire: PeerWire, extensionId: Int, payload: ByteArray) {
            if (extensionId != OUR_UT_METADATA_ID) return
            val headerLength = runCatching { Bencode.consumedLength(payload) }.getOrDefault(0)
            if (headerLength <= 0) return
            val header = Bencode.decodeOrNull(payload.copyOfRange(0, headerLength)) as? BValue.BDict ?: return
            if (header.int("msg_type") != 1L) return
            val index = header.int("piece")?.toInt() ?: return
            val data = payload.copyOfRange(headerLength, payload.size)
            if (data.isEmpty()) return
            synchronized(pieces) {
                pieces[index] = data
                val complete = assemble() ?: return
                output = TorrentMeta.fromInfo(complete, trackers)
                wire.close("métadonnées complètes")
            }
        }

        private fun assemble(): ByteArray? {
            val count = (size + METADATA_PIECE - 1) / METADATA_PIECE
            if (pieces.size < count) return null
            val buffer = java.io.ByteArrayOutputStream(size)
            for (index in 0 until count) {
                val piece = pieces[index] ?: return null
                buffer.write(piece)
            }
            val bytes = buffer.toByteArray()
            val digest = java.security.MessageDigest.getInstance("SHA-1").digest(bytes)
            return if (digest.contentEquals(hash)) bytes else null
        }
    }

    companion object {
        private const val METADATA_PIECE = 16 * 1024
        private const val OUR_UT_METADATA_ID = 1
        private val EMPTY = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
        )
    }
}
