package com.s3arch.app.data

import android.util.Base64
import com.s3arch.app.BuildConfig
import com.s3arch.app.data.vpn.VpnAddresses
import com.s3arch.app.data.vpn.VpnUserRequests
import com.s3arch.app.util.AppLog
import com.s3arch.app.util.DownloadUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

    /** Client court pour tester un lien avant de le confier au lecteur. */
    private val probeClient: OkHttpClient = Network.http.newBuilder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile private var baseUrl: String? = null
    @Volatile private var sessionToken: String? = null
    @Volatile private var appToken: String? = null

    /** Droits accordes a l'application, renvoyes par `POST /login/session/`. */
    @Volatile var grantedPermissions: Set<String> = emptySet()
        private set

    /** Vrai si la box a effectivement liste les droits (sinon on ne sait rien). */
    @Volatile var permissionsKnown: Boolean = false
        private set

    /**
     * Le droit `settings` est indispensable au VPN : l'API `/vpn/` le refuse sinon
     * (`insufficient_rights`, `missing_right: settings`).
     */
    val hasSettingsRight: Boolean get() = "settings" in grantedPermissions

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
            val url = "http://${normalizeHost(host)}$apiBase/login/authorize/"
            fun payload(withPermissions: Boolean) = buildJsonObject {
                put("app_id", APP_ID)
                put("app_name", APP_NAME)
                put("app_version", APP_VERSION)
                put("device_name", android.os.Build.MODEL ?: "Android")
                if (withPermissions) {
                    put(
                        "permissions",
                        buildJsonObject { REQUESTED_PERMISSIONS.forEach { name -> put(name, true) } },
                    )
                }
            }
            val result = try {
                rawCall(url, "POST", payload(true), null).jsonObject
            } catch (e: FreeboxException) {
                // Le champ `permissions` n'est pas garanti par tous les firmwares : on
                // refait la demande sans lui plutot que d'echouer a l'appairage.
                AppLog.warn(
                    "Freebox",
                    "Demande d'autorisation avec droits refusee (${e.code ?: "?"}) : nouvelle demande standard",
                )
                rawCall(url, "POST", payload(false), null).jsonObject
            }
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
        rememberPermissions(result["permissions"])
        AppLog.info("Freebox", "Session ouverte sur $cleanHost$normalizedBase")
    }

    /**
     * Retient les droits reellement accordes a l'application.
     *
     * La reponse de session contient un objet `permissions` (droit → booleen). Un droit
     * absent vaut `false` : c'est ainsi qu'on detecte qu'une box refusera `/vpn/`
     * (`missing_right: settings`) au lieu de laisser croire que le WireGuard manque.
     */
    private fun rememberPermissions(element: JsonElement?) {
        val allowed = FreeboxRights.parse(element)
        if (allowed == null) {
            permissionsKnown = false
            grantedPermissions = emptySet()
            AppLog.warn("Freebox", "Droits de l'application non communiques par la box")
            return
        }
        grantedPermissions = allowed
        permissionsKnown = true
        AppLog.info(
            "Freebox",
            "Droits accordes : ${if (allowed.isEmpty()) "(aucun)" else allowed.sorted().joinToString(", ")}",
        )
        if ("settings" !in allowed) {
            AppLog.warn(
                "Freebox",
                "Droit « settings » absent : les réglages et le VPN seront refusés par la box",
            )
        }
    }

    /**
     * Ferme la session courante pour forcer une reconnexion.
     *
     * C'est la seule facon de relire les droits de l'application : la box ne les renvoie
     * qu'a l'ouverture de session (`POST /login/session/`).
     */
    fun reset() {
        sessionToken = null
        baseUrl = null
        grantedPermissions = emptySet()
        permissionsKnown = false
    }

    /**
     * Change l'adresse de la box sans rouvrir de session : en LAN on utilise le nom
     * mDNS, hors du reseau local on passe par l'IP LAN a travers le tunnel VPN.
     */
    fun useHost(rawHost: String) {
        val current = baseUrl ?: return
        val host = runCatching { normalizeHost(rawHost) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val path = current.substringAfter("//").substringAfter("/", "")
        val updated = if (path.isBlank()) "http://$host" else "http://$host/$path"
        if (updated != current) {
            baseUrl = updated
            AppLog.info("Freebox", "Adresse de la box : $host")
        }
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
     * alors que `download_dir` en exige un (« /Disque dur/Téléchargements/S3ARCH/ »). Mettre le slash
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
            requireTorrentTask(id)
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
            requireTorrentTask(id)
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

    /**
     * Fichiers reels d'une tache (`GET /downloads/{id}/files`).
     *
     * Indispensable pour la lecture : une tache BitTorrent porte souvent un dossier
     * (`name`) et non un fichier, et chaque fichier expose un `preview_url` lisible
     * sans authentification, donc exploitable directement par VLC.
     */
    suspend fun taskFiles(id: String): List<FreeboxFile> = withContext(Dispatchers.IO) {
        ensureSession()
        val element = runCatching { authCall("/downloads/$id/files", "GET", null) }.getOrNull()
            ?: return@withContext emptyList()
        (element as? JsonArray)?.mapNotNull { it.asFileOrNull() } ?: emptyList()
    }

    /** Supprime une tache ; `erase` efface aussi les fichiers telecharges. */
    suspend fun deleteTask(id: String, erase: Boolean = false): Unit = withContext(Dispatchers.IO) {
        ensureSession()
        authCall("/downloads/$id" + if (erase) "/erase" else "", "DELETE", null)
        AppLog.info("Freebox", "Tache $id supprimee${if (erase) " (fichiers effaces)" else ""}")
    }

    /**
     * Verifie que la tache creee est bien un torrent (`bt`).
     *
     * Si la Freebox a traite l'envoi comme un simple fichier, elle enregistre le .torrent dans
     * le dossier sans jamais lancer le telechargement : la tache est alors supprimee et effacee.
     */
    private suspend fun requireTorrentTask(id: String): Unit {
        val created = task(id) ?: return
        AppLog.info(
            "Freebox",
            "Tache $id : type=${created.type.ifBlank { "?" }} statut=${created.status} nom=${created.name}",
        )
        if (created.type.isNotBlank() && created.type != "bt") {
            AppLog.error("Freebox", "Tache ${created.type} au lieu d'un torrent : suppression de $id")
            runCatching { deleteTask(id, erase = true) }
            throw FreeboxException(
                "La Freebox a enregistre le .torrent comme un fichier (${created.type}) " +
                    "au lieu de lancer le torrent",
                "not_bittorrent",
            )
        }
    }

    suspend fun listTasks(): List<FreeboxTask> = withContext(Dispatchers.IO) {
        ensureSession()
        val element = authCall("/downloads/", "GET", null)
        (element as? JsonArray)?.mapNotNull { it.asTaskOrNull() } ?: emptyList()
    }

    /** Resultat du test d'un lien avant de le confier a un lecteur. */
    data class StreamProbe(val ok: Boolean, val detail: String)

    /**
     * Verifie qu'un lien de partage repond vraiment avant de lancer un lecteur :
     * un lien mort donnerait un VLC ouvert sur une adresse muette.
     *
     * Deux tentatives : avec `Range` (comme le fait un lecteur), puis sans, car
     * certaines versions de la Freebox refusent l'en-tete sur un lien de partage.
     * Le detail (code HTML ou exception) est journalise pour ne plus diagnostiquer
     * « injoignable » a l'aveugle.
     */
    suspend fun probeStream(url: String): StreamProbe = withContext(Dispatchers.IO) {
        fun attempt(withRange: Boolean): StreamProbe = try {
            val builder = Request.Builder().url(url).get()
            if (withRange) builder.header("Range", "bytes=0-0")
            probeClient.newCall(builder.build()).execute().use { response ->
                when {
                    response.code == 416 -> StreamProbe(true, "HTTP 416 (lien valide)")
                    response.isSuccessful -> StreamProbe(true, "HTTP ${response.code}")
                    else -> StreamProbe(false, "HTTP ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            StreamProbe(false, "${e.javaClass.simpleName}: ${e.message ?: "sans message"}")
        }

        val first = attempt(true)
        if (first.ok) {
            first
        } else {
            val second = attempt(false)
            if (second.ok) second else second.copy(detail = "${first.detail} / sans Range : ${second.detail}")
        }
    }

    /**
     * Flux direct d'un fichier de la Freebox pour le proxy local. Bloquant : appele
     * depuis le thread du proxy, jamais depuis le thread principal.
     *
     * Trois sources, de la plus fiable a la moins fiable :
     *  1. `GET /api/vX/dl/{chemin base64}` : endpoint documente de l'API fichiers,
     *     en **LAN**, authentifie, sans passer par l'acces distant de la box ;
     *  2. le lien de partage `btpreview` (`preview_url`), qui fonctionne sans
     *     authentification mais transite par l'acces distant HTTPS (`*.freeboxos.fr`,
     *     port aleatoire) — c'est lui que VLC ne savait pas ouvrir ;
     *  3. `GET /fs/download/?path=` pour les firmwares sans `/dl/`.
     *
     * Une session expiree (403) est renouvelee une fois. Chaque tentative est
     * journalisee avec son code HTTP : plus de diagnostic a l'aveugle.
     */
    fun openMediaBlocking(source: VlcProxyServer.StreamSource, range: String?): Response {
        fun authRequest(build: (okhttp3.HttpUrl.Builder) -> Unit): Response {
            val base = baseUrl ?: throw FreeboxException("Freebox non connectee")
            val token = sessionToken ?: throw FreeboxException("Session Freebox absente")
            val parsed = base.toHttpUrlOrNull() ?: throw FreeboxException("URL Freebox invalide")
            val builder = parsed.newBuilder()
            build(builder)
            val req = Request.Builder()
                .url(builder.build())
                .header("X-Fbx-App-Auth", token)
                .apply { if (!range.isNullOrBlank()) header("Range", range) }
                .get()
                .build()
            return streamClient.newCall(req).execute()
        }

        fun withRetry(name: String, retried: Boolean = false, call: () -> Response): Response {
            val response = call()
            val rangeInfo = response.header("Content-Range")?.let { " · $it" }.orEmpty()
            AppLog.info(
                "Lecture",
                "Flux $name -> HTTP ${response.code} ${response.message}" +
                    " (${response.header("Content-Type") ?: "type inconnu"}$rangeInfo)",
            )
            if (response.code == 403 && !retried) {
                response.close()
                AppLog.warn("Lecture", "Session refusee ($name), renouvellement puis nouvel essai")
                runCatching { runBlocking { renewSession() } }
                    .onFailure { AppLog.warn("Lecture", "Renouvellement de session impossible : ${it.message}") }
                return withRetry(name, retried = true, call = call)
            }
            return response
        }

        fun usable(response: Response): Boolean = response.isSuccessful || response.code == 416

        val failures = mutableListOf<String>()

        run {
            val dl = withRetry("/dl/") {
                authRequest { it.addPathSegment("dl").addPathSegment(source.encodedPath) }
            }
            if (usable(dl)) return dl
            failures += "/dl/ HTTP ${dl.code}"
            dl.close()
        }

        val share = source.shareUrl
        if (!share.isNullOrBlank()) {
            val response = withRetry("lien de partage") {
                val request = Request.Builder()
                    .url(share)
                    .apply { if (!range.isNullOrBlank()) header("Range", range) }
                    .get()
                    .build()
                streamClient.newCall(request).execute()
            }
            if (usable(response)) return response
            failures += "lien de partage HTTP ${response.code}"
            response.close()
        }

        val fs = withRetry("/fs/download/") {
            authRequest {
                it.addPathSegment("fs").addPathSegment("download")
                    .addQueryParameter("path", source.encodedPath)
            }
        }
        if (usable(fs)) return fs
        failures += "/fs/download/ HTTP ${fs.code}"
        fs.close()

        throw FreeboxException("Lecture refusee par la Freebox (${failures.joinToString(", ")})")
    }

    // ------------------------------------------------------------------ VPN WireGuard

    /** Serveurs VPN de la box ; on ne s'interesse qu'au WireGuard. */
    suspend fun vpnServers(): List<VpnServer> = withContext(Dispatchers.IO) {
        ensureSession()
        // Ne pas avaler l'erreur : un `insufficient_rights` doit remonter tel quel,
        // sinon l'application annonce a tort « pas de serveur WireGuard ».
        val result = authCall("/vpn/", "GET", null)
        (result as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["name"]?.jsonPrimitive?.contentOrNull
                ?: obj["id"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            VpnServer(
                id = id,
                type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "",
                state = obj["state"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        } ?: emptyList()
    }

    /**
     * Attend que le serveur WireGuard soit reellement demarre.
     *
     * La box active le serveur de facon asynchrone : creer un utilisateur pendant
     * l'etat `starting` peut etre refuse par un `internal_error` generique. On laisse
     * donc au serveur le temps de passer a `started` avant l'appel.
     */
    suspend fun awaitVpnServerStarted(id: String, timeoutMs: Long = 12_000): Boolean =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val state = runCatching {
                    vpnServers().firstOrNull { it.id == id }?.state
                }.getOrNull()
                if (state == "started") {
                    AppLog.info("VPN", "Serveur WireGuard « $id » démarré")
                    return@withContext true
                }
                delay(600)
            }
            false
        }

    /** Active le serveur WireGuard (idempotent). */
    suspend fun enableWireGuardServer(id: String): Unit = withContext(Dispatchers.IO) {
        ensureSession()
        AppLog.info("VPN", "PUT /vpn/$id/config/ enabled=true")
        runCatching { authCall("/vpn/$id/config/", "PUT", buildJsonObject { put("enabled", true) }) }
            .onSuccess { AppLog.info("VPN", "Serveur WireGuard « $id » activé") }
            .onFailure { error ->
                AppLog.error("VPN", "Activation du serveur « $id » refusée : ${error.message}")
                throw FreeboxException(
                    "Activation du serveur WireGuard refusée : ${error.message}",
                    (error as? FreeboxException)?.code,
                )
            }
    }

    /** Plage d'adresses du pool VPN et reservations existantes. */
    suspend fun vpnPool(): VpnPool = withContext(Dispatchers.IO) {
        ensureSession()
        val result = authCall("/vpn/ip_pool/", "GET", null).jsonObject
        VpnPool(
            start = result["ip_start"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            end = result["ip_end"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            reserved = (result["reservations"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("ip")?.jsonPrimitive?.contentOrNull }
                ?.toSet()
                .orEmpty(),
        )
    }

    /**
     * Cree l'utilisateur WireGuard dedie a l'application s'il n'existe pas deja.
     *
     * Le payload suit l'objet `VPNUser` documente par Freebox OS :
     * `login`, `type: "wireguard"` et `ip_reservation` (obligatoire pour ce type),
     * avec `conf_wireguard` pour le keepalive. Les tous premiers essais de S3ARCH
     * omettaient `type`/`ip_reservation`, ou envoyaient `type` sans mot de passe : la
     * box repondait alors `internal_error` (« Impossible de creer cet objet »).
     *
     * Comme certaines versions de firmware sont plus strictes, plusieurs variantes
     * documentees sont essayees, puis une autre adresse du pool si besoin. Un
     * `internal_error` peut aussi survenir apres une creation reussie : la liste des
     * utilisateurs est donc relue avant de conclure a un echec.
     */
    suspend fun ensureWireGuardUser(login: String): String = withContext(Dispatchers.IO) {
        ensureSession()
        if (vpnUsers().contains(login)) {
            AppLog.info("VPN", "Utilisateur WireGuard « $login » déjà présent")
            return@withContext login
        }

        val pool = runCatching { vpnPool() }.getOrNull()
        val candidates = pool?.let {
            VpnAddresses.freeIps(it.start, it.end, it.reserved, limit = 3)
        }.orEmpty()
        if (candidates.isEmpty()) {
            throw FreeboxException(
                "Aucune adresse VPN libre sur la Freebox : supprimez un utilisateur VPN " +
                    "dans Freebox OS, puis réessayez",
                "no_free_ip",
            )
        }

        val password = randomSecret(24)
        var lastError: Throwable? = null

        for (ip in candidates) {
            val payloads = VpnUserRequests.payloads(login, password, ip)
            payloads.forEachIndexed { index, payload ->
                AppLog.info(
                    "VPN",
                    "POST /vpn/user/ (type=wireguard, ip=$ip, variante ${index + 1}/${payloads.size})",
                )
                lastError = runCatching { authCall("/vpn/user/", "POST", payload) }.exceptionOrNull()
                if (lastError == null) {
                    AppLog.info("VPN", "Utilisateur WireGuard « $login » créé (ip=$ip)")
                    return@withContext login
                }
                AppLog.error(
                    "VPN",
                    "Création refusée (ip=$ip, variante ${index + 1}) : ${lastError?.message}",
                )
                // La box a pu creer l'utilisateur malgre la reponse d'erreur.
                if (vpnUsers().contains(login)) {
                    AppLog.warn("VPN", "Utilisateur VPN finalement présent après l'erreur : poursuite")
                    return@withContext login
                }
            }
        }

        val detail = lastError?.message ?: "erreur inconnue"
        throw FreeboxException(
            "Création de l'utilisateur VPN refusée par la Freebox ($detail). " +
                "Vérifiez que le serveur WireGuard est bien activé dans Freebox OS " +
                "(Paramètres de la Freebox → mode avancé → Serveur VPN), puis réessayez.",
            (lastError as? FreeboxException)?.code,
        )
    }

    /** Logins des utilisateurs VPN existants (vide si la box ne repond pas). */
    private suspend fun vpnUsers(): Set<String> {
        val users = runCatching { authCall("/vpn/user/", "GET", null) }.getOrNull() as? JsonArray
        return users.orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.get("login")?.jsonPrimitive?.contentOrNull
        }.toSet()
    }

    /** Mot de passe aleatoire (8 a 32 caracteres, exigence de la Freebox). */
    private fun randomSecret(length: Int): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val random = java.security.SecureRandom()
        return buildString(length) {
            repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    /**
     * Telecharge la configuration wg-quick du client WireGuard.
     *
     * L'endpoint documente ne prend pas de suffixe (`/vpn/download_config/{serveur}/{login}`) ;
     * certaines versions exposent en plus un format explicite (`/plain`). Les deux sont
     * essayes : le premier qui renvoie un vrai fichier `[Interface]` gagne.
     */
    suspend fun wireGuardConfig(serverId: String, login: String): String = withContext(Dispatchers.IO) {
        ensureSession()
        // Le format est obligatoire dans l'API documentee : `plain` d'abord, puis
        // l'ancienne forme sans suffixe pour les firmwares qui la toleraient.
        val candidates = listOf(
            "/vpn/download_config/$serverId/$login/plain",
            "/vpn/download_config/$serverId/$login",
        )
        val failures = mutableListOf<String>()
        for (path in candidates) {
            AppLog.info("VPN", "GET $path")
            val text = runCatching { authText(path) }.getOrElse { error ->
                failures += "$path : ${error.message}"
                null
            } ?: continue
            if (text.contains("[Interface]", ignoreCase = true)) return@withContext text
            failures += "$path : réponse inattendue (${text.take(80)})"
        }
        throw FreeboxException("Configuration WireGuard indisponible : ${failures.joinToString(" ; ")}")
    }

    /** GET authentifie qui renvoie du texte brut (fichiers de configuration). */
    private fun authText(path: String): String {
        val base = baseUrl ?: throw FreeboxException("Freebox non connectee")
        val token = sessionToken ?: throw FreeboxException("Session Freebox absente")
        val request = Request.Builder()
            .url(base + path)
            .header("X-Fbx-App-Auth", token)
            .get()
            .build()
        return streamClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                AppLog.error("Freebox", "HTTP ${response.code} sur $path : ${body.take(200)}")
                throw FreeboxException("Configuration VPN refusée par la Freebox (HTTP ${response.code})")
            }
            body
        }
    }

    data class VpnServer(val id: String, val type: String, val state: String)

    data class VpnPool(val start: String, val end: String, val reserved: Set<String>)

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
            type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "",
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

    private fun JsonElement.asFileOrNull(): FreeboxFile? {
        val obj = this as? JsonObject ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val path = decodePath(obj["filepath"]?.jsonPrimitive?.contentOrNull)
            .ifBlank { decodePath(obj["path"]?.jsonPrimitive?.contentOrNull) }
        return FreeboxFile(
            name = name,
            path = path,
            mimeType = obj["mimetype"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            previewUrl = absoluteShareUrl(obj["preview_url"]?.jsonPrimitive?.contentOrNull),
        )
    }

    /** Le lien de partage peut etre relatif : VLC a besoin d'une URL absolue. */
    private fun absoluteShareUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val base = baseUrl ?: return url
        val origin = base.substringBefore("/api/", base)
        return if (url.startsWith("/")) origin + url else "$origin/$url"
    }

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
        val APP_VERSION: String = BuildConfig.VERSION_NAME
        const val DEFAULT_DIR = FreeboxPaths.DEFAULT_DIR

        /**
         * Droits demandes a la Freebox lors de l'appairage.
         *
         * `settings` est necessaire pour creer le VPN WireGuard (endpoints `/vpn/`),
         * `downloader` pour la file de telechargements et `explorer` pour le disque.
         */
        val REQUESTED_PERMISSIONS = listOf("settings", "downloader", "explorer")
    }
}

/**
 * Lecture de l'objet `permissions` renvoye par `POST /login/session/`.
 *
 * Exemple : `{"downloader": true, "explorer": true, "settings": false}`. Un droit
 * absent vaut `false` (documentation Freebox OS), d'ou l'ensemble des seuls droits vrais.
 */
internal object FreeboxRights {

    /** null quand la box ne renvoie pas de bloc `permissions` (information inconnue). */
    fun parse(element: JsonElement?): Set<String>? {
        val obj = element as? JsonObject ?: return null
        return obj.mapNotNull { (name, value) ->
            val granted = (value as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true
            if (granted) name else null
        }.toSet()
    }
}

internal object SafeErrors {
    fun describe(code: String?, message: String): String = when (code) {
        "eexist" -> "Le dossier existe deja"
        "eacces" -> "Acces refuse par la Freebox ($message)"
        "enoent" -> "Chemin introuvable sur la Freebox ($message)"
        "auth_required", "invalid_session" -> "Session Freebox expiree"
        "insufficient_rights" ->
            "Droits insuffisants : autorisez « Paramètres » pour S3ARCH dans Freebox OS"
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
        "not_bittorrent" -> "La Freebox a enregistre le .torrent comme un fichier au lieu de lancer le torrent"
        "exists" -> "Ce torrent est deja present dans la file Freebox"
        "hibernating" -> "Le gestionnaire de telechargement de la Freebox est en veille"
        else -> if (code.isNullOrBlank()) message else "$message ($code)"
    }
}

class FreeboxException(message: String, val code: String? = null) : Exception(message)
class FreeboxAuthException(message: String) : Exception(message)
