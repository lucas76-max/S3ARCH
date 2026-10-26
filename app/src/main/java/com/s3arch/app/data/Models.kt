package com.s3arch.app.data

import kotlinx.serialization.Serializable

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
