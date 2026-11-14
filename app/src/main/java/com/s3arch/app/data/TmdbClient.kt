package com.s3arch.app.data

import com.s3arch.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Fiche TMDB associee a un resultat (affiche, resume, annee, note).
 *
 * Tous les champs renvoyes par l'API sont nullables : TMDB renvoie `null` pour
 * `poster_path` sur beaucoup de titres, et une simple valeur nulle ferait echouer
 * le decodage de toute la reponse.
 */
@Serializable
data class TmdbInfo(
    val title: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
) {
    val displayTitle: String get() = title.orEmpty()

    val displayOverview: String get() = overview.orEmpty()

    /** Annee extraite de la date de sortie (film) ou de diffusion (serie). */
    val year: String
        get() = releaseDate.orEmpty().ifBlank { firstAirDate.orEmpty() }.take(4)

    val rating: Double get() = voteAverage ?: 0.0

    /** Affiche adaptee a une vignette de liste. */
    val posterUrl: String?
        get() = posterPath?.takeIf { it.isNotBlank() }?.let { "$IMAGE_ROOT/w342$it" }

    /** Affiche en grand, pour la fiche detaillee. */
    val largePosterUrl: String?
        get() = posterPath?.takeIf { it.isNotBlank() }?.let { "$IMAGE_ROOT/w500$it" }

    companion object {
        private const val IMAGE_ROOT = "https://image.tmdb.org/t/p"
    }
}

@Serializable
private data class TmdbResponse(val results: List<TmdbInfo> = emptyList())

/**
 * Recherche TMDB pour illustrer les resultats.
 *
 * La cle est facultative : sans elle, aucun appel n'est fait et l'interface affiche
 * simplement un remplacement. Chaque titre est mis en cache pour ne jamais interroger
 * deux fois la meme oeuvre pendant la session. Aucune exception ne remonte : une fiche
 * manquante ne doit jamais interrompre une recherche.
 */
class TmdbClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val cache = ConcurrentHashMap<String, String?>()

    suspend fun lookup(apiKey: String, title: String, year: Int?): TmdbInfo? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || title.isBlank()) return@withContext null
            val cacheKey = "${title.lowercase()}|${year ?: 0}"
            if (cache.containsKey(cacheKey)) {
                return@withContext cache[cacheKey]?.let { parse(it) }
            }
            val payload = runCatching { fetch(apiKey, title, year) }.getOrElse { error ->
                AppLog.warn("TMDB", "Recherche impossible : ${error.message ?: error.javaClass.simpleName}")
                null
            }
            cache[cacheKey] = payload
            payload?.let { parse(it) }
        }

    private fun fetch(apiKey: String, title: String, year: Int?): String? {
        val url = "https://api.themoviedb.org/3/search/movie".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("api_key", apiKey.trim())
            ?.addQueryParameter("language", "fr-FR")
            ?.addQueryParameter("include_adult", "false")
            ?.addQueryParameter("query", title.take(120))
            ?.apply { if (year != null) addQueryParameter("year", year.toString()) }
            ?.build() ?: return null

        val request = Request.Builder().url(url).get().build()
        Network.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLog.warn("TMDB", "HTTP ${response.code} pour « $title »")
                return null
            }
            return response.body?.string()
        }
    }

    /** Lecture du JSON, isolee pour etre testable sans reseau. */
    internal fun parse(body: String): TmdbInfo? {
        if (body.isBlank()) return null
        return runCatching {
            val response = json.decodeFromString(TmdbResponse.serializer(), body)
            response.results.firstOrNull { !it.posterPath.isNullOrBlank() } ?: response.results.firstOrNull()
        }.getOrElse { error ->
            AppLog.warn("TMDB", "Réponse illisible : ${error.javaClass.simpleName}")
            null
        }
    }
}
