package com.s3arch.app.data

import java.util.Base64

/**
 * Regles de chemin de l'API Freebox OS.
 *
 * Les deux endpoints n'ont pas la meme convention, d'ou les erreurs `path_not_found`
 * puis `bt_file_error` :
 *
 * - `/fs/mkdir/` attend un parent **sans** slash final : b64("/Disque dur") ;
 * - `download_dir` attend un chemin **avec** slash final :
 *   b64("/Disque dur/Téléchargements/S3ARCH/").
 */
internal object FreeboxPaths {

    /**
     * Dossier reellement utilise par defaut : S3ARCH se place dans le dossier de
     * telechargement standard de la Freebox, et non directement a la racine du disque.
     */
    const val DEFAULT_DIR = "/Disque dur/Téléchargements/S3ARCH"

    /** Ancien chemin par defaut, migre automatiquement vers [DEFAULT_DIR]. */
    const val LEGACY_DEFAULT_DIR = "/Disque dur/S3ARCH"

    /** Chemin absolu, sans double slash, termine par un « / ». */
    fun normalizeDir(dir: String, default: String = DEFAULT_DIR): String {
        var value = dir.trim().replace("\\", "/")
        if (value.isEmpty()) value = default
        while (value.contains("//")) value = value.replace("//", "/")
        if (!value.startsWith("/")) value = "/$value"
        if (!value.endsWith("/")) value = "$value/"
        return value
    }

    /** Chaque niveau avec le parent a transmettre a `fs/mkdir` (jamais de slash final). */
    fun levels(target: String): List<Pair<String, String>> {
        var parent = "/"
        return target.trim('/')
            .split('/')
            .filter { it.isNotBlank() }
            .map { part ->
                val level = parent to part
                parent = if (parent == "/") "/$part" else "$parent/$part"
                level
            }
    }

    /** Parent d'un chemin de fichier (sans slash final), tel qu'attendu par `fs/mkdir`. */
    fun parentOf(path: String): String {
        val trimmed = normalizeDir(path).trimEnd('/')
        return trimmed.substringBeforeLast('/', missingDelimiterValue = "")
    }

    fun base64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
}
