package com.s3arch.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
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
import com.s3arch.app.data.VlcProxyServer
import com.s3arch.app.util.AppLog
import com.s3arch.app.util.DownloadUrls
import com.s3arch.app.util.VlcLauncher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val setup: SetupState = SetupState(),
    val message: String? = null,
)

class S3archViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val torznab = TorznabClient()
    private val freebox = FreeboxApi()
    private val proxy = VlcProxyServer()

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

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { repo.setSortMode(mode) }
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
                val folder = settings.freeboxDownloadDir
                ensureFreebox(settings)
                runCatching { freebox.ensureFolder(folder) }
                    .onFailure { throw FreeboxException("Dossier « $folder » : ${it.message}") }

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
     * Trois strategies, de la plus fiable a la plus permissive :
     *  1. le telephone telecharge le .torrent et l'envoie en fichier a la Freebox ;
     *  2. sinon on passe le lien au gestionnaire Freebox (http/https/ftp) ;
     *  3. en dernier recours, le lien magnet.
     * La Freebox refuse souvent les URL (`invalid_url`) : la voie fichier l'evite.
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

        val link = torrentUrl ?: magnet
        if (link != null) {
            val outcome = runCatching { freebox.addDownload(link, folder) }
            outcome.getOrNull()?.let { return it }
            lastError = outcome.exceptionOrNull() ?: lastError
            AppLog.warn("Download", "Ajout par URL refuse : ${lastError?.message}")
        }

        if (magnet != null && magnet != link) {
            val outcome = runCatching { freebox.addDownload(magnet, folder) }
            outcome.getOrNull()?.let { return it }
            lastError = outcome.exceptionOrNull() ?: lastError
            AppLog.warn("Download", "Ajout par magnet refuse : ${lastError?.message}")
        }

        throw lastError ?: FreeboxException("Aucun lien de téléchargement pour ce torrent")
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

    fun removeTracked(download: TrackedDownload) {
        viewModelScope.launch {
            repo.removeDownload(download.taskId)
            _state.update { it.copy(tasks = it.tasks - download.taskId) }
        }
    }

    // ------------------------------------------------------------------ lecture VLC

    fun play(context: Context, download: TrackedDownload) {
        if (_state.value.playingId != null) return
        _state.update { it.copy(playingId = download.taskId) }
        viewModelScope.launch {
            try {
                ensureFreebox()
                val task = _state.value.tasks[download.taskId] ?: freebox.task(download.taskId)
                val name = task?.name?.takeIf { it.isNotBlank() } ?: download.title
                val folder = task?.downloadDir?.takeIf { it.isNotBlank() } ?: download.folder
                val path = "${folder.trimEnd('/')}/$name"

                val port = proxy.start { encodedPath, range -> freebox.openFileBlocking(encodedPath, range) }
                val encodedPath = Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "video.mkv" }
                val url = "http://127.0.0.1:$port/$safeName" +
                    "?path=${Uri.encode(encodedPath)}&name=${Uri.encode(name)}"

                val launched = VlcLauncher.play(context, url, download.title)
                if (!launched) message("Aucun lecteur vidéo trouvé : installez VLC pour lire ce fichier")
            } catch (e: Exception) {
                message(e.message ?: "Lecture impossible")
            } finally {
                _state.update { it.copy(playingId = null) }
            }
        }
    }

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
                runCatching { freebox.ensureFolder(downloadDir) }

                repo.saveFreebox(box.host, box.apiBase, box.apiVersion, token, downloadDir)
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
        freebox.connect(settings.freeboxHost, settings.freeboxApiBase, settings.freeboxAppToken)
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

    fun message(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun reportError(text: String) = message(text)
}
