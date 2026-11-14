package com.s3arch.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.s3arch.app.data.FreeboxTask
import com.s3arch.app.data.TmdbInfo
import com.s3arch.app.data.TrackedDownload
import com.s3arch.app.util.statusLabel
import com.s3arch.app.torrent.PhoneDownload
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.AddSourcesDialog
import com.s3arch.app.ui.components.EmptyState
import com.s3arch.app.ui.components.Pill
import com.s3arch.app.ui.components.ScreenHeader
import com.s3arch.app.ui.components.WaveProgress
import com.s3arch.app.ui.components.scaleOnPress
import com.s3arch.app.util.formatBytes
import com.s3arch.app.util.formatEta
import com.s3arch.app.util.formatMbps
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Bibliotheque : les telechargements deja lances, presentes comme des films.
 *
 * La carte ne montre que l'affiche, le titre lisible et un bouton « Lire » ; tout le
 * reste (pause, annulation, suppression, partage) vit en bas de la fiche detaillee,
 * ouverte en appuyant sur le titre ou l'affiche. Le comportement des actions est
 * inchange par rapport a l'ancienne page « Transferts ».
 */
@Composable
fun LibraryScreen(state: UiState, vm: S3archViewModel) {
    val context = LocalContext.current

    // Rafraichissement temps reel tant que l'ecran est affiche.
    LaunchedEffect(Unit) {
        while (isActive) {
            vm.refreshDownloads()
            delay(1200)
        }
    }

    // Affiches TMDB : 20 titres a la fois, comme dans la recherche.
    LaunchedEffect(state.tracked.size, state.phoneDownloads.size, state.settings.tmdbKey) {
        val titles = state.tracked.map { it.title } + state.phoneDownloads.map { it.name }
        if (titles.isNotEmpty()) vm.requestLibraryTmdb(titles)
    }

    val active = state.tasks.values.count { !it.isDone && !it.isError }
    val totalRate = state.tasks.values.sumOf { it.rxRate }
    val finished = state.tracked.count { state.tasks[it.taskId]?.isDone == true }
    val phoneActive = state.phoneDownloads.count { it.isActive }
    val globalRate = totalRate + state.phoneDownloads.sumOf { it.rateBps }
    val anyTransfer = state.tracked.isNotEmpty() || state.phoneDownloads.isNotEmpty()
    var confirmDelete by remember { mutableStateOf<TrackedDownload?>(null) }
    var confirmHide by remember { mutableStateOf<TrackedDownload?>(null) }
    var confirmPhone by remember { mutableStateOf<PhoneDownload?>(null) }
    var showAddSources by remember { mutableStateOf(false) }
    var phoneDetail by remember { mutableStateOf<PhoneDownload?>(null) }
    var freeboxDetail by remember { mutableStateOf<TrackedDownload?>(null) }
    val torrentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) vm.importTorrentFiles(context, uris)
        showAddSources = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScreenHeader(
            title = "Bibliothèque",
            subtitle = when {
                !anyTransfer -> "Aucun téléchargement"
                globalRate > 0 -> "${active + phoneActive} en cours · ${formatMbps(globalRate)}"
                else -> "${finished} terminé${if (finished > 1) "s" else ""} · " +
                    "${state.tracked.size + state.phoneDownloads.size} au total"
            },
            icon = Icons.Rounded.VideoLibrary,
            trailing = {
                IconButton(onClick = { showAddSources = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Ajouter un téléchargement")
                }
            },
        )

        if (state.freeboxError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        RoundedCornerShape(18.dp),
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.freeboxError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (!anyTransfer) {
            EmptyState(
                icon = Icons.Rounded.VideoLibrary,
                title = "Bibliothèque vide",
                subtitle = "Lancez un téléchargement depuis la recherche, ou ajoutez un magnet, un lien " +
                    ".torrent ou un fichier avec le bouton +.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.phoneDownloads.isNotEmpty()) {
                    item(key = "phone-header") {
                        SectionLabel(
                            "Sur le téléphone",
                            state.phoneStorageLabel.ifBlank { "Aucun dossier choisi" },
                        )
                    }
                    items(items = state.phoneDownloads, key = { "phone-" + it.id }) { download ->
                        PhoneLibraryCard(
                            download = download,
                            info = state.libraryTmdb[vm.prettyTitle(download.name)],
                            onOpen = { phoneDetail = download },
                            onPlay = { vm.playPhoneDownload(context, download) },
                        )
                    }
                }
                if (state.tracked.isNotEmpty()) {
                    item(key = "freebox-header") {
                        SectionLabel("Sur la Freebox", state.settings.freeboxDownloadDir)
                    }
                    items(items = state.tracked, key = { it.taskId }) { download ->
                        FreeboxLibraryCard(
                            download = download,
                            task = state.tasks[download.taskId],
                            info = state.libraryTmdb[vm.prettyTitle(download.title)],
                            playing = state.playingId == download.taskId,
                            onOpen = { freeboxDetail = download },
                            onPlay = { vm.playInApp(context, download) },
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- fiches detail

    phoneDetail?.let { download ->
        PhoneDetailDialog(
            download = download,
            info = state.libraryTmdb[vm.prettyTitle(download.name)],
            onPlay = {
                vm.playPhoneDownload(context, download)
                phoneDetail = null
            },
            onPause = { vm.pausePhoneDownload(download.id) },
            onResume = { vm.resumePhoneDownload(download.id) },
            onDelete = {
                phoneDetail = null
                confirmPhone = download
            },
            onDismiss = { phoneDetail = null },
        )
    }

    freeboxDetail?.let { download ->
        FreeboxDetailDialog(
            download = download,
            task = state.tasks[download.taskId],
            info = state.libraryTmdb[vm.prettyTitle(download.title)],
            playing = state.playingId == download.taskId,
            busy = download.taskId in state.actionIds,
            onPlay = {
                vm.playInApp(context, download)
                freeboxDetail = null
            },
            onPlayVlc = {
                vm.playWithVlc(context, download)
                freeboxDetail = null
            },
            onCancel = { vm.cancelDownload(download) },
            onDelete = {
                freeboxDetail = null
                confirmDelete = download
            },
            onHide = {
                freeboxDetail = null
                confirmHide = download
            },
            onDismiss = { freeboxDetail = null },
        )
    }

    // ---------------------------------------------------------------- confirmations

    val target = confirmDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text("Supprimer du disque ?") },
            text = {
                Text(
                    "« ${vm.prettyTitle(target.title)} » sera retiré de la Freebox et ses fichiers " +
                        "effacés définitivement du disque.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteFromDisk(target)
                        confirmDelete = null
                    },
                ) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Garder") }
            },
        )
    }

    val hidden = confirmHide
    if (hidden != null) {
        AlertDialog(
            onDismissRequest = { confirmHide = null },
            icon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
            title = { Text("Masquer ce téléchargement ?") },
            text = {
                Text(
                    "« ${vm.prettyTitle(hidden.title)} » disparaîtra de cette bibliothèque. Le " +
                        "téléchargement continue et les fichiers restent sur la Freebox : rien n'est " +
                        "supprimé du disque.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.hideDownload(hidden)
                        confirmHide = null
                    },
                ) { Text("Masquer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmHide = null }) { Text("Annuler") }
            },
        )
    }

    if (showAddSources) {
        AddSourcesDialog(
            phoneAvailable = true,
            freeboxAvailable = state.settings.freeboxConfigured,
            onDismiss = { showAddSources = false },
            onPickTorrents = {
                torrentPicker.launch(arrayOf("application/x-bittorrent", "application/octet-stream", "*/*"))
            },
            onSubmit = { text, target ->
                vm.addManualSources(text, target, context)
                showAddSources = false
            },
        )
    }

    val phoneTarget = confirmPhone
    if (phoneTarget != null) {
        AlertDialog(
            onDismissRequest = { confirmPhone = null },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text("Retirer ce téléchargement ?") },
            text = {
                Text(
                    "« ${vm.prettyTitle(phoneTarget.name)} » sera retiré de la bibliothèque. " +
                        "Choisissez si les fichiers déjà téléchargés doivent aussi être effacés du téléphone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removePhoneDownload(phoneTarget.id, true)
                        confirmPhone = null
                    },
                ) { Text("Supprimer + fichiers", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            vm.removePhoneDownload(phoneTarget.id, false)
                            confirmPhone = null
                        },
                    ) { Text("Garder les fichiers") }
                    TextButton(onClick = { confirmPhone = null }) { Text("Annuler") }
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Affiche du film, ou vignette neutre quand TMDB ne connait pas le titre. */
@Composable
private fun LibraryPoster(info: TmdbInfo?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val url = info?.posterUrl
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = info?.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

/** Barre de progression + pourcentage, reutilisee par les deux types de cartes. */
@Composable
private fun ProgressBlock(
    progress: Float?,
    error: Boolean,
    rateLine: String?,
    detailLine: String?,
) {
    WaveProgress(
        progress = progress,
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    if (progress != null) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${(progress * 100).toInt()} %",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            if (detailLine != null) {
                Text(
                    detailLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (rateLine != null) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Speed,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(5.dp))
            Text(
                rateLine,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Carte d'un téléchargement sur le téléphone : affiche, titre lisible, bouton « Lire ». */
@Composable
private fun PhoneLibraryCard(
    download: PhoneDownload,
    info: TmdbInfo?,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val done = download.isDone || download.isSeeding
    val error = download.isError
    val title = info?.displayTitle?.takeIf { it.isNotBlank() } ?: download.name
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(28.dp),
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen),
                verticalAlignment = Alignment.Top,
            ) {
                LibraryPoster(
                    info = info,
                    modifier = Modifier
                        .width(62.dp)
                        .aspectRatio(2f / 3f),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Pill(
                            text = "Téléphone",
                            tint = MaterialTheme.colorScheme.tertiary,
                            icon = Icons.Rounded.PhoneAndroid,
                        )
                        Pill(
                            text = when {
                                download.isSeeding -> "Partage"
                                done -> "Terminé"
                                error -> "Erreur"
                                download.isPaused -> "En pause"
                                download.state == PhoneDownload.STATE_METADATA -> "Métadonnées"
                                download.state == PhoneDownload.STATE_CHECKING -> "Vérification"
                                else -> "Téléchargement"
                            },
                            tint = when {
                                done -> MaterialTheme.colorScheme.primary
                                error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.secondary
                            },
                        )
                    }
                    if (!done) {
                        Spacer(Modifier.height(10.dp))
                        ProgressBlock(
                            progress = download.progress,
                            error = error,
                            rateLine = if (download.rateBps > 0) formatMbps(download.rateBps) else null,
                            detailLine = "${formatBytes(download.doneBytes)} / ${formatBytes(download.totalBytes)}",
                        )
                    }
                    if (download.error != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            download.error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPlay,
                enabled = done,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Lire", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Carte d'un téléchargement Freebox : même présentation, mêmes actions dans la fiche. */
@Composable
private fun FreeboxLibraryCard(
    download: TrackedDownload,
    task: FreeboxTask?,
    info: TmdbInfo?,
    playing: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val progress = task?.progress
    val done = task?.isDone == true
    val error = task?.isError == true
    val interaction = remember { MutableInteractionSource() }
    val title = info?.displayTitle?.takeIf { it.isNotBlank() } ?: download.title
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(28.dp),
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen),
                verticalAlignment = Alignment.Top,
            ) {
                LibraryPoster(
                    info = info,
                    modifier = Modifier
                        .width(62.dp)
                        .aspectRatio(2f / 3f),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Pill(download.tracker, tint = MaterialTheme.colorScheme.secondary)
                        Pill(
                            text = when {
                                task == null -> "Synchronisation"
                                done -> "Terminé"
                                error -> "Erreur"
                                else -> statusLabel(task.status)
                            },
                            tint = when {
                                done -> MaterialTheme.colorScheme.primary
                                error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.tertiary
                            },
                        )
                    }
                    if (!done) {
                        Spacer(Modifier.height(10.dp))
                        ProgressBlock(
                            progress = progress,
                            error = error,
                            rateLine = if (task != null && task.rxRate > 0) formatMbps(task.rxRate) else null,
                            detailLine = task?.let { "${formatBytes(it.rxBytes)} / ${formatBytes(it.totalBytes)}" },
                        )
                    }
                    if (task?.error != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            task.error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onPlay,
                enabled = done && !playing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .scaleOnPress(interaction),
                shape = RoundedCornerShape(50),
                interactionSource = interaction,
            ) {
                if (playing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text("Lire", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Fiche détaillée d'un téléchargement sur le téléphone. */
@Composable
private fun PhoneDetailDialog(
    download: PhoneDownload,
    info: TmdbInfo?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val done = download.isDone || download.isSeeding
    val title = info?.displayTitle?.takeIf { it.isNotBlank() } ?: download.name
    DetailShell(
        info = info,
        title = title,
        rawName = download.name,
        onDismiss = onDismiss,
        details = {
            DetailRow("Destination", "Téléphone")
            DetailRow(
                "État",
                when {
                    download.isSeeding -> "Partage en cours"
                    done -> "Terminé"
                    download.isPaused -> "En pause"
                    download.isError -> "Erreur"
                    else -> "Téléchargement"
                },
            )
            DetailRow(
                "Taille",
                "${formatBytes(download.doneBytes)} / ${formatBytes(download.totalBytes)}",
            )
            if (download.peers > 0) DetailRow("Pairs", "${download.peers}")
            if (download.uploadedBytes > 0) {
                DetailRow("Envoyé", formatBytes(download.uploadedBytes))
            }
            if (!done && download.rateBps > 0) {
                DetailRow("Débit", formatMbps(download.rateBps))
                DetailRow(
                    "Restant",
                    formatEta(download.totalBytes - download.doneBytes, download.rateBps),
                )
            }
            download.savePath.takeIf { it.isNotBlank() }?.let { DetailRow("Fichier", it) }
        },
        actions = {
            Button(
                onClick = onPlay,
                enabled = done,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Lire", style = MaterialTheme.typography.labelLarge)
            }
            when {
                download.isSeeding -> OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Arrêter le partage", style = MaterialTheme.typography.labelLarge)
                }

                download.isError || download.isPaused -> Button(
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Reprendre", style = MaterialTheme.typography.labelLarge)
                }

                else -> OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Mettre en pause", style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Retirer de la bibliothèque",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

/** Fiche détaillée d'un téléchargement Freebox : toutes les actions sont ici. */
@Composable
private fun FreeboxDetailDialog(
    download: TrackedDownload,
    task: FreeboxTask?,
    info: TmdbInfo?,
    playing: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onPlayVlc: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val done = task?.isDone == true
    val error = task?.isError == true
    val title = info?.displayTitle?.takeIf { it.isNotBlank() } ?: download.title
    DetailShell(
        info = info,
        title = title,
        rawName = download.title,
        onDismiss = onDismiss,
        details = {
            DetailRow("Destination", "Freebox")
            DetailRow("Tracker", download.tracker)
            DetailRow(
                "État",
                when {
                    task == null -> "Synchronisation"
                    done -> "Terminé"
                    error -> "Erreur"
                    else -> statusLabel(task.status)
                },
            )
            if (task != null) {
                DetailRow("Taille", "${formatBytes(task.rxBytes)} / ${formatBytes(task.totalBytes)}")
                if (!done && task.rxRate > 0) {
                    DetailRow("Débit", formatMbps(task.rxRate))
                    DetailRow("Restant", formatEta(task.totalBytes - task.rxBytes, task.rxRate))
                }
            }
            DetailRow("Dossier", download.folder)
            if (download.seeders > 0) DetailRow("Sources", "${download.seeders}")
            task?.error?.let { DetailRow("Erreur", it) }
        },
        actions = {
            Button(
                onClick = onPlay,
                enabled = done && !playing && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Lire", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onPlayVlc, enabled = done && !playing && !busy) {
                Icon(
                    Icons.Rounded.PlayCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Lire avec VLC", style = MaterialTheme.typography.labelLarge)
            }
            if (!done) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(
                        Icons.Rounded.StopCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (error) "Retirer de la file" else "Annuler le téléchargement",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(2.dp))
            TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Supprimer du disque",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onHide, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Masquer de la bibliothèque",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** Structure commune aux deux fiches : affiche, titre, details, puis actions en bas. */
@Composable
private fun DetailShell(
    info: TmdbInfo?,
    title: String,
    rawName: String,
    onDismiss: () -> Unit,
    details: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.Top) {
                            LibraryPoster(
                                info = info,
                                modifier = Modifier
                                    .width(96.dp)
                                    .aspectRatio(2f / 3f),
                            )
                            Spacer(Modifier.size(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (info?.year?.isNotBlank() == true) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Pill(info.year, tint = MaterialTheme.colorScheme.primary)
                                        if (info.rating > 0.0) {
                                            Pill(
                                                "%.1f/10".format(java.util.Locale.FRANCE, info.rating),
                                                tint = MaterialTheme.colorScheme.tertiary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (info != null && info.displayOverview.isNotBlank()) {
                        item {
                            Text(
                                info.displayOverview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (rawName.isNotBlank() && rawName != title) {
                        item {
                            Text(
                                rawName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            details()
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        actions()
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
