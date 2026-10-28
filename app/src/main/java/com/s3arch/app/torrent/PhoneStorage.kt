package com.s3arch.app.torrent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.documentfile.provider.DocumentFile
import com.s3arch.app.util.AppLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel

/** Canal d'ecriture d'un fichier de torrent, avec sa ressource a liberer. */
class StorageChannel(
    val channel: FileChannel,
    private val release: () -> Unit,
) : AutoCloseable {
    override fun close() {
        runCatching { channel.close() }
        runCatching { release() }
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
        return StorageChannel(random.channel) { runCatching { random.close() } }
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
        val stream = FileOutputStream(descriptor.fileDescriptor)
        return StorageChannel(stream.channel) {
            runCatching { stream.close() }
            runCatching { descriptor.close() }
        }
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
