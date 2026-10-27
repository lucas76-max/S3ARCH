package com.s3arch.app.data

import android.content.Context
import com.s3arch.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Release GitHub publiee pour S3ARCH. */
data class AppUpdate(
    /** Version sans le prefixe « v » (ex. « 1.3.0 »). */
    val version: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkName: String,
    val apkSize: Long,
)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tag: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0L,
)

/**
 * Verification des mises a jour via l'API GitHub Releases, et telechargement de l'APK.
 *
 * La comparaison se fait sur la version de `BuildConfig` : une release dont le tag est
 * superieur (au sens numerique) est proposee. Le bouton « Ignorer » memorise la version
 * concernee cote reglages, pas ici.
 */
class UpdateRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Les APK font plusieurs dizaines de Mo : aucun delai global sur le telechargement. */
    private val downloadClient = Network.http.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Derniere release stable plus recente que [currentVersion], sinon `null`. */
    suspend fun latest(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val payload = runCatching {
            Network.http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    AppLog.warn("Update", "GitHub a repondu HTTP ${response.code}")
                    return@withContext null
                }
                body
            }
        }.getOrElse { error ->
            AppLog.warn("Update", "Verification impossible : ${error.message}")
            return@withContext null
        }

        val release = runCatching {
            json.decodeFromString(GithubRelease.serializer(), payload)
        }.getOrElse { error ->
            AppLog.warn("Update", "Release GitHub illisible : ${error.message}")
            return@withContext null
        }
        if (release.draft || release.prerelease) return@withContext null

        val version = release.tag.trim().removePrefix("v").removePrefix("V")
        if (version.isBlank() || compareVersions(version, currentVersion) <= 0) {
            AppLog.info("Update", "A jour (version en ligne : ${version.ifBlank { "inconnue" }})")
            return@withContext null
        }

        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        AppLog.info(
            "Update",
            "Version $version disponible" + if (apk == null) " (aucun APK joint)" else " (${apk.name})",
        )
        AppUpdate(
            version = version,
            name = release.name.ifBlank { "S3ARCH $version" },
            notes = release.body.trim(),
            pageUrl = release.htmlUrl.ifBlank { RELEASES_PAGE },
            apkUrl = apk?.downloadUrl?.takeIf { it.isNotBlank() },
            apkName = apk?.name?.takeIf { it.isNotBlank() } ?: "S3ARCH-$version.apk",
            apkSize = apk?.size ?: 0L,
        )
    }

    /**
     * Telecharge l'APK dans le dossier prive de l'application et renvoie le fichier.
     * [onProgress] recoit une valeur entre 0 et 1 (0 si la taille est inconnue).
     */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val url = update.apkUrl
                ?: throw UpdateException("Cette release ne contient aucun APK a installer")
            val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates")
            if (!directory.exists() && !directory.mkdirs()) {
                throw UpdateException("Impossible de preparer le dossier de mise a jour")
            }
            directory.listFiles()?.forEach { file -> runCatching { file.delete() } }

            val target = File(directory, "s3arch-${update.version}.apk")
            val request = Request.Builder().url(url).get().build()
            AppLog.info("Update", "Telechargement de l'APK ${update.version}")
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw UpdateException("Telechargement refuse par GitHub (HTTP ${response.code})")
                }
                val body = response.body ?: throw UpdateException("Reponse vide de GitHub")
                val total = body.contentLength().takeIf { it > 0 } ?: update.apkSize
                var written = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 0.99f))
                        }
                    }
                }
                if (target.length() < 1024) {
                    target.delete()
                    throw UpdateException("APK telecharge vide ou incomplet")
                }
                onProgress(1f)
                AppLog.info("Update", "APK telecharge (${target.length()} octets)")
                target
            }
        }

    companion object {
        /** Depot GitHub officiel de l'application. */
        const val REPO = "lucas76-max/S3ARCH"
        const val GITHUB_URL = "https://github.com/$REPO"
        const val RELEASES_PAGE = "$GITHUB_URL/releases"
        private const val LATEST_RELEASE = "https://api.github.com/repos/$REPO/releases/latest"

        /**
         * Compare deux versions numeriques (« 1.10.0 » > « 1.9.1 »).
         * Les suffixes (« -beta ») sont ignores et valent 0.
         */
        fun compareVersions(left: String, right: String): Int {
            fun parts(value: String): List<Int> = value
                .trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .split('.')
                .map { it.toIntOrNull() ?: 0 }
            val a = parts(left)
            val b = parts(right)
            for (index in 0 until maxOf(a.size, b.size)) {
                val diff = (a.getOrNull(index) ?: 0) - (b.getOrNull(index) ?: 0)
                if (diff != 0) return diff
            }
            return 0
        }
    }
}

class UpdateException(message: String) : Exception(message)
