package com.s3arch.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Base64
import com.s3arch.app.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.s3arch.app.data.AppSettings
import com.s3arch.app.data.FreeboxApi
import com.s3arch.app.data.FreeboxException
import com.s3arch.app.data.FreeboxTask
import com.s3arch.app.data.MediaType
import com.s3arch.app.data.SettingsRepository
import com.s3arch.app.data.SortMode
import com.s3arch.app.data.TorrentResult
import com.s3arch.app.data.TorznabClient
import com.s3arch.app.data.Tracker
import com.s3arch.app.data.TrackerException
import com.s3arch.app.data.TrackerSource
import com.s3arch.app.data.TrackedDownload
import com.s3arch.app.data.UpdateException
import com.s3arch.app.data.UpdateRepository
import com.s3arch.app.data.VlcProxyServer
import com.s3arch.app.data.freeboxPath
import com.s3arch.app.data.playableMedia
import com.s3arch.app.data.vpn.VpnTunnel
import com.s3arch.app.util.ApkInstaller
import com.s3arch.app.util.AppLog
import com.s3arch.app.util.DownloadUrls
import com.s3arch.app.ui.player.PlayerActivity
import com.s3arch.app.util.VlcLauncher
import com.s3arch.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

enum class FreeboxStatus { Idle, Connecting, WaitingApproval, Connected, Error }

data class SetupState(
    val status: FreeboxStatus = FreeboxStatus.Idle,
    val detail: String = "",
)

data class UiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val query: String = "",
    val searching: Boolean = false,
    val searched: Boolean = false,
    val results: List<TorrentResult> = emptyList(),
    val trackerErrors: Map<String, String> = emptyMap(),
    val pendingDownloads: Set<String> = emptySet(),
    val tracked: List<TrackedDownload> = emptyList(),
    val tasks: Map<String, FreeboxTask> = emptyMap(),
    val freeboxError: String? = null,
    val playingId: String? = null,
    /** Transferts en cours d'annulation / suppression (boutons desactives). */
    val actionIds: Set<String> = emptySet(),
    val setup: SetupState = SetupState(),
    val message: String? = null,
    /** Autorisation Android du VPN a presenter (null si deja accordee). */
    val vpnConsentIntent: Intent? = null,
    val vpnBusy: Boolean = false,
    /** Tunnel actuellement monte (hors du reseau local). */
    val vpnActive: Boolean = false,
    /**
     * Droits accordes par la Freebox (null tant qu'aucune session n'a ete ouverte).
     * Le droit `settings` conditionne l'acces a `/vpn/`.
     */
    val freeboxRights: Set<String>? = null,
    /** Release GitHub plus recente que l'application installee. */
    val update: com.s3arch.app.data.AppUpdate? = null,
    /** Le detail de la mise a jour (changelog + bouton) est ouvert. */
    val updateDialogOpen: Boolean = false,
    val updateBusy: Boolean = false,
    val updateProgress: Float = 0f,
) {
    /** Vrai quand la box a explicitement refuse le droit utile au VPN. */
    val needsSettingsRight: Boolean get() = freeboxRights?.contains("settings") == false
}

