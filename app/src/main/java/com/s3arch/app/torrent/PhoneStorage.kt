package com.s3arch.app.torrent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import com.s3arch.app.util.AppLog
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Canal d'acces aleatoire en lecture **et** ecriture.
 *
 * Indispensable pour reprendre un torrent : sans relecture, impossible de verifier
 * les pieces deja sur le disque, et la reprise repartait de zero. Les fichiers SAF
 * (`ContentResolver`) ne donnent qu'un `FileOutputStream`, donc un canal en
 * ecriture seule : d'ou cette abstraction et l'implementation par `pread`/`pwrite`.
 */
interface RandomChannel {
    fun position(newPosition: Long)
    fun size(): Long
    fun read(dst: ByteBuffer): Int
    fun write(src: ByteBuffer): Int
    fun force()
    fun close()
}

/** Canal d'ecriture d'un fichier de torrent, avec sa ressource a liberer. */
class StorageChannel(
    val channel: RandomChannel,
    private val release: () -> Unit = {},
) : AutoCloseable {
    override fun close() {
        runCatching { channel.close() }
        runCatching { release() }
    }
}

/** Fichier classique (dossier public ou prive) : le `FileChannel` suffit. */
class FileRandomChannel(private val channel: FileChannel) : RandomChannel {
    override fun position(newPosition: Long) {
        channel.position(newPosition)
    }

    override fun size(): Long = channel.size()
    override fun read(dst: ByteBuffer): Int = channel.read(dst)
    override fun write(src: ByteBuffer): Int = channel.write(src)
    override fun force() {
        runCatching { channel.force(false) }
    }

    override fun close() {
        runCatching { channel.close() }
    }
}

/**
 * Document SAF lu et ecrit par descripteur.
 *
 * `FileOutputStream` ne sait pas relire : passer par `Os.pread`/`Os.pwrite` donne un
 * acces complet au meme fichier, sans seconde ouverture ni cache incoherent.
 */
class DescriptorRandomChannel(private val descriptor: ParcelFileDescriptor) : RandomChannel {

    private var position = 0L

    override fun position(newPosition: Long) {
        position = newPosition
    }

    override fun size(): Long =
        runCatching { Os.fstat(descriptor.fileDescriptor).st_size }.getOrDefault(0L)

    override fun read(dst: ByteBuffer): Int {
        val size = dst.remaining()
        if (size <= 0) return 0
        val chunk = minOf(size, MAX_IO)
        val buffer = ByteArray(chunk)
        val count = try {
            Os.pread(descriptor.fileDescriptor, buffer, 0, chunk, position)
        } catch (e: Exception) {
            throw IOException("Lecture impossible : ${e.message}")
        }
        if (count <= 0) return if (count == 0) -1 else count
        dst.put(buffer, 0, count)
        position += count
        return count
    }

    override fun write(src: ByteBuffer): Int {
        val size = src.remaining()
        if (size <= 0) return 0
        val chunk = minOf(size, MAX_IO)
        val buffer = ByteArray(chunk)
        src.get(buffer, 0, chunk)
        var written = 0
        while (written < chunk) {
            val count = try {
                Os.pwrite(descriptor.fileDescriptor, buffer, written, chunk - written, position + written)
            } catch (e: Exception) {
                throw IOException("Écriture impossible : ${e.message}")
            }
            if (count <= 0) throw IOException("Écriture interrompue")
            written += count
        }
        position += chunk
        return chunk
    }

    override fun force() {
        runCatching { Os.fsync(descriptor.fileDescriptor) }
    }

    override fun close() {
        runCatching { descriptor.close() }
    }

    private companion object {
        /** Un bloc BitTorrent fait 16 Kio : inutile de copier des morceaux plus gros. */
        const val MAX_IO = 256 * 1024
    }
}

/**
 * Destination des telechargements sur le telephone.
 *
 * Deux implementations : un dossier classique (dossier public `Download/S3ARCH` quand
 * Android l'autorise, sinon dossier prive de l'application) et un dossier choisi par
 * l'utilisateur via le selecteur de documents (obligatoire depuis Android 11 pour
 * ecrire dans `Telechargements`).
 */
interface PhoneStorage {
    val label: String
    val canPlayInPlace: Boolean
    fun freeBytes(): Long
    fun open(relativePath: String, expectedLength: Long = 0L): StorageChannel
    fun exists(relativePath: String): Boolean
    fun delete(relativePath: String): Boolean
    fun deleteTree(relativeDir: String): Boolean
    fun uri(relativePath: String): String?
}

/** Dossier reel (public ou prive) : acces direct, allocation creuse possible. */
class FolderStorage(private val root: File, private val displayLabel: String? = null) : PhoneStorage {

    override val label: String get() = displayLabel ?: root.absolutePath
    override val canPlayInPlace: Boolean get() = true

    override fun freeBytes(): Long = runCatching {
        val stat = StatFs(root.absolutePath)
        stat.availableBytes
    }.getOrElse {
        runCatching { StatFs(Environment.getDataDirectory().absolutePath).availableBytes }.getOrDefault(0L)
    }

    override fun open(relativePath: String, expectedLength: Long): StorageChannel {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        if (!file.parentFile!!.exists()) throw IOException("Dossier impossible a creer : ${file.parent}")
        if (!file.exists() && !file.createNewFile()) {
            throw IOException("Fichier impossible a creer : ${file.name}")
        }
        val random = java.io.RandomAccessFile(file, "rw")
        // Allocation creuse : le systeme connait la taille sans ecrire les zeros.
        if (expectedLength > random.length()) runCatching { random.setLength(expectedLength) }
        return StorageChannel(FileRandomChannel(random.channel)) { runCatching { random.close() } }
    }

