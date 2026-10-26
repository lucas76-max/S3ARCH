package com.s3arch.app.data

import android.util.Base64
import com.s3arch.app.util.AppLog
import com.s3arch.app.util.DownloadUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Client de l'API officielle Freebox OS.
 *
 * Flux d'autorisation :
 *  1. GET  /api_version                          -> decouverte de la version
 *  2. POST /api/vX/login/authorize/              -> app_token + track_id (+ appui sur la fleche)
 *  3. GET  /api/vX/login/authorize/{track_id}    -> statut (pending/granted/denied/timeout)
 *  4. GET  /api/vX/login/                        -> challenge
 *  5. POST /api/vX/login/session/                -> session_token (HMAC-SHA1 du challenge)
 *  6. X-Fbx-App-Auth: <session_token> sur tous les appels authentifies
 */
class FreeboxApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Client dedie au streaming : pas de callTimeout pour les gros fichiers. */
    private val streamClient: OkHttpClient = Network.http.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile private var baseUrl: String? = null
    @Volatile private var sessionToken: String? = null
    @Volatile private var appToken: String? = null

    val isConnected: Boolean get() = sessionToken != null && baseUrl != null

    data class BoxInfo(
        val host: String,
        val apiBase: String,
        val apiVersion: String,
        val deviceName: String,
        val model: String,
    )

    // ------------------------------------------------------------------ decouverte

    suspend fun discover(rawHost: String): BoxInfo = withContext(Dispatchers.IO) {
        val host = normalizeHost(rawHost)
        val request = Request.Builder().url("http://$host/api_version").get().build()
        val body = try {
            streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw FreeboxException("Reponse ${response.code} de la Freebox (${'$'}host)")
                }
                response.body?.string().orEmpty()
            }
        } catch (e: FreeboxException) {
            AppLog.error("Freebox", "Decouverte impossible : ${e.message}")
            throw e
        } catch (e: Exception) {
            val message =
                "Freebox injoignable sur $host. Verifiez que le telephone est sur le meme reseau Wi-Fi. (${e.message})"
            AppLog.error("Freebox", message)
            throw FreeboxException(message)
        }

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw FreeboxException("Reponse /api_version illisible")
        val apiVersion = root["api_version"]?.jsonPrimitive?.contentOrNull ?: "8.0"
        val apiBaseRaw = root["api_base_url"]?.jsonPrimitive?.contentOrNull ?: "/api/"
        val major = apiVersion.substringBefore('.').toIntOrNull() ?: 8
        BoxInfo(
            host = host,
            apiBase = apiBaseRaw.trimEnd('/') + "/v$major",
            apiVersion = apiVersion,
            deviceName = root["device_name"]?.jsonPrimitive?.contentOrNull ?: "Freebox",
            model = root["device_type"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    // ------------------------------------------------------------------ autorisation

    suspend fun requestAuthorization(host: String, apiBase: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("app_id", APP_ID)
                put("app_name", APP_NAME)
                put("app_version", APP_VERSION)
                put("device_name", android.os.Build.MODEL ?: "Android")
            }
            val result = rawCall(
                url = "http://${normalizeHost(host)}$apiBase/login/authorize/",
                method = "POST",
                body = payload,
                auth = null,
            ).jsonObject
            val token = result["app_token"]?.jsonPrimitive?.contentOrNull
                ?: throw FreeboxException("Autorisation refusee : pas de token renvoye")
            val trackId = result["track_id"]?.jsonPrimitive?.contentOrNull
                ?: throw FreeboxException("Autorisation : track_id absent")
            token to trackId
        }

    /** Attend l'appui physique sur la fleche de la Freebox. */
    suspend fun awaitAuthorization(
        host: String,
        apiBase: String,
        trackId: String,
        timeoutMs: Long = 120_000,
    ): String = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val result = runCatching {
                rawCall(
                    url = "http://${normalizeHost(host)}$apiBase/login/authorize/$trackId",
                    method = "GET",
                    body = null,
                    auth = null,
                ).jsonObject
            }.getOrNull()
            val status = result?.get("status")?.jsonPrimitive?.contentOrNull ?: "pending"
            when (status) {
                "granted" -> return@withContext status
                "denied", "timeout" -> throw FreeboxException(
                    if (status == "denied") "Autorisation refusee sur la Freebox" else "Delai d'autorisation depasse",
                )
            }
            delay(1_200)
        }
        throw FreeboxException("Delai d'autorisation depasse : appuyez sur la fleche de la Freebox")
    }

    // ------------------------------------------------------------------ session

    suspend fun connect(host: String, apiBase: String, token: String): Unit = withContext(Dispatchers.IO) {
        val cleanHost = normalizeHost(host)
        val normalizedBase = normalizeBase(apiBase)
        val challenge = rawCall(
            url = "http://$cleanHost$normalizedBase/login/",
            method = "GET",
            body = null,
            auth = null,
        ).jsonObject["challenge"]?.jsonPrimitive?.contentOrNull
            ?: throw FreeboxException("Challenge de connexion absent")

        val password = hmacSha1Hex(token, challenge)
        val payload = buildJsonObject {
            put("app_id", APP_ID)
            put("password", password)
        }
        val result = rawCall(
            url = "http://$cleanHost$normalizedBase/login/session/",
            method = "POST",
            body = payload,
            auth = token,
        ).jsonObject
        val session = result["session_token"]?.jsonPrimitive?.contentOrNull
            ?: throw FreeboxException("Session Freebox refusee (app_token invalide ?)")

        appToken = token
        baseUrl = "http://$cleanHost$normalizedBase"
        sessionToken = session
        AppLog.info("Freebox", "Session ouverte sur $cleanHost$normalizedBase")
    }

    private suspend fun ensureSession() {
        if (isConnected) return
        throw FreeboxException("Freebox non connectee : relancez l'autorisation dans les reglages")
    }

    // ------------------------------------------------------------------ telechargements

    /**
     * Cree (si besoin) le dossier de destination et renvoie le chemin reellement utilisable
     * (toujours termine par un « / », comme l'exige `download_dir`).
     *
     * Attention : `/fs/mkdir/` attend un parent **sans** slash final (« /Disque dur »),
     * alors que `download_dir` en exige un (« /Disque dur/S3ARCH/ »). Mettre le slash
     * dans le parent fait echouer la creation (`path_not_found`), puis le telechargement
     * echoue en `bt_file_error` faute de dossier.
     */
    suspend fun ensureFolder(dir: String): String = withContext(Dispatchers.IO) {
        ensureSession()
        val requested = normalizeDir(dir)
        val first = tryCreateFolder(requested)
        if (first.created && folderExists(requested)) return@withContext requested

        AppLog.warn("Freebox", "Dossier $requested indisponible : recherche d'un repli")
        val base = configuredDownloadDir() ?: first.deepest
            ?: throw FreeboxException(
                "Impossible de creer le dossier « $requested » sur la Freebox",
                "mkdir_failed",
            )

        val target = normalizeDir(base + "S3ARCH")
        if (tryCreateFolder(target).created && folderExists(target)) return@withContext target

        AppLog.warn("Freebox", "Creation impossible : utilisation directe de $base")
        base
    }

    private data class FolderAttempt(val created: Boolean, val deepest: String?)

    /**
     * Cree chaque niveau du chemin.
     *
     * `/fs/mkdir/` attend un parent SANS slash final : parent = b64("/Disque dur").
     * La Freebox repond `access_denied` quand le dossier existe deja.
     */
    private suspend fun tryCreateFolder(target: String): FolderAttempt {
        var current = ""
        for ((parent, part) in FreeboxPaths.levels(target)) {
            val label = if (parent == "/") "/$part" else "$parent/$part"
            val outline = runCatching {
                val payload = buildJsonObject {
                    put("parent", b64(parent))
                    put("dirname", part)
                }
                authCall("/fs/mkdir/", "POST", payload)
            }
            val error = outline.exceptionOrNull()
            if (error != null) {
                val code = (error as? FreeboxException)?.code
                val alreadyThere = code == "access_denied" ||
                    code == "destination_conflict" ||
                    code == "eexist"
                if (!alreadyThere) {
                    AppLog.warn("Freebox", "mkdir $label : ${error.message}")
                    return FolderAttempt(false, current.ifBlank { null }?.plus("/"))
                }
                AppLog.info("Freebox", "Dossier deja present : $label")
            } else {
                AppLog.info("Freebox", "Dossier cree : $label")
            }
            current = if (current.isBlank()) "/$part" else "$current/$part"
        }
        return FolderAttempt(true, "$current/")
    }

    /**
     * Verifie qu'un dossier existe vraiment (la Freebox repond `access_denied` aussi bien
     * pour « deja present » que pour un refus reel). En cas de doute on considere qu'il existe.
     */
    private suspend fun folderExists(path: String): Boolean {
        val encoded = b64(path.trimEnd('/'))
        // Un base64 contenant « / » serait ambigu dans l'URL : on ne bloque pas.
        if (encoded.contains('/')) return true
        return try {
            authCall("/fs/info/$encoded", "GET", null)
            true
        } catch (e: FreeboxException) {
            e.code != "path_not_found"
        } catch (e: Exception) {
            true
        }
    }

    /** Dossier de telechargement par defaut configure dans le gestionnaire de la Freebox. */
    suspend fun configuredDownloadDir(): String? = withContext(Dispatchers.IO) {
        ensureSession()
        val result = runCatching { authCall("/downloads/config/", "GET", null) }.getOrNull()
            ?: return@withContext null
        val raw = result.jsonObject["download_dir"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
        val decoded = decodePath(raw).takeIf { it.isNotBlank() } ?: return@withContext null
        AppLog.info("Freebox", "Dossier par defaut de la Freebox : $decoded")
        normalizeDir(decoded)
    }

    /**
     * Ajoute une URL (.torrent, magnet) dans le gestionnaire de la Freebox.
     *
     * ATTENTION : contrairement au reste de l'API, /downloads/add/ n'accepte PAS
     * de corps JSON (la Freebox repond alors « invalid_request ») : les parametres
     * doivent etre envoyes en application/x-www-form-urlencoded.
     */
    suspend fun addDownload(url: String, dir: String): String = withContext(Dispatchers.IO) {
        ensureSession()
        val link = DownloadUrls.sanitize(url)
        if (link.isBlank()) {
            throw FreeboxException(
                "Lien de telechargement inutilisable (URL relative ou mal formee)",
                "invalid_url",
            )
        }
        val target = normalizeDir(dir)
        AppLog.info("Freebox", "POST /downloads/add/ dir=$target url=${AppLog.redact(link)}")
        val element = authFormCall(
            "/downloads/add/",
            listOf(
                "download_url" to link,
                "download_dir" to b64(target),
            ),
        )
        val id = element.findAddedTaskId()
        if (id != null) {
            AppLog.info("Freebox", "Tache creee : $id")
            return@withContext id
        }

        // Certaines versions ne renvoient pas l'id : on prend la tache la plus recente.
        val tasks = listTasks()
        val latest = tasks.maxByOrNull { it.id.toLongOrNull() ?: 0L }?.id
            ?: throw FreeboxException("Telechargement ajoute mais introuvable dans la liste Freebox")
        AppLog.info("Freebox", "Tache detectee : $latest")
        latest
    }

    /**
     * Envoie le fichier .torrent directement a la Freebox (multipart/form-data).
     *
     * C'est la voie la plus fiable pour les trackers prives : la Freebox n'a pas a
     * telecharger le .torrent elle-meme, donc plus de refus « invalid_url » ni de
     * blocage du tracker envers l'IP de la box.
     */
    suspend fun addTorrentFile(
        torrent: ByteArray,
        dir: String,
        fileName: String = "s3arch.torrent",
    ): String = withContext(Dispatchers.IO) {
        ensureSession()
        if (torrent.isEmpty()) throw FreeboxException("Fichier .torrent vide")
        val target = normalizeDir(dir)
        AppLog.info("Freebox", "POST /downloads/add/ (fichier ${torrent.size} octets) dir=$target")

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("download_dir", b64(target))
            .addFormDataPart(
                "download_file",
                fileName,
                torrent.toRequestBody("application/x-bittorrent".toMediaType()),
            )
            .build()

        val element = authMultipartCall("/downloads/add/", body)
        val id = element.findAddedTaskId()
        if (id != null) {
            AppLog.info("Freebox", "Tache creee depuis le fichier : $id")
            return@withContext id
        }
        val latest = listTasks().maxByOrNull { it.id.toLongOrNull() ?: 0L }?.id
            ?: throw FreeboxException("Telechargement ajoute mais introuvable dans la liste Freebox")
        AppLog.info("Freebox", "Tache detectee : $latest")
        latest
    }

    suspend fun task(id: String): FreeboxTask? = withContext(Dispatchers.IO) {
        ensureSession()
        runCatching { authCall("/downloads/$id/", "GET", null).asTask() }.getOrNull()
    }

    suspend fun listTasks(): List<FreeboxTask> = withContext(Dispatchers.IO) {
        ensureSession()
        val element = authCall("/downloads/", "GET", null)
        (element as? JsonArray)?.mapNotNull { it.asTaskOrNull() } ?: emptyList()
    }

    /** Flux direct d'un fichier de la Freebox (pour VLC). Bloquant : appele depuis le proxy local. */
    fun openFileBlocking(encodedPath: String, range: String?): Response {
        val base = baseUrl ?: throw FreeboxException("Freebox non connectee")
        val token = sessionToken ?: throw FreeboxException("Session Freebox absente")

        fun request(pathPrefix: String): Response {
            val parsed = base.toHttpUrlOrNull() ?: throw FreeboxException("URL Freebox invalide")
            val builder = parsed.newBuilder()
            if (pathPrefix == "dl") {
                builder.addPathSegment("dl").addPathSegment(encodedPath)
            } else {
                builder.addPathSegment("fs").addPathSegment("download")
                    .addQueryParameter("path", encodedPath)
            }
            val req = Request.Builder()
                .url(builder.build())
                .header("X-Fbx-App-Auth", token)
                .apply { if (!range.isNullOrBlank()) header("Range", range) }
                .get()
                .build()
            return streamClient.newCall(req).execute()
        }

        val response = request("dl")
        if (response.code == 404) {
            response.close()
            return request("fs")
        }
        return response
    }

    // ------------------------------------------------------------------ HTTP interne

    private suspend fun authCall(path: String, method: String, body: JsonElement?): JsonElement {
        val base = baseUrl ?: throw FreeboxException("Freebox non connectee")
        val token = sessionToken ?: throw FreeboxException("Session Freebox absente")
        return try {
            rawCall(base + path, method, body, token)
        } catch (e: FreeboxAuthException) {
            renewSession()
            rawCall(base + path, method, body, sessionToken)
        }
    }

    private suspend fun authMultipartCall(path: String, body: MultipartBody): JsonElement {
        val base = baseUrl ?: throw FreeboxException("Freebox non connectee")
        val token = sessionToken ?: throw FreeboxException("Session Freebox absente")
        return try {
            rawMultipartCall(base + path, body, token)
        } catch (e: FreeboxAuthException) {
            renewSession()
            rawMultipartCall(base + path, body, sessionToken)
        }
    }

    /** Appel POST en application/x-www-form-urlencoded (requis par /downloads/add/). */
    private suspend fun authFormCall(path: String, params: List<Pair<String, String>>): JsonElement {
        val base = baseUrl ?: throw FreeboxException("Freebox non connectee")
        val token = sessionToken ?: throw FreeboxException("Session Freebox absente")
        return try {
            rawFormCall(base + path, params, token)
        } catch (e: FreeboxAuthException) {
            renewSession()
            rawFormCall(base + path, params, sessionToken)
        }
    }

    /** Rouvre une session avec l'app_token conserve (session expiree). */
    private suspend fun renewSession() {
        val base = baseUrl ?: throw FreeboxException("Freebox non connectee")
        val stored = appToken
            ?: throw FreeboxException("Session Freebox expiree : reconnectez la Freebox dans les reglages")
        val host = base.substringAfter("//").substringBefore("/")
        val apiBase = "/" + base.substringAfter("//").substringAfter("/")
        connect(host, apiBase, stored)
    }

    private fun rawCall(url: String, method: String, body: JsonElement?, auth: String?): JsonElement {
        val parsed = url.toHttpUrlOrNull() ?: throw FreeboxException("URL Freebox invalide : $url")
        val builder = Request.Builder().url(parsed)
        if (!auth.isNullOrBlank()) builder.header("X-Fbx-App-Auth", auth)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((body?.toString() ?: "{}").toRequestBody(jsonMedia))
            "DELETE" -> builder.delete()
            else -> builder.method(method, (body?.toString() ?: "{}").toRequestBody(jsonMedia))
        }
        return readResponse(streamClient.newCall(builder.build()).execute())
    }

    private fun rawFormCall(
        url: String,
        params: List<Pair<String, String>>,
        auth: String?,
    ): JsonElement {
        val parsed = url.toHttpUrlOrNull() ?: throw FreeboxException("URL Freebox invalide : $url")
        val form = FormBody.Builder()
        params.forEach { (name, value) -> form.add(name, value) }
        val builder = Request.Builder().url(parsed).post(form.build())
        if (!auth.isNullOrBlank()) builder.header("X-Fbx-App-Auth", auth)
        return readResponse(streamClient.newCall(builder.build()).execute())
    }

    private fun rawMultipartCall(url: String, body: MultipartBody, auth: String?): JsonElement {
        val parsed = url.toHttpUrlOrNull() ?: throw FreeboxException("URL Freebox invalide : $url")
        val builder = Request.Builder().url(parsed).post(body)
        if (!auth.isNullOrBlank()) builder.header("X-Fbx-App-Auth", auth)
        return readResponse(streamClient.newCall(builder.build()).execute())
    }

    private fun readResponse(response: Response): JsonElement = response.use {
        val text = it.body?.string().orEmpty()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw FreeboxException("Reponse Freebox illisible (HTTP ${it.code})")
        val success = root["success"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        if (!success) {
            val code = root["error_code"]?.jsonPrimitive?.contentOrNull
            val msg = root["msg"]?.jsonPrimitive?.contentOrNull
                ?: root["error"]?.jsonPrimitive?.contentOrNull
                ?: "Erreur Freebox"
            AppLog.error("Freebox", "HTTP ${it.code} ${code ?: "?"} : ${text.take(300)}")
            if (code == "auth_required" || code == "invalid_session") throw FreeboxAuthException(msg)
            throw FreeboxException(SafeErrors.describe(code, msg), code)
        }
        if (!it.isSuccessful) {
            AppLog.error("Freebox", "HTTP ${it.code} : ${text.take(300)}")
            throw FreeboxException("Erreur Freebox HTTP ${it.code}")
        }
        root["result"] ?: JsonObject(emptyMap())
    }

    // ------------------------------------------------------------------ helpers

    private fun JsonElement.findAddedTaskId(): String? {
        val element = this
        return when (element) {
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonObject -> element["id"]?.jsonPrimitive?.contentOrNull
                ?: element["download_id"]?.jsonPrimitive?.contentOrNull
                ?: element.values.firstOrNull()?.findAddedTaskId()
            is JsonArray -> element.firstOrNull()?.findAddedTaskId()
            else -> null
        }
    }

    private fun JsonElement.asTaskOrNull(): FreeboxTask? {
        val obj = this as? JsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        return FreeboxTask(
            id = id,
            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Torrent $id",
            status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "queued",
            rxBytes = obj["rx_bytes"]?.jsonPrimitive?.longOrNull ?: 0L,
            totalBytes = obj["total_bytes"]?.jsonPrimitive?.longOrNull
                ?: obj["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            rxRate = obj["rx_rate"]?.jsonPrimitive?.longOrNull ?: 0L,
            error = obj["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "none" },
            downloadDir = decodePath(obj["download_dir"]?.jsonPrimitive?.contentOrNull),
        )
    }

    private fun JsonElement.asTask(): FreeboxTask? = asTaskOrNull()

    private fun decodePath(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        if (raw.startsWith("/")) return raw
        return runCatching { String(Base64.decode(raw, Base64.DEFAULT)) }.getOrElse { raw }
    }

    private fun b64(value: String): String = FreeboxPaths.base64(value)

    /**
     * Les chemins Freebox sont absolus et se terminent obligatoirement par un « / ».
     * Un dossier sans slash final est refuse par /downloads/add/ (« invalid_request »).
     */
    private fun normalizeDir(dir: String): String = FreeboxPaths.normalizeDir(dir, DEFAULT_DIR)

    private fun normalizeHost(rawHost: String): String {
        var host = rawHost.trim()
        host = host.removePrefix("http://").removePrefix("https://")
        host = host.trimEnd('/')
        if (host.isEmpty()) throw FreeboxException("Adresse de la Freebox vide")
        // On ne garde pas de chemin eventuel (ex: mafreebox.freebox.fr:80)
        return host
    }

    private fun normalizeBase(rawBase: String): String {
        var base = rawBase.trim()
        if (base.isEmpty()) base = "/api/v8"
        if (!base.startsWith("/")) base = "/$base"
        return base.trimEnd('/')
    }

    private fun hmacSha1Hex(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val digest = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val APP_ID = "com.s3arch.app"
        const val APP_NAME = "S3ARCH"
        const val APP_VERSION = "1.4.0"
        const val DEFAULT_DIR = "/Disque dur/S3ARCH"
    }
}

internal object SafeErrors {
    fun describe(code: String?, message: String): String = when (code) {
        "eexist" -> "Le dossier existe deja"
        "eacces" -> "Acces refuse par la Freebox ($message)"
        "enoent" -> "Chemin introuvable sur la Freebox ($message)"
        "auth_required", "invalid_session" -> "Session Freebox expiree"
        "insufficient_rights" -> "Droits insuffisants : autorisez S3ARCH sur la Freebox"
        "no_free_space" -> "Plus d'espace libre sur la Freebox"
        "invalid_request" ->
            "Parametres refuses par la Freebox : verifiez le dossier de destination et le lien du torrent"
        "invalid_url" -> "Lien refuse par la Freebox : le tracker a peut-etre renvoye une page au lieu du .torrent"
        "invalid_file" -> "Fichier .torrent invalide ou illisible par la Freebox"
        "invalid_operation" -> "Operation invalide pour ce telechargement"
        "too_many_tasks" -> "Trop de telechargements dans la file de la Freebox"
        "path_not_found" -> "Dossier introuvable sur la Freebox (disque non monte ?)"
        "disk_unavailable" -> "Le disque de la Freebox n'est pas monte"
        "access_denied" -> "Acces refuse a ce dossier par la Freebox"
        "bt_file_error" -> "La Freebox n'a pas pu ecrire le torrent dans le dossier de destination"
        "exists" -> "Ce torrent est deja present dans la file Freebox"
        "hibernating" -> "Le gestionnaire de telechargement de la Freebox est en veille"
        else -> if (code.isNullOrBlank()) message else "$message ($code)"
    }
}

class FreeboxException(message: String, val code: String? = null) : Exception(message)
class FreeboxAuthException(message: String) : Exception(message)
