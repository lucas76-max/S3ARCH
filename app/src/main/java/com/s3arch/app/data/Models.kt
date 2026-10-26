package com.s3arch.app.data

import kotlinx.serialization.Serializable

/** Les trois trackers pris en charge. L'URL de base est modifiable dans les reglages. */
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

/** Un resultat de recherche, normalise quel que soit le tracker. */
data class TorrentResult(
    val id: String,
    val title: String,
    val sizeBytes: Long,
    val seeders: Int,
    val leechers: Int,
    val tracker: Tracker,
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
    val freeboxHost: String = "mafreebox.freebox.fr",
    val freeboxApiBase: String = "/api/v8",
    val freeboxApiVersion: String = "8",
    val freeboxAppToken: String = "",
    val freeboxDownloadDir: String = "/Disque dur/S3ARCH",
    val onboardingDone: Boolean = false,
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

    fun isConfigured(tracker: Tracker): Boolean = keyFor(tracker).isNotBlank()

    val configuredTrackers: List<Tracker>
        get() = Tracker.entries.filter { isConfigured(it) }

    val freeboxConfigured: Boolean
        get() = freeboxAppToken.isNotBlank()
}
