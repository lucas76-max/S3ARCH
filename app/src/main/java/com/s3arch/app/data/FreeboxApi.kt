package com.s3arch.app.data

import android.util.Base64
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
            throw e
        } catch (e: Exception) {
            throw FreeboxException(
                "Freebox injoignable sur $host. Verifiez que le telephone est sur le meme reseau Wi-Fi. (${e.message})",
            )
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
    }

    private suspend fun ensureSession() {
        if (isConnected) return
        throw FreeboxException("Freebox non connectee : relancez l'autorisation dans les reglages")
    }

    // ------------------------------------------------------------------ telechargements

    /**
     * Cree (si besoin) le dossier de destination sur la Freebox.
     * Les chemins de l'API Freebox doivent se terminer par un « / ».
     */
    suspend fun ensureFolder(dir: String) = withContext(Dispatchers.IO) {
        ensureSession()
        var parent = "/"
        for (part in normalizeDir(dir).split('/').filter { it.isNotBlank() }) {
            runCatching {
                val payload = buildJsonObject {
                    put("parent", b64(parent))
                    put("dirname", part)
                }
                authCall("/fs/mkdir/", "POST", payload)
            }
            parent = "$parent$part/"
        }
        Unit
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
        val link = url.trim()
        if (link.isBlank()) throw FreeboxException("Lien de telechargement absent pour ce torrent")
        val element = authFormCall(
            "/downloads/add/",
            listOf(
                "download_url" to link,
                "download_dir" to b64(normalizeDir(dir)),
            ),
        )
        val id = element.findAddedTaskId()
        if (id != null) return@withContext id

        // Certaines versions ne renvoient pas l'id : on prend la tache la plus recente.
        val tasks = listTasks()
        tasks.maxByOrNull { it.id.toLongOrNull() ?: 0L }?.id
            ?: throw FreeboxException("Telechargement ajoute mais introuvable dans la liste Freebox")
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
            if (code == "auth_required" || code == "invalid_session") throw FreeboxAuthException(msg)
            throw FreeboxException(SafeErrors.describe(code, msg))
        }
        if (!it.isSuccessful) throw FreeboxException("Erreur Freebox HTTP ${it.code}")
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

    private fun b64(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /**
     * Les chemins Freebox sont absolus et se terminent obligatoirement par un « / ».
     * Un dossier sans slash final est refuse par /downloads/add/ (« invalid_request »).
     */
    private fun normalizeDir(dir: String): String {
        var value = dir.trim().replace("\\", "/")
        if (value.isEmpty()) value = DEFAULT_DIR
        while (value.contains("//")) value = value.replace("//", "/")
        if (!value.startsWith("/")) value = "/$value"
        if (!value.endsWith("/")) value = "$value/"
        return value
    }

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
        const val APP_VERSION = "1.2.0"
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
        "exists" -> "Ce torrent est deja present dans la file Freebox"
        "hibernating" -> "Le gestionnaire de telechargement de la Freebox est en veille"
        else -> if (code.isNullOrBlank()) message else "$message ($code)"
    }
}

class FreeboxException(message: String) : Exception(message)
class FreeboxAuthException(message: String) : Exception(message)
