package com.s3arch.app.torrent

import java.security.MessageDigest

/** Un fichier du torrent, replace dans l'espace lineaire global des octets. */
data class TorrentEntry(
    val path: String,
    val length: Long,
    val offset: Long,
)

/**
 * Metadonnees d'un torrent (fichier `.torrent` ou bloc `info` obtenu par magnet).
 *
 * Tous les calculs de position (piece, bloc, fichier) se font sur un espace lineaire
 * unique : c'est ce qui permet d'ecrire un torrent multi-fichiers sans se soucier des
 * frontieres entre fichiers.
 */
class TorrentMeta(
    val infoHash: ByteArray,
    val name: String,
    val pieceLength: Long,
    val pieceHashes: List<ByteArray>,
    val files: List<TorrentEntry>,
    val totalLength: Long,
    val trackers: List<String>,
) {
    val pieceCount: Int get() = pieceHashes.size
    val infoHashHex: String get() = infoHash.toHex()

    /** Taille reelle de la piece : la derniere est presque toujours plus courte. */
    fun pieceSize(index: Int): Int {
        if (index < 0 || index >= pieceCount) return 0
        val start = index.toLong() * pieceLength
        val remaining = totalLength - start
        if (remaining <= 0L) return 0
        return minOf(pieceLength, remaining).toInt()
    }

    fun fileFor(offset: Long): TorrentEntry? =
        files.firstOrNull { offset >= it.offset && offset < it.offset + it.length }

    fun matches(index: Int, data: ByteArray, length: Int = data.size): Boolean {
        if (index < 0 || index >= pieceCount) return false
        val expected = pieceHashes[index]
        val digest = MessageDigest.getInstance("SHA-1").digest(if (length == data.size) data else data.copyOf(length))
        return digest.contentEquals(expected)
    }

    companion object {

        /** Analyse un fichier `.torrent` complet. */
        fun parse(bytes: ByteArray): TorrentMeta {
            val root = Bencode.decode(bytes) as? BValue.BDict
                ?: throw BencodeException("Fichier .torrent illisible")
            val infoSlice = Bencode.infoSlice(bytes)
                ?: throw BencodeException("Bloc « info » absent du .torrent")
            val info = root.dict("info")
                ?: throw BencodeException("Bloc « info » absent du .torrent")
            val trackers = mutableListOf<String>()
            root.str("announce")?.takeIf { it.isNotBlank() }?.let { trackers += it }
            root.list("announce-list")?.items?.forEach { tier ->
                (tier as? BValue.BList)?.items?.forEach { entry ->
                    (entry as? BValue.BStr)?.text?.takeIf { it.isNotBlank() }?.let { trackers += it }
                }
            }
            return parseInfo(info, infoSlice, trackers.distinct(), root.str("comment"))
        }

        /**
         * Analyse le bloc `info` seul (reponse `ut_metadata` d'un magnet).
         * Les trackers viennent alors du lien magnet.
         */
        fun fromInfo(infoBytes: ByteArray, trackers: List<String>): TorrentMeta {
            val info = Bencode.decode(infoBytes) as? BValue.BDict
                ?: throw BencodeException("Métadonnées du torrent illisibles")
            return parseInfo(info, infoBytes, trackers.distinct(), null)
        }

        private fun parseInfo(
            info: BValue.BDict,
            infoBytes: ByteArray,
            trackers: List<String>,
            comment: String?,
        ): TorrentMeta {
            val hash = MessageDigest.getInstance("SHA-1").digest(infoBytes)
            val pieceLength = info.int("piece length")
                ?: throw BencodeException("Taille de pièce absente")
            if (pieceLength <= 0L || pieceLength > 64L * 1024 * 1024) {
                throw BencodeException("Taille de pièce invalide : $pieceLength")
            }
            val rawPieces = (info["pieces"] as? BValue.BStr)?.bytes
                ?: throw BencodeException("Empreintes de pièces absentes")
            if (rawPieces.size % 20 != 0) {
                throw BencodeException("Empreintes de pièces corrompues")
            }
            val hashes = (0 until rawPieces.size / 20).map { index ->
                rawPieces.copyOfRange(index * 20, index * 20 + 20)
            }

            val rootName = sanitizeSegment(info.str("name") ?: "")
                .ifBlank { "torrent-" + hash.toHex().take(8) }

            val files = mutableListOf<TorrentEntry>()
            var offset = 0L
            val single = info.int("length")
            val multi = info.list("files")
            if (single != null && single >= 0) {
                files += TorrentEntry(rootName, single, 0L)
                offset = single
            } else if (multi != null) {
                multi.items.forEach { item ->
                    val file = item as? BValue.BDict ?: return@forEach
                    val length = file.int("length")?.takeIf { it >= 0 } ?: return@forEach
                    val parts = file.list("path")?.items.orEmpty()
                        .mapNotNull { (it as? BValue.BStr)?.text }
                        .map { sanitizeSegment(it) }
                        .filter { it.isNotBlank() }
                    if (parts.isEmpty()) return@forEach
                    files += TorrentEntry(
                        path = "$rootName/${parts.joinToString("/")}",
                        length = length,
                        offset = offset,
                    )
                    offset += length
                }
            }

            if (files.isEmpty()) throw BencodeException("Torrent sans fichier exploitable")
            if (comment != null) {
                // Le commentaire n'est pas utilise mais sa lecture garantit qu'il ne
                // fait pas echouer l'analyse du reste du dictionnaire.
            }
            return TorrentMeta(
                infoHash = hash,
                name = rootName,
                pieceLength = pieceLength,
                pieceHashes = hashes,
                files = files,
                totalLength = offset,
                trackers = trackers,
            )
        }
    }
}

/**
 * Nettoie un element de chemin annonce par un torrent : jamais de `..`, jamais de
 * separateur, de caractere de controle ni de nom vide (sinon un torrent malveillant
 * ecrirait hors du dossier de telechargement).
 */
fun sanitizeSegment(raw: String): String {
    val cleaned = raw
        .replace('/', '_')
        .replace('\\', '_')
        .replace("\u0000", "")
        .trim()
        .trim('.')
    if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return ""
    return cleaned.take(180)
}
