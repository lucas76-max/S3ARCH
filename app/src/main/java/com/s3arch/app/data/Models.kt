package com.s3arch.app.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Les trois trackers integres. L'URL de base est modifiable dans les reglages. */
enum class Tracker(
    val id: String,
    val label: String,
    val defaultUrl: String,
    val keyHint: String,
    val docsHint: String,
) {
    C411(
        id = "c411",
        label = "C411",
        defaultUrl = "https://c411.org/api",
        keyHint = "Cle API C411",
        docsHint = "Profil → Parametres → cle API",
    ),
    TR4KER(
        id = "tr4ker",
        label = "TR4KER",
        defaultUrl = "https://tr4ker.net/api/torznab",
        keyHint = "Cle API TR4KER",
        docsHint = "Mon compte → Parametres → cle API",
    ),
    V3X(
        id = "v3x",
        label = "V3X",
        defaultUrl = "https://api.v3x.club/torznab",
        keyHint = "Cle API V3X (scope torznab)",
        docsHint = "Reglages → API → cle scope torznab",
    );

    companion object {
        fun fromId(id: String): Tracker? = entries.firstOrNull { it.id == id }
    }
}

/** Tracker Torznab ajoute par l'utilisateur (compatibilite non garantie). */
@Serializable
data class CustomTracker(
    val id: String,
    val label: String,
    val url: String,
    val key: String,
)

/**
 * Une source interrogeable : soit un tracker integre, soit un endpoint Torznab tiers.
 * C'est cette abstraction qui est utilisee par la recherche et les puces de filtre.
 */
