package com.s3arch.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sauvegarde complete de la configuration dans un fichier JSON.
 *
 * Toutes les valeurs ont un defaut : un fichier produit par une version anterieure
 * reste importable, et une cle absente n'efface rien (voir `SettingsRepository`).
 */
@Serializable
data class S3archConfig(
    val app: String = "S3ARCH",
    val schema: Int = SCHEMA,
    val exportedAt: Long = System.currentTimeMillis(),
    /** Cles API des trackers integres, indexees par identifiant (`c411`, `v3x`...). */
    val trackerKeys: Map<String, String> = emptyMap(),
    val trackerUrls: Map<String, String> = emptyMap(),
    val enabledTrackers: Set<String> = emptySet(),
    val customTrackers: List<CustomTracker> = emptyList(),
    val tmdbKey: String = "",
    val sortMode: String = SortMode.SIZE_ASC.id,
    val enabledTypes: Set<String> = emptySet(),
    val hideZeroSeeders: Boolean = true,
    val filters: SearchFilters = SearchFilters(),
    val dynamicColor: Boolean = true,
    val accentColor: String = "azure",
    val downloadTargets: Set<String> = setOf(TARGET_PHONE),
    val phoneTreeUri: String = "",
    val seedAfterDownload: Boolean = false,
    val freeboxHost: String = "",
    val freeboxApiBase: String = "",
    val freeboxApiVersion: String = "",
    val freeboxAppToken: String = "",
    val freeboxDownloadDir: String = "",
    val vpnConfig: String = "",
    val vpnLogin: String = "",
    val freeboxLanIp: String = "",
) {
    companion object {
        const val SCHEMA = 1
    }
}

object ConfigExport {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Construit le fichier a partir des reglages courants. */
    fun from(settings: AppSettings): S3archConfig = S3archConfig(
        trackerKeys = Tracker.entries.associate { it.id to settings.keyFor(it) },
        trackerUrls = Tracker.entries.associate { it.id to settings.urlFor(it) },
        enabledTrackers = settings.enabledTrackers,
        customTrackers = settings.customTrackers,
        tmdbKey = settings.tmdbKey,
        sortMode = settings.sortMode.id,
        enabledTypes = settings.enabledTypes,
        hideZeroSeeders = settings.hideZeroSeeders,
        filters = settings.filters,
        dynamicColor = settings.dynamicColor,
        accentColor = settings.accentColor,
        downloadTargets = settings.downloadTargets,
        phoneTreeUri = settings.phoneTreeUri,
        seedAfterDownload = settings.seedAfterDownload,
        freeboxHost = settings.freeboxHost,
        freeboxApiBase = settings.freeboxApiBase,
        freeboxApiVersion = settings.freeboxApiVersion,
        freeboxAppToken = settings.freeboxAppToken,
        freeboxDownloadDir = settings.freeboxDownloadDir,
        vpnConfig = settings.vpnConfig,
        vpnLogin = settings.vpnLogin,
        freeboxLanIp = settings.freeboxLanIp,
    )

    fun encode(settings: AppSettings): String =
        json.encodeToString(S3archConfig.serializer(), from(settings))

    /** Lit un fichier exporte ; `null` si le contenu n'est pas une configuration S3ARCH. */
    fun decode(text: String): S3archConfig? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        val config = runCatching {
            json.decodeFromString(S3archConfig.serializer(), trimmed)
        }.getOrNull() ?: return null
        // Un fichier d'une version plus recente reste lisible tant que le schema tient.
        if (config.schema > S3archConfig.SCHEMA) return null
        return config
    }
}
