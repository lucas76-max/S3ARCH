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

/** Fiche TMDB associee a un resultat (affiche, resume, annee, note). */
@Serializable
data class TmdbInfo(
    val title: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("first_air_date") val firstAirDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
) {
    /** Annee extraite de la date de sortie (film) ou de diffusion (serie). */
    val year: String
        get() = (releaseDate.ifBlank { firstAirDate }).take(4)

    /** Affiche adaptee a une vignette de liste. */
    val posterUrl: String?
        get() = posterPath.takeIf { it.isNotBlank() }?.let { "$IMAGE_ROOT/w342$it" }

    /** Affiche en grand, pour la fiche detaillee. */
    val largePosterUrl: String?
        get() = posterPath.takeIf { it.isNotBlank() }?.let { "$IMAGE_ROOT/w500$it" }

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
 * deux fois la meme oeuvre pendant la session.
 */
class TmdbClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = ConcurrentHashMap<String, TmdbInfo?>()

    suspend fun lookup(apiKey: String, title: String, year: Int?): TmdbInfo? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || title.isBlank()) return@withContext null
            val cacheKey = "${title.lowercase()}|${year ?: 0}"
            cache[cacheKey]?.let { return@withContext it }
            if (cache.containsKey(cacheKey)) return@withContext null

            val url = "https://api.themoviedb.org/3/search/movie".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("api_key", apiKey.trim())
                ?.addQueryParameter("language", "fr-FR")
                ?.addQueryParameter("include_adult", "false")
                ?.addQueryParameter("query", title.take(120))
                ?.apply { if (year != null) addQueryParameter("year", year.toString()) }
                ?.build() ?: return@withContext null

            val request = Request.Builder().url(url).get().build()
            val info = runCatching {
                Network.http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLog.warn("TMDB", "HTTP ${response.code} pour « $title »")
                        return@use null
                    }
                    val body = response.body?.string().orEmpty()
                    json.decodeFromString(TmdbResponse.serializer(), body)
                        .results
                        .firstOrNull { it.posterPath.isNotBlank() }
                        ?: json.decodeFromString(TmdbResponse.serializer(), body).results.firstOrNull()
                }
            }.getOrElse { error ->
                AppLog.warn("TMDB", "Recherche impossible : ${error.message}")
                null
            }
            cache[cacheKey] = info
            info
        }
}