data class TrackerSource(
    val id: String,
    val label: String,
    val url: String,
    val apiKey: String,
    val builtin: Tracker? = null,
) {
    val isCustom: Boolean get() = builtin == null
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

/** Type de contenu deduit des categories Torznab (utile pour le filtrage). */
enum class MediaType(val id: String, val label: String) {
    MOVIE("movie", "Films"),
    SERIES("series", "Séries"),
    MUSIC("music", "Musique"),
    BOOK("book", "Ebooks"),
    APP("app", "Applications"),
    GAME("game", "Jeux"),
    XXX("xxx", "XXX"),
    OTHER("other", "Autres");

    companion object {
        fun fromId(id: String?): MediaType? = entries.firstOrNull { it.id == id }

        /**
         * Deduit le type depuis les identifiants Torznab (2000 = Films, 5000 = TV, ...),
         * puis depuis le libelle de categorie, puis depuis des motifs tres explicites du titre.
         */
        fun infer(categoryIds: List<Int>, categoryText: String?, title: String): MediaType {
            categoryIds.firstOrNull { it >= 1000 }?.let { id ->
                when (id / 1000) {
                    1 -> return GAME
                    2 -> return MOVIE
                    3 -> return MUSIC
                    4 -> return APP
                    5 -> return SERIES
                    6 -> return XXX
                    7 -> return BOOK
                }
            }

            val haystack = (categoryText.orEmpty() + " " + title).lowercase()
            when {
                haystack.containsAny("xxx", "hentai", "porn", "adulte", "erotic") -> return XXX
                haystack.containsAny(
                    "serie", "série", "saison", "season", "episode", "épisode", "emission",
                    "émission", "animation serie", "animé", "anime", "documentaire serie",
                ) -> return SERIES
                haystack.containsAny("film", "movie", "cinema", "cinéma", "documentaire", "concert", "spectacle", "sport") ->
                    return MOVIE
                haystack.containsAny("musique", "music", "album", "karaoke", "karaoké", "podcast", "sample", "audio") ->
                    return MUSIC
                haystack.containsAny("ebook", "livre", "book", "manga", "comics", " bd", "bd ", "presse", "epub") ->
                    return BOOK
                haystack.containsAny("logiciel", "application", "software", "windows", "macos", "linux", "android", "formation") ->
                    return APP
                haystack.containsAny("jeu", "game", "console", "nintendo", "playstation", "xbox", "rom", "emulation", "émulation") ->
                    return GAME
                SERIES_PATTERN.containsMatchIn(title) -> return SERIES
                else -> return OTHER
            }
        }

        private fun String.containsAny(vararg needles: String): Boolean =
            needles.any { this.contains(it) }

        private val SERIES_PATTERN = Regex("\\bS\\d{1,2}\\s?E\\d{1,3}\\b", RegexOption.IGNORE_CASE)
    }
}

/** Ordre de tri des resultats. */
enum class SortMode(val id: String, val label: String) {
    SIZE_ASC("size_asc", "Taille croissante"),
    SIZE_DESC("size_desc", "Taille décroissante"),
    SEEDERS_DESC("seeders", "Seeders décroissants"),
    RECENT("recent", "Plus récents"),
    TITLE("title", "Nom A → Z");

    companion object {
        fun fromId(id: String?): SortMode = entries.firstOrNull { it.id == id } ?: SIZE_ASC
    }
}

/** Applique l'ordre choisi ; les tailles inconnues restent en fin de liste. */
fun List<TorrentResult>.sortedFor(mode: SortMode): List<TorrentResult> = when (mode) {
    SortMode.SIZE_ASC -> sortedWith(
        compareBy({ if (it.sizeBytes > 0) 0 else 1 }, { it.sizeBytes }, { it.title.lowercase() }),
    )
    SortMode.SIZE_DESC -> sortedWith(
        compareBy({ if (it.sizeBytes > 0) 0 else 1 }, { -it.sizeBytes }, { it.title.lowercase() }),
    )
    SortMode.SEEDERS_DESC -> sortedWith(
        compareByDescending<TorrentResult> { it.seeders }.thenBy { it.sizeBytes },
    )
    SortMode.RECENT -> sortedWith(
        compareByDescending<TorrentResult> { parsePublishedAt(it.publishDate) }
            .thenByDescending { it.seeders },
    )
    SortMode.TITLE -> sortedWith(
        compareBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
}

private val RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME

/** Convertit la date de publication Torznab (RFC 822/1123, ISO) en epoch millis. */
fun parsePublishedAt(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    val text = raw.trim()
    runCatching { return OffsetDateTime.parse(text).toInstant().toEpochMilli() }
    runCatching { return ZonedDateTime.parse(text, RFC_1123).toInstant().toEpochMilli() }
    runCatching { return LocalDateTime.parse(text, RFC_1123).atZone(ZoneOffset.UTC).toInstant().toEpochMilli() }
    runCatching { return Instant.parse(text).toEpochMilli() }
    return 0L
}

/** Un resultat de recherche, normalise quel que soit le tracker. */
data class TorrentResult(
    val id: String,
    val title: String,
    val sizeBytes: Long,
    val seeders: Int,
    val leechers: Int,
    val trackerId: String,
    val trackerLabel: String,
    val downloadUrl: String,
    val magnet: String? = null,
    val category: String? = null,
    val publishDate: String? = null,
    val mediaType: MediaType = MediaType.OTHER,
)

/** Etat de la Freebox une fois decouverte. */
data class FreeboxBox(
    val host: String,
    val apiBase: String,
    val apiVersion: String,
)

/** Une tache de telechargement cote Freebox, normalisee. */
data class FreeboxTask(
    val id: String,
    val name: String,
    /** `bt` pour un torrent, `http`/`ftp` pour un simple fichier. */
    val type: String,
    val status: String,
    val rxBytes: Long,
    val totalBytes: Long,
    val rxRate: Long,
    val error: String?,
    val downloadDir: String,
) {
    val progress: Float
        get() = if (totalBytes > 0L) (rxBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val isDone: Boolean
        get() = status == "done" || status == "seeding"

    val isError: Boolean
        get() = status == "error" || status == "stopped"

    /** Chemin complet du fichier sur la Freebox, utilise pour la lecture VLC. */
    val filePath: String
        get() = buildString {
            append(downloadDir.trimEnd('/'))
            if (name.isNotBlank()) {
                append('/')
                append(name)
            }
        }
}

/**
 * Un fichier appartenant a une tache de telechargement Freebox
 * (`GET /downloads/{id}/files`).
 *
 * `previewUrl` est le lien de partage renvoye par la Freebox : il est lisible
 * **sans authentification**, donc directement exploitable par VLC.
 */
data class FreeboxFile(
    val name: String,
    val path: String,
    val mimeType: String,
    val size: Long,
    val previewUrl: String? = null,
) {
    val isMedia: Boolean
        get() = mimeType.startsWith("video/") ||
            mimeType.startsWith("audio/") ||
            name.substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS

    companion object {
        val MEDIA_EXTENSIONS = setOf(
            "mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "mpg", "mpeg",
            "wmv", "flv", "ogv", "mp3", "flac", "m4a", "aac", "ogg", "wav", "opus",
        )
    }
}

/**
 * Choisit le fichier a lire dans une tache : le plus gros media.
 *
 * Un torrent de film contient souvent un dossier, un `.nfo`, des sous-titres et
 * un echantillon de quelques Mo ; le plus gros fichier video est donc le film.
 * C'est aussi ce qui corrige la lecture d'un torrent qui telecharge un dossier
 * (l'ancien code passait le dossier a VLC, qui ne pouvait rien lire).
 */
fun List<FreeboxFile>.playableMedia(): FreeboxFile? =
    filter { it.isMedia && it.size > 0L }.maxByOrNull { it.size }
        ?: filter { it.isMedia }.maxByOrNull { it.size }

/** Telechargement suivi par l'application (persiste entre deux lancements). */
@Serializable
data class TrackedDownload(
    val taskId: String,
    val title: String,
    val tracker: String,
    val folder: String,
    val addedAt: Long,
    val sizeBytes: Long = 0L,
    val seeders: Int = 0,
)

/** Reglages persistants de l'application. */
data class AppSettings(
    val c411Key: String = "",
    val c411Url: String = Tracker.C411.defaultUrl,
    val tr4kerKey: String = "",
    val tr4kerUrl: String = Tracker.TR4KER.defaultUrl,
    val v3xKey: String = "",
    val v3xUrl: String = Tracker.V3X.defaultUrl,
    val enabledTrackers: Set<String> = Tracker.entries.map { it.id }.toSet(),
    val customTrackers: List<CustomTracker> = emptyList(),
    val freeboxHost: String = "mafreebox.freebox.fr",
    val freeboxApiBase: String = "/api/v8",
    val freeboxApiVersion: String = "8",
    val freeboxAppToken: String = "",
    val freeboxDownloadDir: String = "/Disque dur/S3ARCH",
    val onboardingDone: Boolean = false,
    val dynamicColor: Boolean = true,
    val sortMode: SortMode = SortMode.SIZE_ASC,
    /** Types de contenus a afficher ; ensemble vide = aucun filtre. */
    val enabledTypes: Set<String> = emptySet(),
) {
    fun keyFor(tracker: Tracker): String = when (tracker) {
        Tracker.C411 -> c411Key
        Tracker.TR4KER -> tr4kerKey
        Tracker.V3X -> v3xKey
    }

    fun urlFor(tracker: Tracker): String = when (tracker) {
        Tracker.C411 -> c411Url
        Tracker.TR4KER -> tr4kerUrl
        Tracker.V3X -> v3xUrl
    }

    /** Toutes les sources disponibles (integrees + tiers). */
    fun sources(): List<TrackerSource> = buildList {
        Tracker.entries.forEach { tracker ->
            add(
                TrackerSource(
                    id = tracker.id,
                    label = tracker.label,
                    url = urlFor(tracker),
                    apiKey = keyFor(tracker),
                    builtin = tracker,
                ),
            )
        }
        customTrackers.forEach { custom ->
            add(
                TrackerSource(
                    id = custom.id,
                    label = custom.label,
                    url = custom.url,
                    apiKey = custom.key,
                ),
            )
        }
    }

    /** Sources effectivement interrogeables : activees et avec une cle. */
    fun enabledSources(): List<TrackerSource> =
        sources().filter { it.id in enabledTrackers && it.isConfigured }

    fun isConfigured(tracker: Tracker): Boolean = keyFor(tracker).isNotBlank()

    val configuredTrackers: List<Tracker>
        get() = Tracker.entries.filter { isConfigured(it) }

    val freeboxConfigured: Boolean
        get() = freeboxAppToken.isNotBlank()
}
