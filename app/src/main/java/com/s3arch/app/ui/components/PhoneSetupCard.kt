package com.s3arch.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.s3arch.app.data.TARGET_FREEBOX
import com.s3arch.app.data.TARGET_PHONE
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.util.formatBytes

/**
 * Reglages du telechargement direct sur l'appareil.
 *
 * Trois obstacles reels y sont traites : le dossier d'ecriture (Android 11+ interdit
 * d'ecrire dans `Telechargements` sans passer par le selecteur), l'optimisation de
 * batterie (qui tue le service) et les notifications (sans elles, aucune progression
 * visible ecran eteint).
 */
@Composable
fun PhoneSetupCard(
    state: UiState,
    vm: S3archViewModel,
    modifier: Modifier = Modifier,
    showTargets: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var batteryRestricted by remember { mutableStateOf(isBatteryRestricted(context)) }
    var notificationsAllowed by remember { mutableStateOf(notificationsEnabled(context)) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) vm.setPhoneFolder(context, uri)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryRestricted = isBatteryRestricted(context)
                notificationsAllowed = notificationsEnabled(context)
                vm.refreshPhoneStorage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { vm.refreshPhoneStorage() }

    SectionCard(
        modifier = modifier,
        title = "Téléchargement sur le téléphone",
        subtitle = "Le torrent est téléchargé directement sur l'appareil, sans Freebox.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showTargets) {
                TargetSwitch(
                    title = "Téléphone",
                    subtitle = "Télécharger sur cet appareil",
                    checked = state.settings.hasPhoneTarget,
                    icon = Icons.Rounded.PhoneAndroid,
                ) { vm.setDownloadTargets(toggleTarget(state.settings.downloadTargets, TARGET_PHONE)) }

                TargetSwitch(
                    title = "Freebox",
                    subtitle = "Confier les téléchargements à la box",
                    checked = state.settings.hasFreeboxTarget,
                    icon = Icons.Rounded.CloudDownload,
                ) { vm.setDownloadTargets(toggleTarget(state.settings.downloadTargets, TARGET_FREEBOX)) }

                if (state.settings.downloadTargets.size > 1) {
                    Text(
                        "Les deux destinations sont actives : S3ARCH demande où télécharger à chaque lancement.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // Aucun repli silencieux : sans dossier choisi, aucun telechargement ne
            // demarre, sinon les fichiers partiraient dans un dossier que
            // l'utilisateur ne retrouve pas.
            val folderChosen = state.settings.phoneTreeUri.isNotBlank()
            Text(
                if (folderChosen) "Dossier : ${state.phoneStorageLabel}" else "Aucun dossier choisi",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (folderChosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            Text(
                if (folderChosen) {
                    "${formatBytes(state.phoneFreeBytes)} libres sur ce téléphone"
                } else {
                    "Obligatoire : S3ARCH n'enregistre rien tant que le dossier n'est pas choisi."
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (folderChosen) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                "Sur Android 11 et plus, choisissez le dossier « Téléchargements » : Android interdit " +
                    "d'y écrire directement. S3ARCH y crée son sous-dossier et rien d'autre.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (folderChosen) "Changer de dossier" else "Choisir un dossier (obligatoire)")
            }

            StatusRow(
                title = "Optimisation de la batterie",
                detail = if (batteryRestricted) {
                    "Android peut arrêter le téléchargement écran éteint. Autorisez S3ARCH à tourner sans restriction."
                } else {
                    "S3ARCH est autorisée à tourner en arrière-plan."
                },
                ok = !batteryRestricted,
                icon = Icons.Rounded.BatterySaver,
            )
            if (batteryRestricted) {
                OutlinedButton(
                    onClick = { askBatteryExemption(context) },
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.BatterySaver, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Retirer la restriction")
                }
            }

            StatusRow(
                title = "Notifications",
                detail = if (notificationsAllowed) {
                    "La progression reste visible dans la barre de notification."
                } else {
                    "Sans notifications, Android masque la progression et peut arrêter le service."
                },
                ok = notificationsAllowed,
                icon = Icons.Rounded.NotificationsActive,
            )
            if (!notificationsAllowed) {
                OutlinedButton(
                    onClick = { openNotificationSettings(context) },
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Autoriser les notifications")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            TargetSwitch(
                title = "Partager après téléchargement",
                subtitle = "Continue à envoyer le torrent terminé aux autres pairs (seed)",
                checked = state.settings.seedAfterDownload,
                icon = Icons.Rounded.Upload,
            ) { vm.setSeedAfterDownload(!state.settings.seedAfterDownload) }
            if (state.settings.seedAfterDownload) {
                Text(
                    "Le partage continue en arrière-plan, écran éteint : il peut consommer de la " +
                        "batterie et des données. Désactivez-le si l'autonomie passe avant le ratio.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    "Désactivé : le téléchargement s'arrête dès que le fichier est complet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TargetSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun StatusRow(
    title: String,
    detail: String,
    ok: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Au moins une destination doit rester active. */
private fun toggleTarget(current: Set<String>, target: String): Set<String> {
    val next = if (target in current) current - target else current + target
    return next.ifEmpty { setOf(target) }
}

private fun isBatteryRestricted(context: Context): Boolean {
    val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return !manager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun notificationsEnabled(context: Context): Boolean =
    runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(true)

private fun askBatteryExemption(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    val opened = runCatching { context.startActivity(direct) }.isSuccess
    if (!opened) {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}
