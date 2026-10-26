package com.s3arch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.s3arch.app.util.formatBytes
import com.s3arch.app.util.formatEta
import com.s3arch.app.util.formatSpeed
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
    val totalSpeed = state.tasks.values.sumOf { it.rxRate }
    val finished = state.tracked.count { state.tasks[it.taskId]?.isDone == true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScreenHeader(
            title = "Téléchargements",
            subtitle = when {
                state.tracked.isEmpty() -> "Aucun transfert sur la Freebox"
                totalSpeed > 0 -> "$active en cours · ${formatSpeed(totalSpeed)}"
                else -> "$finished terminé${if (finished > 1) "s" else ""} · ${state.tracked.size} au total"
            },
            icon = Icons.Rounded.CloudDownload,
        )

        if (state.freeboxError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(12.dp),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = state.tracked, key = { it.taskId }) { download ->
                    DownloadCard(
                        download = download,
                        task = state.tasks[download.taskId],
                        playing = state.playingId == download.taskId,
                        onPlay = { vm.play(context, download) },
                        onRemove = { vm.removeTracked(download) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    download: TrackedDownload,
    task: FreeboxTask?,
    playing: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val progress = task?.progress ?: 0f
    val done = task?.isDone == true
    val error = task?.isError == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        download.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
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
                        if (task != null && task.totalBytes > 0) {
                            Pill(
                                text = formatBytes(task.totalBytes),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Retirer de la liste",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (task == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(progress * 100).toInt()} %",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
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
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            formatSpeed(task.rxRate),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(12.dp))
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            formatEta(task.totalBytes - task.rxBytes, task.rxRate),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (task.error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        task.error,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (done) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onPlay,
                    enabled = !playing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
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
                    Text("Lire avec VLC")
                }
            }
        }
    }
}
