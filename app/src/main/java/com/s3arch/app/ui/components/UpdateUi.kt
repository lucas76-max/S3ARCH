package com.s3arch.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s3arch.app.data.AppUpdate
import com.s3arch.app.util.formatBytes

/**
 * Bandeau discret au-dessus de la barre de navigation : « Mise a jour disponible. »
 * avec les boutons « Ignorer » (ne revient plus pour cette version) et « Ouvrir »
 * (changelog + installation).
 */
@Composable
fun UpdateBanner(
    update: AppUpdate?,
    onIgnore: () -> Unit,
    onOpen: () -> Unit,
) {
    if (update == null) return
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    "Mise à jour disponible.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "S3ARCH ${update.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            TextButton(onClick = onIgnore) {
                Text(
                    "Ignorer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Ouvrir", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Detail de la mise a jour : changelog de la release et bouton « Mettre a jour ».
 * Le telechargement de l'APK et l'installation sont geres par le ViewModel.
 */
@Composable
fun UpdateDialog(
    update: AppUpdate?,
    visible: Boolean,
    busy: Boolean,
    progress: Float,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    if (update == null || !visible) return
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Icon(
                Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("S3ARCH ${update.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    update.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (update.apkSize > 0L) {
                    Text(
                        "APK · ${formatBytes(update.apkSize)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = update.notes.ifBlank { "Aucun détail fourni pour cette version." },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 230.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
                if (busy) {
                    WaveProgress(
                        progress = progress.takeIf { it > 0f },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        height = 8.dp,
                    )
                    Text(
                        if (progress > 0f) {
                            "Téléchargement de l'APK… ${(progress * 100).toInt()} %"
                        } else {
                            "Préparation du téléchargement…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (update.apkUrl == null) {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl)),
                            )
                        }
                    },
                    shape = RoundedCornerShape(50),
                ) { Text("Voir la release") }
            } else {
                Button(
                    onClick = onInstall,
                    enabled = !busy,
                    shape = RoundedCornerShape(50),
                ) { Text(if (busy) "Mise à jour…" else "Mettre à jour") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Plus tard") }
        },
    )
}