class S3archViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val torznab = TorznabClient()
    private val freebox = FreeboxApi()
    private val proxy = VlcProxyServer()
    private val updates = UpdateRepository(app)

    @Volatile private var updateChecked = false

    /** APK deja telecharge pour la mise a jour proposee. */
    @Volatile private var downloadedUpdate: File? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    @Volatile private var refreshing = false

    init {
        viewModelScope.launch {
            repo.settings.collect { settings ->
                _state.update { it.copy(settings = settings, loaded = true) }
            }
        }
        viewModelScope.launch {
            repo.downloads.collect { list ->
                _state.update { it.copy(tracked = list) }
            }
        }
    }

    // ------------------------------------------------------------------ recherche

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun toggleTracker(source: TrackerSource) {
        val enabled = _state.value.settings.enabledTrackers
        val updated = if (source.id in enabled) enabled - source.id else enabled + source.id
        viewModelScope.launch { repo.setEnabledTrackers(updated) }
    }

    fun addCustomTracker(label: String, url: String, key: String) {
        if (label.isBlank() || url.isBlank()) {
            message("Nom et URL Torznab obligatoires")
            return
        }
        viewModelScope.launch {
            repo.addCustomTracker(label, url, key)
            message("Tracker « $label » ajouté")
        }
    }

    fun removeCustomTracker(id: String) {
        viewModelScope.launch { repo.removeCustomTracker(id) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repo.setDynamicColor(enabled) }
    }

    fun setAccentColor(id: String) {
        viewModelScope.launch { repo.setAccentColor(id) }
    }

    fun setEnabledTrackers(ids: Set<String>) {
        viewModelScope.launch { repo.setEnabledTrackers(ids) }
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { repo.setSortMode(mode) }
    }

    /** Masque ou reaffiche les torrents sans seeder. */
    fun setHideZeroSeeders(hide: Boolean) {
        viewModelScope.launch { repo.setHideZeroSeeders(hide) }
    }

    /** Filtre facultatif par type de contenu ; un ensemble vide affiche tout. */
    fun toggleType(type: MediaType) {
        val current = _state.value.settings.enabledTypes
        val updated = if (type.id in current) current - type.id else current + type.id
        viewModelScope.launch { repo.setEnabledTypes(updated) }
    }

    fun clearTypeFilter() {
        viewModelScope.launch { repo.setEnabledTypes(emptySet()) }
    }

    fun search(query: String = _state.value.query) {
        val term = query.trim()
        if (term.isBlank()) {
            message("Saisissez un titre à rechercher")
            return
        }
        val settings = _state.value.settings
        val sources = settings.enabledSources()
        if (sources.isEmpty()) {
            message("Ajoutez une clé API dans les réglages pour lancer une recherche")
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(searching = true, query = term, trackerErrors = emptyMap()) }
            val outcomes = coroutineScope {
                sources.map { source ->
                    async {
                        source to runCatching { torznab.search(source, term) }
                    }
                }.awaitAll()
            }

            val results = outcomes.flatMap { (_, result) -> result.getOrDefault(emptyList()) }
                .sortedWith(
                    compareBy(
                        { if (it.sizeBytes > 0) 0 else 1 },
                        { it.sizeBytes },
                        { it.title.lowercase() },
                    ),
                )
            val errors = outcomes.mapNotNull { (tracker, result) ->
                result.exceptionOrNull()?.let { tracker.label to (it.message ?: "Erreur inconnue") }
            }.toMap()

            _state.update {
                it.copy(
                    searching = false,
                    searched = true,
                    results = results,
                    trackerErrors = errors,
                    message = if (results.isEmpty() && errors.isEmpty()) "Aucun résultat pour « $term »" else it.message,
                )
            }
        }
    }

    // ------------------------------------------------------------------ telechargement

    fun download(result: TorrentResult) {
        val key = "${result.trackerId}:${result.id}"
        if (key in _state.value.pendingDownloads) return
        _state.update { it.copy(pendingDownloads = it.pendingDownloads + key) }

        viewModelScope.launch {
            try {
                val settings = _state.value.settings
                val requested = settings.freeboxDownloadDir
                ensureFreebox(settings)
                val folder = try {
                    freebox.ensureFolder(requested)
                } catch (e: Exception) {
                    throw FreeboxException("Dossier « $requested » : ${e.message}")
                }
                if (folder.trimEnd('/') != requested.trim().trimEnd('/')) {
                    AppLog.warn("Download", "Dossier demandé indisponible, repli sur $folder")
                }

                AppLog.info("Download", "« ${result.title} » (${result.trackerLabel})")
                val taskId = launchDownload(result, folder)
                repo.addDownload(
                    TrackedDownload(
                        taskId = taskId,
                        title = result.title,
                        tracker = result.trackerLabel,
                        folder = folder,
                        addedAt = System.currentTimeMillis(),
                        sizeBytes = result.sizeBytes,
                        seeders = result.seeders,
                    ),
                )
                _state.update { it.copy(message = "Téléchargement lancé sur la Freebox") }
                refreshDownloads()
            } catch (e: Exception) {
                AppLog.error("Download", "Echec : ${e.message}")
                _state.update { it.copy(message = e.message ?: "Impossible de lancer le téléchargement") }
            } finally {
                _state.update { it.copy(pendingDownloads = it.pendingDownloads - key) }
            }
        }
    }

    /**
     * Lance une vraie tache BitTorrent sur la Freebox :
     *  1. le telephone recupere le .torrent et l'envoie en fichier (la box le transforme en torrent) ;
     *  2. sinon, le lien magnet (la box cree directement une tache `bt`).
     *
     * L'URL du .torrent (`?t=get&id=…` chez C411/TR4KER) n'est JAMAIS transmise a la box :
     * elle la traite comme un simple fichier et enregistre le .torrent dans le dossier sans
     * lancer le telechargement.
     */
    private suspend fun launchDownload(result: TorrentResult, folder: String): String {
        val torrentUrl = result.downloadUrl.takeIf { DownloadUrls.isHttp(it) }
        val magnet = result.magnet?.takeIf { DownloadUrls.isMagnet(it) }
        var lastError: Throwable? = null

        if (torrentUrl != null) {
            val outcome = runCatching {
                val torrent = torznab.fetchTorrent(torrentUrl)
                freebox.addTorrentFile(torrent, folder, torrentFileName(result.title))
            }
            outcome.getOrNull()?.let { return it }
            lastError = outcome.exceptionOrNull()
            AppLog.warn("Download", "Envoi du fichier .torrent impossible : ${lastError?.message}")
        }

        if (magnet != null) {
            val outcome = runCatching { freebox.addDownload(magnet, folder) }
            outcome.getOrNull()?.let { return it }
            lastError = outcome.exceptionOrNull() ?: lastError
            AppLog.warn("Download", "Ajout par magnet refuse : ${lastError?.message}")
        }

        throw lastError ?: FreeboxException(
            "Aucun moyen de lancer le torrent : fichier .torrent indisponible et pas de lien magnet",
        )
    }

    private fun torrentFileName(title: String): String {
        val base = title.replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.')
            .take(60)
            .ifBlank { "s3arch" }
        return "$base.torrent"
    }

    fun refreshDownloads() {
        val tracked = _state.value.tracked
        if (tracked.isEmpty()) {
            if (_state.value.tasks.isNotEmpty()) _state.update { it.copy(tasks = emptyMap()) }
            return
        }
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                ensureFreebox()
                val all = freebox.listTasks().associateBy { it.id }
                _state.update {
                    it.copy(
                        tasks = tracked.mapNotNull { download -> all[download.taskId] }
                            .associateBy { task -> task.id },
                        freeboxError = null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(freeboxError = e.message) }
            } finally {
                refreshing = false
            }
        }
    }

    /**
     * Annule un transfert : `DELETE /downloads/{id}` arrete la tache et la retire de la
     * file Freebox **sans** effacer ce qui a deja ete telecharge.
     */
    fun cancelDownload(download: TrackedDownload) {
        removeTask(download, eraseFiles = false, success = "Téléchargement annulé")
    }

    /** Supprime le transfert **et** les fichiers du disque de la Freebox (`/erase`). */
    fun deleteFromDisk(download: TrackedDownload) {
        removeTask(download, eraseFiles = true, success = "Fichiers supprimés du disque")
    }

    /**
     * Masque un transfert de la liste suivie par l'app.
     *
     * Aucun appel a la Freebox : la tache continue son cours et les fichiers restent
     * sur le disque. Seule l'entree locale disparait, ce qui permet de nettoyer la
     * liste sans rien perdre. Retrouvable dans Freebox Files, ou en relancant un
     * telechargement du meme contenu.
     */
    fun hideDownload(download: TrackedDownload) {
        val id = download.taskId
        _state.update {
            it.copy(
                tracked = it.tracked.filterNot { tracked -> tracked.taskId == id },
                tasks = it.tasks - id,
                message = "Transfert masqué — le fichier reste sur la Freebox",
            )
        }
        viewModelScope.launch {
            runCatching { repo.removeDownload(id) }
                .onFailure { AppLog.error("Download", "Masquage de $id impossible : ${it.message}") }
        }
    }

    private fun removeTask(download: TrackedDownload, eraseFiles: Boolean, success: String) {
        val id = download.taskId
        if (id in _state.value.actionIds) return
        _state.update { it.copy(actionIds = it.actionIds + id) }
        viewModelScope.launch {
            try {
                ensureFreebox()
                freebox.deleteTask(id, erase = eraseFiles)
                repo.removeDownload(id)
                _state.update {
                    it.copy(tasks = it.tasks - id, message = success)
                }
            } catch (e: Exception) {
                AppLog.error("Download", "Suppression de $id impossible : ${e.message}")
                _state.update { it.copy(message = e.message ?: "Opération impossible") }
            } finally {
                _state.update { it.copy(actionIds = it.actionIds - id) }
            }
        }
    }

    // ------------------------------------------------------------------ lecture VLC

    /**
     * Lit le media dans le lecteur integre (Media3).
     *
     * VLC et le lecteur integre consomment la meme source : une URL **HTTP locale**
     * servie par le proxy, alimentee en LAN par `GET /api/vX/dl/{base64}`. Le lecteur
     * integre affiche en plus le code d'erreur exact quand le flux est refuse.
     */
    fun playInApp(context: Context, download: TrackedDownload) = launchPlayback(download) { target ->
        PlayerActivity.start(context, target.url, target.title)
    }

    /** Lit le media dans VLC (le lecteur integre est le choix par defaut). */
    fun playWithVlc(context: Context, download: TrackedDownload) = launchPlayback(download) { target ->
        if (!VlcLauncher.play(context, target.url, target.title)) {
            message("VLC n'est pas installé : utilisez « Lire dans S3ARCH »")
        }
    }

    private fun launchPlayback(download: TrackedDownload, open: (PlayTarget) -> Unit) {
        if (_state.value.playingId != null) return
        _state.update { it.copy(playingId = download.taskId) }
        viewModelScope.launch {
            try {
                ensureFreebox()
                // Tout le travail reseau (liste des fichiers, test du lien, proxy local)
                // se fait hors du thread principal : un appel bloquant sur le main thread
                // leve NetworkOnMainThreadException, qui n'a aucun message — d'ou
                // l'ancien « Lecture impossible » sans la moindre piste.
                val target = withContext(Dispatchers.IO) { resolvePlayback(download) }
                AppLog.info(
                    "Lecture",
                    if (target.shared) "« ${target.title} » via le lien de partage Freebox"
                    else "« ${target.title} » via le proxy local (HTTP ${target.code})",
                )
                AppLog.info("Lecture", target.url)
                open(target)
            } catch (e: Exception) {
                AppLog.throwable("Lecture", "Lecture impossible", e)
                message(e.message?.takeIf { it.isNotBlank() } ?: "Lecture impossible : ${e.javaClass.simpleName}")
            } finally {
                _state.update { it.copy(playingId = null) }
            }
        }
    }

    /**
     * Prepare la source de lecture.
     *
     * Une tache BitTorrent porte presque toujours un **dossier** (`name`) : on demande
     * donc la liste reelle des fichiers (`/downloads/{id}/files`) et on garde le plus
     * gros media, puis on publie son flux dans le proxy local.
     */
    private suspend fun resolvePlayback(download: TrackedDownload): PlayTarget {
        val task = _state.value.tasks[download.taskId] ?: freebox.task(download.taskId)
        val folder = task?.downloadDir?.takeIf { it.isNotBlank() } ?: download.folder
        val files = freebox.taskFiles(download.taskId)
        AppLog.info("Lecture", "Tache ${download.taskId} : ${files.size} fichier(s)")
        val media = files.playableMedia()
        val title = media?.name ?: task?.name?.takeIf { it.isNotBlank() } ?: download.title

        // Un chemin relatif doit etre rattache au dossier de la tache, sinon
        // `/dl/` ne trouve rien et l'on retombait sur l'acces distant.
        val path = freeboxPath(
            folder = folder,
            name = task?.name?.takeIf { it.isNotBlank() } ?: download.title,
            declared = media?.path,
        )
        val size = media?.size ?: 0L
        AppLog.info("Lecture", "Fichier : $path (${formatBytes(size)})")

        // 1. Proxy local : lecteur integre et VLC recoivent du HTTP simple en 127.0.0.1,
        //    alimente par l'API fichiers de la box sur le reseau local.
        val encodedPath = Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val source = VlcProxyServer.StreamSource(
            encodedPath = encodedPath,
            name = title,
            size = size,
            shareUrl = media?.previewUrl,
        )
        val port = proxy.start { stream, range -> freebox.openMediaBlocking(stream, range) }
        val localPath = proxy.publish(source)
        val localProbe = runCatching { proxy.probe(port, localPath) }
        localProbe.exceptionOrNull()?.let { error ->
            AppLog.warn("Lecture", "Proxy local en echec : ${error.message}")
        }
        if (localProbe.isSuccess) {
            return PlayTarget(
                proxy.urlFor(port, localPath),
                title,
                shared = false,
                code = localProbe.getOrDefault(200),
            )
        }

        // 2. Repli : lien de partage tel quel (acces distant HTTPS).
        val share = media?.previewUrl
        if (!share.isNullOrBlank()) {
            val probe = freebox.probeStream(share)
            AppLog.info("Lecture", "Lien de partage : ${probe.detail} — $share")
            if (probe.ok) return PlayTarget(share, title, shared = true)
        }
        throw FreeboxException(
            "La Freebox n'a pas fourni de flux lisible : " +
                (localProbe.exceptionOrNull()?.message ?: "proxy local indisponible"),
        )
    }

    /** Cible de lecture retenue : lien de partage Freebox ou proxy local. */
    private data class PlayTarget(
        val url: String,
        val title: String,
        val shared: Boolean,
        val code: Int = 0,
    )

    // ------------------------------------------------------------------ Freebox

    fun connectFreebox(host: String, downloadDir: String) {
        if (_state.value.setup.status == FreeboxStatus.Connecting ||
            _state.value.setup.status == FreeboxStatus.WaitingApproval
        ) {
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(setup = SetupState(FreeboxStatus.Connecting, "Recherche de la Freebox…"))
            }
            try {
                val box = freebox.discover(host)
                _state.update {
                    it.copy(
                        setup = SetupState(
                            FreeboxStatus.Connecting,
                            "${box.deviceName} détectée (API ${box.apiVersion}). Demande d'autorisation…",
                        ),
                    )
                }
                val (token, trackId) = freebox.requestAuthorization(box.host, box.apiBase)
                _state.update {
                    it.copy(
                        setup = SetupState(
                            FreeboxStatus.WaitingApproval,
                            "Appuyez sur la flèche → de la Freebox pour autoriser S3ARCH (2 min)",
                        ),
                    )
                }
                freebox.awaitAuthorization(box.host, box.apiBase, trackId)
                _state.update {
                    it.copy(
                        setup = SetupState(FreeboxStatus.Connecting, "Autorisation accordée. Ouverture de la session…"),
                    )
                }
                freebox.connect(box.host, box.apiBase, token)
                syncRights()
                runCatching { freebox.ensureFolder(downloadDir) }

                repo.saveFreebox(box.host, box.apiBase, box.apiVersion, token, downloadDir)
                rememberLanIp(box.host)
                _state.update {
                    it.copy(
                        setup = SetupState(
                            FreeboxStatus.Connected,
                            "Connecté à ${box.deviceName} — API ${box.apiVersion}",
                        ),
                    )
                }
                message("Freebox connectée ✓")
            } catch (e: Exception) {
                _state.update {
                    it.copy(setup = SetupState(FreeboxStatus.Error, e.message ?: "Connexion impossible"))
                }
            }
        }
    }

    fun reconnectFreebox() {
        viewModelScope.launch {
            try {
                ensureFreebox()
                runCatching { freebox.ensureFolder(_state.value.settings.freeboxDownloadDir) }
                    .onFailure { throw FreeboxException("Dossier : ${it.message}") }
                syncRights()
                _state.update {
                    it.copy(setup = SetupState(FreeboxStatus.Connected, "Session Freebox active"))
                }
                message("Freebox connectée ✓")
            } catch (e: Exception) {
                _state.update {
                    it.copy(setup = SetupState(FreeboxStatus.Error, e.message ?: "Connexion impossible"))
                }
            }
        }
    }

    private suspend fun ensureFreebox(settings: AppSettings = _state.value.settings) {
        if (freebox.isConnected) return
        if (!settings.freeboxConfigured) {
            throw FreeboxException("Connectez votre Freebox dans les réglages")
        }
        // Hors du reseau local, l'adresse mDNS de la box ne resout pas : on monte le
        // tunnel et on joint la box par son IP locale, qui passe par le VPN.
        val host = withContext(Dispatchers.IO) {
            val vpnReady = settings.vpnEnabled && settings.vpnConfig.isNotBlank()
            if (vpnReady && settings.freeboxLanIp.isNotBlank() && !probeLan(settings.freeboxHost)) {
                val app = getApplication<Application>()
                if (!VpnTunnel.isUp(app)) VpnTunnel.up(app, settings.vpnConfig)
                settings.freeboxLanIp
            } else {
                settings.freeboxHost
            }
        }
        freebox.connect(host, settings.freeboxApiBase, settings.freeboxAppToken)
        syncRights()
    }

    /** Recopie les droits accordes par la box dans l'etat affiche. */
    private fun syncRights() {
        val rights = if (freebox.permissionsKnown) freebox.grantedPermissions else null
        _state.update { it.copy(freeboxRights = rights) }
    }

    // ------------------------------------------------------------------ VPN

    /**
     * Cree la configuration VPN sur la Freebox puis demande l'autorisation Android.
     *
     * Cote box : activation du serveur WireGuard, creation d'un utilisateur dedie
     * (`s3arch`) et telechargement de sa configuration wg-quick. Cote telephone :
     * Android exige un accord explicite avant qu'une application puisse ouvrir un VPN,
     * d'ou l'Intent renvoye a l'ecran.
     */
    fun enableVpn(context: Context) {
        if (_state.value.vpnBusy) return
        _state.update { it.copy(vpnBusy = true) }
        viewModelScope.launch {
            try {
                ensureFreebox()
                val settings = _state.value.settings
                val config = withContext(Dispatchers.IO) { createVpnConfig(settings) }
                repo.saveVpnConfig(config, settings.vpnLogin)
                rememberLanIp(settings.freeboxHost)

                val consent = VpnTunnel.consentIntent(context)
                if (consent != null) {
                    AppLog.info("VPN", "Autorisation Android demandée")
                    _state.update { it.copy(vpnConsentIntent = consent, vpnBusy = false) }
                } else {
                    startTunnel(context, config)
                }
            } catch (e: Exception) {
                AppLog.throwable("VPN", "Creation du VPN impossible", e)
                _state.update { it.copy(vpnBusy = false) }
                val refused = (e as? FreeboxException)?.code == "insufficient_rights"
                if (refused) {
                    _state.update { it.copy(freeboxRights = (it.freeboxRights ?: emptySet()) - "settings") }
                }
                message(
                    when {
                        refused ->
                            "La Freebox exige le droit « Paramètres » : ouvrez Freebox OS → " +
                                "Paramètres de la Freebox → mode avancé → Gestion des accès → " +
                                "Applications → S3ARCH, cochez « Modification des réglages de la " +
                                "Freebox », puis revenez appuyer sur « Créer le VPN »."
                        else -> e.message?.takeIf { it.isNotBlank() } ?: "Création du VPN impossible"
                    },
                )
            }
        }
    }

    /**
     * Apres avoir coche le droit « Paramètres » sur la box : on rouvre la session pour
     * recuperer les nouveaux droits, puis on relance la creation du VPN si besoin.
     */
    fun retryAfterFreeboxRights(context: Context) {
        if (_state.value.vpnBusy) return
        _state.update { it.copy(vpnBusy = true) }
        viewModelScope.launch {
            try {
                val settings = _state.value.settings
                // Session fermee puis rouverte : le jeu de droits est renvoye a ce moment-la.
                freebox.reset()
                _state.update { it.copy(freeboxRights = null) }
                ensureFreebox(settings)
                syncRights()
                if (!freebox.hasSettingsRight) {
                    _state.update { it.copy(vpnBusy = false) }
                    message("Droit « Paramètres » toujours absent : Enregistrez bien la modification sur la Freebox")
                    return@launch
                }
                _state.update { it.copy(vpnBusy = false) }
                message("Droit « Paramètres » accordé ✓")
                if (settings.vpnConfig.isBlank()) enableVpn(context)
            } catch (e: Exception) {
                AppLog.throwable("Freebox", "Rafraichissement des droits impossible", e)
                _state.update { it.copy(vpnBusy = false) }
                message(e.message?.takeIf { it.isNotBlank() } ?: "Freebox injoignable")
            }
        }
    }

    /** Reponse a la demande d'autorisation Android (« autoriser une connexion VPN »). */
    fun onVpnConsentResult(context: Context, granted: Boolean) {
        _state.update { it.copy(vpnConsentIntent = null) }
        if (!granted) {
            AppLog.warn("VPN", "Autorisation Android refusée")
            message("VPN non activé : Android a refusé la connexion")
            return
        }
        viewModelScope.launch {
            startTunnel(context, _state.value.settings.vpnConfig)
        }
    }

    /** Desactive le VPN (le tunnel est coupe, la configuration reste enregistree). */
    fun disableVpn(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { VpnTunnel.down(context) }
            repo.setVpnEnabled(false)
            _state.update { it.copy(vpnActive = false) }
            message("VPN désactivé")
        }
    }

    fun dismissVpnPrompt() {
        viewModelScope.launch { repo.dismissVpnPrompt() }
    }

    /**
     * Au lancement et au retour au premier plan : on garde le reseau local s'il repond,
     * sinon on monte le tunnel et on adresse les appels a l'IP LAN de la box.
     */
    fun applyNetworkMode() {
        val settings = _state.value.settings
        if (!settings.vpnEnabled || settings.vpnConfig.isBlank() || _state.value.vpnBusy) return
        viewModelScope.launch { withContext(Dispatchers.IO) { syncNetworkMode(settings) } }
    }

    /** L'application passe en arriere-plan : on rend le trafic du telephone normal. */
    fun onAppBackground() {
        val settings = _state.value.settings
        if (!settings.vpnEnabled) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { VpnTunnel.down(getApplication()) }
            _state.update { it.copy(vpnActive = false) }
        }
    }

    /**
     * Cree la configuration VPN cote box, etape par etape.
     *
     * Chaque etape est journalisee avec son nom : en cas d'`internal_error` (reponse
     * generique de la Freebox), le journal indique precisement si le refus vient de
     * l'activation du serveur, de la creation de l'utilisateur ou de la configuration.
     */
    private suspend fun createVpnConfig(settings: AppSettings): String {
        AppLog.info("VPN", "Étape 1/3 : recherche du serveur WireGuard")
        val servers = freebox.vpnServers()
        val server = servers.firstOrNull { it.type == "wireguard" }
            ?: throw FreeboxException("Cette Freebox n'expose pas de serveur WireGuard")
        AppLog.info("VPN", "Serveur « ${server.id} » (état : ${server.state})")

        if (server.state != "started") {
            AppLog.info("VPN", "Étape 2/3 : activation du serveur WireGuard")
            freebox.enableWireGuardServer(server.id)
            // Le serveur demarre de facon asynchrone : le creer trop tot donne un
            // `internal_error` generique cote box.
            if (!freebox.awaitVpnServerStarted(server.id)) {
                AppLog.warn("VPN", "Serveur WireGuard pas encore démarré : nouvel essai de création")
            }
        }

        AppLog.info("VPN", "Étape 3/3 : utilisateur et configuration")
        val login = freebox.ensureWireGuardUser(settings.vpnLogin)
        return freebox.wireGuardConfig(server.id, login)
    }

    private suspend fun startTunnel(context: Context, config: String) {
        val settings = _state.value.settings
        // Sur le reseau local, monter un tunnel vers la box serait inutile (et parfois
        // impossible, le NAT local ne revenant pas toujours). On garde l'accord et le
        // tunnel se montera automatiquement hors du reseau.
        if (probeLan(settings.freeboxHost)) {
            repo.setVpnEnabled(true)
            _state.update { it.copy(vpnBusy = false, vpnActive = false) }
            message("VPN activé : il se connectera automatiquement hors du réseau local")
            return
        }
        val error = withContext(Dispatchers.IO) { VpnTunnel.up(context, config) }
        if (error != null) {
            _state.update { it.copy(vpnBusy = false) }
            message("VPN inactif : $error")
            return
        }
        repo.setVpnEnabled(true)
        if (settings.freeboxLanIp.isNotBlank()) freebox.useHost(settings.freeboxLanIp)
        _state.update { it.copy(vpnBusy = false, vpnActive = true) }
        message("VPN activé : S3ARCH reste joignable hors du réseau local")
    }

    private fun syncNetworkMode(settings: AppSettings) {
        val context = getApplication<Application>()
        if (probeLan(settings.freeboxHost)) {
            if (VpnTunnel.isUp(context)) VpnTunnel.down(context)
            freebox.useHost(settings.freeboxHost)
            AppLog.info("VPN", "Réseau local joignable : VPN inactif")
        } else {
            if (!VpnTunnel.isUp(context)) {
                val error = VpnTunnel.up(context, settings.vpnConfig)
                if (error != null) {
                    AppLog.warn("VPN", "Hors réseau local, tunnel indisponible : $error")
                    return
                }
            }
            freebox.useHost(settings.freeboxLanIp.ifBlank { settings.freeboxHost })
            AppLog.info("VPN", "Hors du réseau local : tunnel actif (box ${settings.freeboxLanIp})")
        }
        _state.update { it.copy(vpnActive = VpnTunnel.isUp(context)) }
    }

    /** Le portail de la box repond-il sur le reseau local ? */
    private fun probeLan(host: String): Boolean = runCatching {
        val clean = host.removePrefix("http://").removePrefix("https://").substringBefore('/')
        Socket().use { socket -> socket.connect(InetSocketAddress(clean, 80), 1_500) }
    }.isSuccess

    /** Memorise l'IP locale de la box : c'est elle qu'on joint a travers le tunnel. */
    private suspend fun rememberLanIp(host: String) = withContext(Dispatchers.IO) {
        val clean = host.removePrefix("http://").removePrefix("https://").substringBefore('/')
        val ip = runCatching { InetAddress.getByName(clean).hostAddress }.getOrNull()
        if (!ip.isNullOrBlank()) repo.saveLanIp(ip)
    }

    // ------------------------------------------------------------------ reglages

    fun saveApiKeys(keys: Map<Tracker, String>, urls: Map<Tracker, String>) {
        viewModelScope.launch {
            repo.saveApiKeys(keys, urls)
            message("Clés API enregistrées")
        }
    }

    fun saveDownloadFolder(dir: String) {
        viewModelScope.launch {
            repo.setDownloadDir(dir)
            message("Dossier de téléchargement enregistré")
        }
    }

    fun testTracker(source: TrackerSource) {
        viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            val outcome = runCatching { torznab.testConnection(source) }
            _state.update { it.copy(searching = false) }
            message(
                outcome.getOrElse { e ->
                    (e as? TrackerException)?.message ?: "Échec du test : ${e.message}"
                },
            )
        }
    }

    fun finishOnboarding() {
        viewModelScope.launch { repo.completeOnboarding() }
    }

    // ------------------------------------------------------------------ mises a jour

    /**
     * Interroge GitHub Releases une seule fois par lancement.
     *
     * Aucune erreur n'est remontee a l'utilisateur : hors ligne, l'application doit
     * demarrer normalement. Une version ignoree n'est plus proposee.
     */
    fun checkForUpdates() {
        if (updateChecked) return
        updateChecked = true
        viewModelScope.launch {
            val found = updates.latest(BuildConfig.VERSION_NAME) ?: return@launch
            if (found.version == _state.value.settings.ignoredUpdateVersion) {
                AppLog.info("Update", "Version ${found.version} ignorée par l'utilisateur")
                return@launch
            }
            _state.update { it.copy(update = found) }
        }
    }

    fun openUpdateDetails() {
        if (_state.value.update == null) return
        _state.update { it.copy(updateDialogOpen = true) }
    }

    fun closeUpdateDetails() {
        _state.update { it.copy(updateDialogOpen = false) }
    }

    /** « Ignorer » : le bandeau ne revient plus pour cette version. */
    fun ignoreUpdate() {
        val update = _state.value.update ?: return
        downloadedUpdate = null
        _state.update { it.copy(update = null, updateDialogOpen = false) }
        viewModelScope.launch { repo.setIgnoredUpdateVersion(update.version) }
        message("Mise à jour ${update.version} ignorée")
    }

    /**
     * Telecharge l'APK de la release puis lance l'installateur Android.
     *
     * Le fichier reste dans le dossier prive de l'application (`updates/`) et n'est
     * partage qu'avec l'installateur via le FileProvider declare dans le manifeste.
     */
    fun installUpdate(context: Context) {
        if (_state.value.updateBusy) return
        val update = _state.value.update ?: return
        viewModelScope.launch {
            _state.update { it.copy(updateBusy = true, updateProgress = 0f) }
            try {
                val file = downloadedUpdate ?: withContext(Dispatchers.IO) {
                    updates.download(update) { progress ->
                        _state.update { it.copy(updateProgress = progress) }
                    }
                }.also { downloadedUpdate = it }

                _state.update { it.copy(updateBusy = false, updateProgress = 1f) }
                val problem = withContext(Dispatchers.IO) { ApkInstaller.install(context, file) }
                if (problem != null) {
                    message(problem)
                } else {
                    _state.update { it.copy(updateDialogOpen = false) }
                    message("Installation de S3ARCH ${update.version}…")
                }
            } catch (e: Exception) {
                AppLog.throwable("Update", "Mise a jour impossible", e)
                _state.update { it.copy(updateBusy = false, updateProgress = 0f) }
                message(
                    when (e) {
                        is UpdateException -> e.message ?: "Mise à jour impossible"
                        else -> e.message?.takeIf { it.isNotBlank() } ?: "Mise à jour impossible"
                    },
                )
            }
        }
    }

    fun message(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun reportError(text: String) = message(text)
}