    override fun exists(relativePath: String): Boolean = File(root, relativePath).exists()

    override fun delete(relativePath: String): Boolean = runCatching {
        File(root, relativePath).delete()
    }.getOrDefault(false)

    override fun deleteTree(relativeDir: String): Boolean = runCatching {
        File(root, relativeDir).deleteRecursively()
    }.getOrDefault(false)

    override fun uri(relativePath: String): String? {
        val file = File(root, relativePath)
        return if (file.exists()) Uri.fromFile(file).toString() else null
    }

    override fun toString(): String = "FolderStorage(${root.absolutePath})"
}

/** Dossier choisi via `ACTION_OPEN_DOCUMENT_TREE` (Telechargements sur Android 11+). */
class TreeStorage(private val context: Context, private val treeUri: Uri) : PhoneStorage {

    private val root: DocumentFile? by lazy {
        DocumentFile.fromTreeUri(context, treeUri)
    }

    override val label: String
        get() = root?.name?.takeIf { it.isNotBlank() } ?: "Dossier choisi"

    override val canPlayInPlace: Boolean get() = true

    override fun freeBytes(): Long = runCatching {
        StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes
    }.getOrElse {
        runCatching { StatFs(Environment.getDataDirectory().absolutePath).availableBytes }.getOrDefault(0L)
    }

    private fun segments(relativePath: String): List<String> =
        relativePath.trim('/').split('/').map { sanitizeSegment(it) }.filter { it.isNotBlank() }

    private fun locate(relativePath: String, createDirs: Boolean, createFile: Boolean): DocumentFile? {
        var directory = root ?: return null
        val parts = segments(relativePath)
        if (parts.isEmpty()) return directory
        for (name in parts.dropLast(1)) {
            val existing = directory.findFile(name)
            directory = when {
                existing != null && existing.isDirectory -> existing
                createDirs -> directory.createDirectory(name) ?: return null
                else -> return null
            }
        }
        val fileName = parts.last()
        val existing = directory.findFile(fileName)
        if (existing != null) return existing
        return if (createFile) directory.createFile("application/octet-stream", fileName) else null
    }

    override fun open(relativePath: String, expectedLength: Long): StorageChannel {
        val document = locate(relativePath, createDirs = true, createFile = true)
            ?: throw IOException("Création impossible dans le dossier choisi")
        val descriptor = context.contentResolver.openFileDescriptor(document.uri, "rw")
            ?: throw IOException("Ouverture du fichier impossible (autorisation du dossier ?)")
        return StorageChannel(DescriptorRandomChannel(descriptor))
    }

    override fun exists(relativePath: String): Boolean =
        locate(relativePath, createDirs = false, createFile = false)?.exists() == true

    override fun delete(relativePath: String): Boolean =
        runCatching { locate(relativePath, createDirs = false, createFile = false)?.delete() == true }
            .getOrDefault(false)

    override fun deleteTree(relativeDir: String): Boolean {
        var directory = root ?: return false
        val parts = segments(relativeDir)
        for (name in parts) {
            directory = directory.findFile(name) ?: return false
        }
        return runCatching { directory.delete() }.getOrDefault(false)
    }

    override fun uri(relativePath: String): String? =
        locate(relativePath, createDirs = false, createFile = false)?.uri?.toString()

    override fun toString(): String = "TreeStorage($treeUri)"
}

/**
 * Construit le stockage a utiliser.
 *
 * Ordre : dossier choisi par l'utilisateur, puis `Telechargements/S3ARCH` quand le
 * systeme l'autorise encore, puis le dossier prive de l'application (toujours
 * accessible, sans aucune permission).
 */
object PhoneStorageFactory {

    const val DEFAULT_FOLDER = "S3ARCH"

    fun forTree(context: Context, treeUri: String?): PhoneStorage {
        val parsed = treeUri?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (parsed != null) {
            val storage = TreeStorage(context, parsed)
            if (storage.freeBytes() > 0L) return storage
        }
        return fallback(context)
    }

    fun fallback(context: Context): PhoneStorage {
        publicDownloads(context)?.let { return it }
        val appDir = (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
        return FolderStorage(File(appDir, DEFAULT_FOLDER), "Dossier de l'application")
    }

    /** `Downloads/S3ARCH` n'est inscriptible en direct que jusqu'a Android 10. */
    private fun publicDownloads(context: Context): PhoneStorage? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return null
        val base = runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }
            .getOrNull() ?: return null
        val dir = File(base, DEFAULT_FOLDER)
        val writable = runCatching {
            if (!dir.exists()) dir.mkdirs()
            File(dir, ".write-test").let {
                it.createNewFile()
                it.delete()
                true
            }
        }.getOrDefault(false)
        if (!writable) {
            AppLog.warn("Stockage", "Download/S3ARCH non accessible en écriture : repli sur l'application")
            return null
        }
        return FolderStorage(dir, "Téléchargements/S3ARCH")
    }

    /** Conserve l'autorisation du dossier choisi entre deux lancements. */
    fun persistPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { AppLog.warn("Stockage", "Autorisation durable du dossier refusée : ${it.message}") }
    }
}
