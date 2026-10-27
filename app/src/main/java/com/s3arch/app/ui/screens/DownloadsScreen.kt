package com.s3arch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s3arch.app.data.FreeboxTask
import com.s3arch.app.data.TrackedDownload
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.EmptyState
import com.s3arch.app.ui.components.Pill
import com.s3arch.app.ui.components.ScreenHeader
import com.s3arch.app.ui.components.WaveProgress
import com.s3arch.app.ui.components.scaleOnPress
import com.s3arch.app.util.formatBytes
import com.s3arch.app.util.formatEta
import com.s3arch.app.util.formatMbps
import com.s3arch.app.util.statusLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun DownloadsScreen(state: UiState, vm: S3archViewModel) {
    val context = LocalContext.current

    // Rafraichissement temps reel tant que l'ecran est affiche.
    LaunchedEffect(Unit) {
        while (isActive) {
            vm.refreshDownloads()
            delay(1200)
        }
    }

    val active = state.tasks.values.count { !it.isDone && !it.isError }
    val totalRate = state.tasks.values.sumOf { it.rxRate }
    val finished = state.tracked.count { state.tasks[it.taskId]?.isDone == true }
    var confirmDelete by remember { mutableStateOf<TrackedDownload?>(null) }
    var confirmHide by remember { mutableStateOf<TrackedDownload?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScreenHeader(
            title = "Transferts",
            subtitle = when {
                state.tracked.isEmpty() -> "Aucun transfert sur la Freebox"
                totalRate > 0 -> "$active en cours · ${formatMbps(totalRate)}"
                else -> "$finished terminé${if (finished > 1) "s" else ""} · ${state.tracked.size} au total"
            },
            eyebrow = "Freebox OS",
            icon = Icons.Rounded.CloudDownload,
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

        if (state.tracked.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.DownloadDone,
                title = "Rien en téléchargement",
                subtitle = "Lancez un téléchargement depuis la recherche : le fichier arrive dans le dossier S3ARCH de la Freebox.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items = state.tracked, key = { it.taskId }) { download ->
                    DownloadCard(
                        download = download,
                        task = state.tasks[download.taskId],
                        playing = state.playingId == download.taskId,
                        busy = download.taskId in state.actionIds,
                        onPlay = { vm.playInApp(context, download) },
                        onPlayVlc = { vm.playWithVlc(context, download) },
                        onCancel = { vm.cancelDownload(download) },
                        onDelete = { confirmDelete = download },
                        onHide = { confirmHide = download },
                    )
                }
            }
        }
    }

    // La suppression disque est definitive : on demande confirmation.
    val target = confirmDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text("Supprimer du disque ?") },
            text = {
                Text(
                    "« ${target.title} » sera retiré de la Freebox et ses fichiers " +
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

    // Le masquage ne touche ni la Freebox ni le disque : on explique ce qui est conserve.
    val hidden = confirmHide
    if (hidden != null) {
        AlertDialog(
            onDismissRequest = { confirmHide = null },
            icon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
            title = { Text("Masquer ce transfert ?") },
            text = {
                Text(
                    "« ${hidden.title} » disparaîtra de cette liste. Le téléchargement continue " +
                        "et les fichiers restent sur la Freebox : rien n'est supprimé du disque.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.hideDownload(hidden)
                        confirmHide = null
                    },
                ) {
                    Text("Masquer")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmHide = null }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun DownloadCard(
    download: TrackedDownload,
    task: FreeboxTask?,
    playing: Boolean,
    busy: Boolean,
    onPlay: () -> Unit,
    onPlayVlc: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onHide: () -> Unit,
) {
    val progress = task?.progress ?: 0f
    val done = task?.isDone == true
    val error = task?.isError == true
    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(30.dp),
            ),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        download.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
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
                        Pill(
                            text =                        if (download.seeders > 0) "${download.seeders} seeders" else "seeders —",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            icon = Icons.Rounded.Upload,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (task == null) {
                WaveProgress(
                    progress = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                WaveProgress(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(progress * 100).toInt()} %",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${formatBytes(task.rxBytes)} / ${formatBytes(task.totalBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!done && !error) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            formatMbps(task.rxRate),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(14.dp))
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            formatEta(task.totalBytes - task.rxBytes, task.rxRate),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (task.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        task.error,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            if (done) {
                Button(
                    onClick = onPlay,
                    enabled = !playing && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
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
                    Text("Lire dans S3ARCH", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(
                    onClick = onPlayVlc,
                    enabled = !playing && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.PlayCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Lire avec VLC",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
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

            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onDelete,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "Supprimer du disque",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Masquage : la tache et les fichiers restent sur la Freebox.
            TextButton(
                onClick = onHide,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Masquer de la liste",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
