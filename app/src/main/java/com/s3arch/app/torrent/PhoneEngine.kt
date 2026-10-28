package com.s3arch.app.torrent

import android.content.Context
import com.s3arch.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Moteur de telechargement **sur le telephone**, independant de la Freebox.
 *
 * Il vit au niveau de l'application (pas du ViewModel) pour que le service de premier
 * plan puisse continuer a telecharger quand l'ecran est ferme. Les telechargements
 * lances depuis un fichier `.torrent` ne survivent pas a un redemarrage du processus :
 * le fichier n'est pas recopie dans les reglages, il faut le reimporter.
 */
class PhoneEngine private constructor(context: Context) {

    companion object {
        @Volatile private var instance: PhoneEngine? = null

        fun get(context: Context): PhoneEngine =
            instance ?: synchronized(this) {
                instance ?: PhoneEngine(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloads = MutableStateFlow<List<PhoneDownload>>(emptyList())
    val downloads: StateFlow<List<PhoneDownload>> = _downloads.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val torrentBytes = ConcurrentHashMap<String, ByteArray>()

    @Volatile private var storage: PhoneStorage = PhoneStorageFactory.fallback(appContext)
    @Volatile private var peerId: ByteArray = TrackerClient.randomPeerId()
    @Volatile private var seedAfterDownload = false

    private val trackerClient = TrackerClient()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val storageLabel: String get() = storage.label

    fun freeBytes(): Long = runCatching { storage.freeBytes() }.getOrDefault(0L)

    fun updateStorage(treeUri: String?) {
        storage = PhoneStorageFactory.forTree(appContext, treeUri)
    }

    fun setPeerId(id: ByteArray) {
        if (id.size == 20) peerId = id
    }

    /**
     * Active ou coupe le partage des torrents deja termines.
     *
     * Desactiver suffit : les sessions en partage sont arretees et repassent en
     * « Termine », sans relancer de telechargement.
     */
    fun setSeeding(enabled: Boolean) {
        if (seedAfterDownload == enabled) return
        seedAfterDownload = enabled
        if (enabled) return
        _downloads.value.filter { it.state == PhoneDownload.STATE_SEEDING }.forEach { record ->
            jobs.remove(record.id)?.cancel()
            update(record.id) {
                it.copy(state = PhoneDownload.STATE_DONE, rateBps = 0L, peers = 0)
            }
        }
    }

    /** Installe la liste persistee : les telechargements actifs repassent en pause. */
    fun restore(records: List<PhoneDownload>) {
        if (_downloads.value.isNotEmpty()) return
        _downloads.value = records.map { record ->
            if (record.isActive) record.copy(state = PhoneDownload.STATE_PAUSED, rateBps = 0L, peers = 0)
            else record
        }
    }

    fun enqueue(source: PhoneSource, title: String?, tracker: String? = null): PhoneDownload {
        val (name, sourceText, kind) = describe(source, title)
        val record = PhoneDownload(
            id = UUID.randomUUID().toString().take(8),
            name = name,
            source = sourceText,
            kind = kind,
            state = PhoneDownload.STATE_QUEUED,
            totalBytes = (source as? PhoneSource.Magnet)?.link?.sizeBytes ?: 0L,
            tracker = tracker?.takeIf { it.isNotBlank() } ?: "Téléphone",
        )
        if (source is PhoneSource.TorrentFile) torrentBytes[record.id] = source.bytes
        _downloads.update { listOf(record) + it }
        start(record.id)
        return record
    }

    fun pause(id: String) {
        val record = current(id)
        jobs.remove(id)?.cancel()
        if (record != null && record.isSeeding) {
            // Arreter un partage ne doit pas remettre le fichier en « en pause ».
            update(id) {
                it.copy(state = PhoneDownload.STATE_DONE, rateBps = 0L, peers = 0)
            }
            return
        }
        update(id) {
            it.copy(
                state = if (it.isDone) it.state else PhoneDownload.STATE_PAUSED,
                rateBps = 0L,
                peers = 0,
            )
        }
    }

    fun resume(id: String) {
        val record = current(id) ?: return
        if (record.isDone) return
        if (jobs.containsKey(id)) return
        update(id) { it.copy(state = PhoneDownload.STATE_QUEUED, error = null, rateBps = 0L) }
        start(id)
    }

    fun remove(id: String, deleteFiles: Boolean) {
        jobs.remove(id)?.cancel()
        val record = current(id)
        if (deleteFiles && record != null) {
            // Le dossier racine du torrent porte le nom du torrent ; le fichier direct
            // est son unique chemin. Les deux cas sont couverts sans risquer d'effacer
            // le dossier de telechargement entier.
            if (record.kind == PhoneDownload.KIND_TORRENT) {
                storage.deleteTree(record.name)
            }
            if (record.savePath.isNotBlank()) storage.delete(record.savePath)
        }
        torrentBytes.remove(id)
        _downloads.update { list -> list.filterNot { it.id == id } }
    }

    fun current(id: String): PhoneDownload? = _downloads.value.firstOrNull { it.id == id }

    /** URI lisible d'un telechargement termine, pour la lecture dans l'application. */
    fun mediaUri(id: String): String? {
        val record = current(id) ?: return null
        if (!record.isDone) return null
        val path = record.savePath.takeIf { it.isNotBlank() } ?: record.name
        return runCatching { storage.uri(path) }.getOrNull()
    }

    private fun update(id: String, transform: (PhoneDownload) -> PhoneDownload) {
        _downloads.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun fail(id: String, message: String) {
        AppLog.error("Téléphone", message)
        update(id) {
            it.copy(
                state = if (it.isPaused) it.state else PhoneDownload.STATE_ERROR,
                error = message,
                rateBps = 0L,
                peers = 0,
            )
        }
    }

    private fun start(id: String) {
        jobs.remove(id)?.cancel()
        val record = current(id) ?: return
        val job = scope.launch {
            try {
                when (val source = sourceOf(record)) {
                    is PhoneSource.Magnet -> downloadTorrent(id, source, verifyExisting = record.doneBytes > 0L)
                    is PhoneSource.TorrentFile -> downloadTorrent(id, source, verifyExisting = record.doneBytes > 0L)
                    is PhoneSource.Link -> resolveLink(id, source)
                    null -> fail(id, "Source illisible : magnet, lien ou fichier .torrent attendu")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(id, e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
            } finally {
                jobs.remove(id)
            }
        }
        jobs[id] = job
    }

    private fun sourceOf(record: PhoneDownload): PhoneSource? {
        if (record.kind == PhoneDownload.KIND_TORRENT) {
            torrentBytes[record.id]?.let { return PhoneSource.TorrentFile(it, record.name) }
        }
        return phoneSourceOf(record.source)
    }

    // ------------------------------------------------------------------ torrents

    private suspend fun downloadTorrent(
        id: String,
        source: PhoneSource,
        verifyExisting: Boolean,
    ) {
        val meta = when (source) {
            is PhoneSource.TorrentFile -> withContext(Dispatchers.IO) { TorrentMeta.parse(source.bytes) }
            is PhoneSource.Magnet -> {
                update(id) {
                    it.copy(
                        state = PhoneDownload.STATE_METADATA,
                        name = source.link.displayName,
                        totalBytes = source.link.sizeBytes ?: it.totalBytes,
                    )
                }
                val fetched = withContext(Dispatchers.IO) {
                    MetadataFetcher(source.link, peerId, trackerClient).fetch()
                } ?: throw TorrentException(
                    "Métadonnées introuvables : aucun pair n'a répondu. " +
                        "Vérifiez la clé API du tracker et votre connexion.",
                )
                fetched
            }
            is PhoneSource.Link -> withContext(Dispatchers.IO) {
                TorrentMeta.parse(fetchTorrentBytes(source.url))
            }
        }

        spaceProblem("sur le téléphone", meta.totalLength, storage.freeBytes())?.let {
            throw TorrentException(it)
        }

        val target = TorrentStorage(storage, meta)
        target.prepare()
        update(id) {
            it.copy(
                name = meta.name,
                totalBytes = meta.totalLength,
                state = PhoneDownload.STATE_CHECKING,
                savePath = target.largestFilePath() ?: meta.name,
                tracker = when (source) {
                    is PhoneSource.Magnet -> it.tracker
                    else -> it.tracker
                },
            )
        }

        val done = BooleanArray(meta.pieceCount)
        if (verifyExisting) {
            for (index in done.indices) {
                if (!currentCoroutineContext().isActive) return
                val piece = target.readPiece(index)
                if (piece.isNotEmpty() && meta.matches(index, piece)) done[index] = true
            }
            update(id) {
                it.copy(doneBytes = done.indices.sumOf { i -> if (done[i]) meta.pieceSize(i).toLong() else 0L })
            }
        } else {
            update(id) { it.copy(doneBytes = 0L) }
        }

        update(id) { it.copy(state = PhoneDownload.STATE_DOWNLOADING) }
        val seeding = seedAfterDownload
        val download = TorrentDownload(
            meta = meta,
            storage = target,
            trackerClient = trackerClient,
            peerId = peerId,
            donePieces = done,
            seeding = seeding,
            onProgress = { bytes, rate, peers, uploaded ->
                update(id) {
                    if (!it.isActive) it
                    else it.copy(
                        doneBytes = bytes,
                        rateBps = rate,
                        peers = peers,
                        uploadedBytes = uploaded,
                        totalBytes = meta.totalLength,
                    )
                }
            },
            onComplete = { total ->
                update(id) {
                    it.copy(
                        state = if (seeding) PhoneDownload.STATE_SEEDING else PhoneDownload.STATE_DONE,
                        doneBytes = total,
                        totalBytes = meta.totalLength,
                        rateBps = 0L,
                        savePath = target.largestFilePath() ?: it.savePath,
                        error = null,
                    )
                }
            },
        )
        try {
            download.run()
        } finally {
            target.close()
        }

        val complete = done.all { it }
        // Un partage arrete volontairement garde son etat « Termine ».
        val settled = current(id)?.let { it.isDone || it.isSeeding || it.isPaused } == true
        if (!complete && !settled && current(id)?.isActive == true) {
            fail(id, "Téléchargement interrompu : plus aucun pair disponible")
        }
    }

    private fun fetchTorrentBytes(url: String): ByteArray {
        val request = Request.Builder().url(url).header("User-Agent", "S3ARCH/1.5").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} pour le .torrent")
            val body = response.body ?: throw IOException("Réponse vide pour le .torrent")
            val bytes = body.bytes()
            if (bytes.size > 32 * 1024 * 1024) throw IOException("Fichier .torrent trop volumineux")
            return bytes
        }
    }

    // ------------------------------------------------------------------ liens directs

    private suspend fun resolveLink(id: String, source: PhoneSource.Link) {
        val url = source.url
        if (isTorrentUrl(url) || looksLikeTorrent(url)) {
            downloadTorrent(id, PhoneSource.Link(url, source.suggestedName), verifyExisting = false)
            return
        }
        runDirect(id, url, source.suggestedName)
    }

    private fun isTorrentUrl(url: String): Boolean =
        url.substringBefore('?').lowercase().endsWith(".torrent")

    /** Un lien sans extension peut quand meme renvoyer un .torrent (API de tracker). */
    private fun looksLikeTorrent(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-1023")
            .header("User-Agent", "S3ARCH/1.5")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body ?: return@use false
            val head = body.byteStream().readNBytes(1024)
            head.isNotEmpty() && head[0] == 'd'.code.toByte() &&
                String(head, Charsets.ISO_8859_1).contains("info")
        }
    }.getOrDefault(false)

    private suspend fun runDirect(id: String, url: String, suggestedName: String?) {
        val record = current(id) ?: return
        val existing = if (record.kind == PhoneDownload.KIND_LINK) record.doneBytes else 0L
        val builder = Request.Builder().url(url).header("User-Agent", "S3ARCH/1.5").get()
        if (existing > 0L) builder.header("Range", "bytes=$existing-")

        withContext(Dispatchers.IO) {
            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw IOException("HTTP ${response.code} ${response.message}")
                }
                val body = response.body ?: throw IOException("Réponse vide")
                val resumed = existing > 0L && response.code == 206
                val start = if (resumed) existing else 0L
                val total = totalBytes(response.header("Content-Range"), body.contentLength(), start)
                val name = sanitizeSegment(
                    fileName(response.header("Content-Disposition"), url, suggestedName),
                ).ifBlank { "telechargement" }

                spaceProblem("sur le téléphone", (total - start).coerceAtLeast(0L), storage.freeBytes())
                    ?.let { throw IOException(it) }

                update(id) {
                    it.copy(
                        kind = PhoneDownload.KIND_LINK,
                        name = if (it.name.isBlank() || it.name == "Téléchargement") name else it.name,
                        state = PhoneDownload.STATE_DOWNLOADING,
                        totalBytes = if (total > 0) total else it.totalBytes,
                        savePath = name,
                        doneBytes = start,
                        error = null,
                    )
                }

                storage.open(name, if (total > 0) total else 0L).use { target ->
                    target.channel.position(start)
                    val buffer = ByteArray(64 * 1024)
                    var done = start
                    var windowStart = System.currentTimeMillis()
                    var windowBytes = done
                    var rate = 0L
                    val input = body.byteStream()
                    while (currentCoroutineContext().isActive) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        val slice = if (count == buffer.size) buffer else buffer.copyOf(count)
                        target.channel.write(java.nio.ByteBuffer.wrap(slice))
                        done += count
                        val now = System.currentTimeMillis()
                        if (now - windowStart >= 800) {
                            rate = ((done - windowBytes) * 1000L / (now - windowStart)).coerceAtLeast(0L)
                            windowStart = now
                            windowBytes = done
                            update(id) {
                                it.copy(doneBytes = done, rateBps = rate, totalBytes = if (total > 0) total else done)
                            }
                        }
                    }
                    runCatching { target.channel.force(false) }
                    val finalTotal = if (total > 0) total else done
                    if (total > 0 && done < total) {
                        update(id) { it.copy(doneBytes = done, rateBps = 0L, state = PhoneDownload.STATE_PAUSED) }
                    } else {
                        update(id) {
                            it.copy(
                                state = PhoneDownload.STATE_DONE,
                                doneBytes = finalTotal,
                                totalBytes = finalTotal,
                                rateBps = 0L,
                                savePath = name,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun totalBytes(contentRange: String?, contentLength: Long, start: Long): Long {
        val range = contentRange?.substringAfter('/', "")?.toLongOrNull()
        if (range != null && range > 0) return range
        if (contentLength > 0) return contentLength + start
        return 0L
    }

    private fun fileName(disposition: String?, url: String, suggested: String?): String {
        val fromHeader = disposition
            ?.substringAfter("filename*=UTF-8''", "")
            ?.substringBefore(';')
            ?.takeIf { it.isNotBlank() }
            ?: disposition
                ?.substringAfter("filename=", "")
                ?.trim('"', ' ')
                ?.takeIf { it.isNotBlank() }
        if (fromHeader != null) return runCatching {
            java.net.URLDecoder.decode(fromHeader, "UTF-8")
        }.getOrDefault(fromHeader)
        val fromUrl = url.substringBefore('?').substringAfterLast('/')
        if (fromUrl.isNotBlank() && fromUrl.contains('.')) return fromUrl
        return suggested?.takeIf { it.isNotBlank() } ?: "telechargement"
    }

    private fun describe(source: PhoneSource, title: String?): Triple<String, String, String> = when (source) {
        is PhoneSource.Magnet -> Triple(
            title?.takeIf { it.isNotBlank() } ?: source.link.displayName,
            magnetUri(source.link),
            PhoneDownload.KIND_TORRENT,
        )
        is PhoneSource.TorrentFile -> Triple(
            title?.takeIf { it.isNotBlank() }
                ?: source.fileName.removeSuffix(".torrent").ifBlank { "Torrent" },
            "torrent:${source.fileName}",
            PhoneDownload.KIND_TORRENT,
        )
        is PhoneSource.Link -> Triple(
            title?.takeIf { it.isNotBlank() } ?: source.suggestedName ?: "Téléchargement",
            source.url,
            PhoneDownload.KIND_TORRENT,
        )
    }

    private fun magnetUri(link: MagnetLink): String = buildString {
        append("magnet:?xt=urn:btih:").append(link.infoHash)
        link.name?.let { append("&dn=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
        link.trackers.forEach { append("&tr=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
    }
}
