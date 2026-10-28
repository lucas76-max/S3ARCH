package com.s3arch.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.s3arch.app.data.TARGET_FREEBOX
import com.s3arch.app.data.TARGET_PHONE
import com.s3arch.app.ui.DownloadRequest
import com.s3arch.app.util.formatBytes

/** Choix de la destination quand le telephone **et** la Freebox sont configures. */
@Composable
fun DownloadTargetDialog(
    request: DownloadRequest?,
    onPhone: () -> Unit,
    onFreebox: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (request == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
        title = { Text("Télécharger où ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.title, style = MaterialTheme.typography.titleSmall, maxLines = 3)
                Text(
                    "Ce téléchargement peut aller sur ce téléphone ou sur votre Freebox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TargetRow(
                    icon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                    title = "Sur ce téléphone",
                    subtitle = "Le fichier arrive dans le dossier S3ARCH de l'appareil",
                    onClick = onPhone,
                )
                TargetRow(
                    icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
                    title = "Sur la Freebox",
                    subtitle = "Le téléchargement continue même application fermée",
                    onClick = onFreebox,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

@Composable
private fun TargetRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    icon()
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Avertissement explicite avant un telechargement de plus de 10 Go. */
@Composable
fun LargeDownloadDialog(
    request: DownloadRequest?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (request == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
        title = { Text("Gros téléchargement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.title, style = MaterialTheme.typography.titleSmall, maxLines = 3)
                Text(
                    "Téléchargement de ${formatBytes(request.sizeBytes)}, voulez-vous continuer ?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Vérifiez qu'il reste assez de place sur ce téléphone avant de continuer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Continuer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Ajout manuel : plusieurs magnets, liens directs ou fichiers `.torrent`.
 *
 * Le texte est accepte ligne par ligne pour permettre de coller une liste entiere.
 */
@Composable
fun AddSourcesDialog(
    phoneAvailable: Boolean,
    freeboxAvailable: Boolean,
    onDismiss: () -> Unit,
    onPickTorrents: () -> Unit,
    onSubmit: (text: String, target: String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable {
        mutableStateOf(if (phoneAvailable) TARGET_PHONE else TARGET_FREEBOX)
    }
    val canPickTarget = phoneAvailable && freeboxAvailable

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.InsertDriveFile, contentDescription = null) },
        title = { Text("Ajouter des téléchargements") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text("Magnet, lien .torrent ou lien direct") },
                    placeholder = { Text("magnet:?xt=urn:btih:…\nhttps://…/fichier.torrent") },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(18.dp),
                )
                Text(
                    "Un lien par ligne, autant que vous voulez : S3ARCH les ajoute tous en une fois.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (canPickTarget) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TargetChip(
                            selected = target == TARGET_PHONE,
                            label = "Téléphone",
                            onClick = { target = TARGET_PHONE },
                            modifier = Modifier.weight(1f),
                        )
                        TargetChip(
                            selected = target == TARGET_FREEBOX,
                            label = "Freebox",
                            onClick = { target = TARGET_FREEBOX },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                TextButton(onClick = onPickTorrents) {
                    Icon(
                        Icons.Rounded.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Choisir des fichiers .torrent", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(text, target) },
                enabled = text.isNotBlank(),
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun TargetChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
