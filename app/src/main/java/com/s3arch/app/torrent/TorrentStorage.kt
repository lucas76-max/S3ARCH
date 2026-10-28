package com.s3arch.app.torrent

import java.io.IOException
import java.nio.ByteBuffer

/**
 * Ecrit les pieces d'un torrent dans le stockage choisi.
 *
 * Les fichiers sont ouverts a la demande puis gardes ouverts : un torrent multi-fichiers
 * ecrit souvent plusieurs fichiers pour une meme piece, rouvrir a chaque bloc serait
 * tres couteux sur un stockage SAF.
 */
class TorrentStorage(
    private val storage: PhoneStorage,
    private val meta: TorrentMeta,
) {
    private val channels = LinkedHashMap<String, StorageChannel>()

    private fun channel(entry: TorrentEntry): StorageChannel =
        channels.getOrPut(entry.path) { storage.open(entry.path, entry.length) }

    /** Cree les fichiers si besoin et garantit leur taille logique (sans effacer). */
    fun prepare() {
        meta.files.forEach { entry ->
            if (entry.length <= 0L) return@forEach
            val channel = channel(entry)
            // Une taille logique nulle empeche certaines ecritures sur SAF : on
            // ecrit un octet a la fin pour etendre le fichier sans le remplir.
            if (runCatching { channel.channel.size() }.getOrDefault(0L) < entry.length) {
                runCatching {
                    channel.channel.write(ByteBuffer.wrap(byteArrayOf(0)), entry.length - 1)
                }
            }
        }
    }

    @Synchronized
    fun write(offset: Long, data: ByteArray, length: Int) {
        var written = 0
        while (written < length) {
            val position = offset + written
            val entry = meta.fileFor(position)
                ?: throw IOException("Position $position hors du torrent")
            val local = position - entry.offset
            val chunk = minOf(entry.length - local, (length - written).toLong()).toInt()
            if (chunk <= 0) throw IOException("Fichier ${entry.path} de taille insuffisante")
            val channel = channel(entry).channel
            channel.position(local)
            val buffer = ByteBuffer.wrap(data, written, chunk)
            while (buffer.hasRemaining()) {
                if (channel.write(buffer) < 0) throw IOException("Ecriture interrompue sur ${entry.path}")
            }
            written += chunk
        }
    }

    /** Relit une piece complete (verification a la reprise). */
    @Synchronized
    fun read(offset: Long, destination: ByteArray, length: Int): Int {
        var read = 0
        while (read < length) {
            val position = offset + read
            val entry = meta.fileFor(position)
                ?: return read
            val local = position - entry.offset
            val chunk = minOf(entry.length - local, (length - read).toLong()).toInt()
            if (chunk <= 0) return read
            val channel = channel(entry).channel
            channel.position(local)
            val buffer = ByteBuffer.wrap(destination, read, chunk)
            var got = 0
            while (buffer.hasRemaining()) {
                val count = channel.read(buffer)
                if (count <= 0) break
                got += count
            }
            read += got
            if (got < chunk) return read
        }
        return read
    }

    fun readPiece(index: Int): ByteArray {
        val size = meta.pieceSize(index)
        val buffer = ByteArray(size)
        val got = read(index.toLong() * meta.pieceLength, buffer, size)
        return if (got == size) buffer else ByteArray(0)
    }

    @Synchronized
    fun close() {
        channels.values.forEach { runCatching { it.close() } }
        channels.clear()
    }

    /** Supprime tous les fichiers du torrent (dossier racine inclus). */
    fun erase() {
        close()
        runCatching { storage.deleteTree(meta.name) }
        meta.files.forEach { runCatching { storage.delete(it.path) } }
    }

    /** Emplacement lisible du plus gros fichier (lecture dans l'application). */
    fun largestMediaUri(): String? {
        val media = meta.files
            .filter { it.length > 0 && it.path.substringAfterLast('.').lowercase() in MEDIA }
            .maxByOrNull { it.length }
            ?: meta.files.maxByOrNull { it.length }
            ?: return null
        return storage.uri(media.path)
    }

    fun largestFilePath(): String? {
        val media = meta.files
            .filter { it.length > 0 && it.path.substringAfterLast('.').lowercase() in MEDIA }
            .maxByOrNull { it.length }
            ?: return null
        return media.path
    }

    companion object {
        val MEDIA = setOf(
            "mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "mpg", "mpeg",
            "wmv", "flv", "ogv", "mp3", "flac", "m4a", "aac", "ogg", "wav", "opus",
        )
    }
}
