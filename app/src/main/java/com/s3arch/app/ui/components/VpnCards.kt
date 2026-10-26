package com.s3arch.app.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState

private const val VPN_EXPLANATION =
    "S3ARCH peut créer automatiquement une configuration VPN (WireGuard) sur votre Freebox. " +
        "Au lancement, l'application s'y connecte toute seule quand vous n'êtes pas sur votre " +
        "réseau local : la recherche et les téléchargements continuent de fonctionner, où que " +
        "vous soyez. Rien n'est modifié sans votre accord, et vous pouvez désactiver le VPN " +
        "à tout moment."

/** Proposition affichee a la fin de l'appairage de la Freebox. */
@Composable
fun VpnProposalCard(
    enabled: Boolean,
    busy: Boolean,
    onEnable: () -> Unit,
    onDecline: () -> Unit,
) {
    SectionCard(
        title = "Utiliser S3ARCH hors de chez vous",
        subtitle = "Recommandé : un VPN créé sur votre Freebox, sans réglage manuel.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                VPN_EXPLANATION,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onEnable,
                enabled = enabled && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("Création du VPN…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Créer le VPN", style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Pas maintenant",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Carte affichee quand la Freebox n'accorde pas le droit « Paramètres ».
 *
 * L'API `/vpn/` de la box repond `insufficient_rights` (`missing_right: settings`) tant
 * que ce droit n'est pas coche pour S3ARCH : la creation du VPN est donc impossible, et
 * aucun contournement n'existe cote API. On explique exactement ou le cocher.
 */
@Composable
fun FreeboxRightsCard(
    state: UiState,
    onOpenBox: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier,
        title = "Droit « Paramètres » à accorder",
        subtitle = "La Freebox refuse la création du VPN sans cette autorisation.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Votre Freebox a autorisé S3ARCH pour les téléchargements, mais pas pour ses " +
                    "réglages. Or c'est ce droit qui permet de créer le serveur WireGuard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "1. Ouvrez Freebox OS (mafreebox.freebox.fr) depuis le réseau local.\n" +
                    "2. Paramètres de la Freebox → mode avancé → Gestion des accès → Applications.\n" +
                    "3. Sélectionnez S3ARCH et cochez « Modification des réglages de la Freebox », " +
                    "puis validez par OK.\n" +
                    "4. Revenez ici et appuyez sur « J'ai autorisé ».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpenBox,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                ) { Text("Ouvrir Freebox OS", style = MaterialTheme.typography.labelMedium) }
                Button(
                    onClick = onRetry,
                    enabled = !state.vpnBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                ) {
                    if (state.vpnBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("J'ai autorisé", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Ouvre Freebox OS dans le navigateur, sur le reseau local. */
fun openFreeboxOs(context: android.content.Context, host: String) {
    val clean = host.removePrefix("http://").removePrefix("https://").substringBefore('/')
        .ifBlank { "mafreebox.freebox.fr" }
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("http://$clean/"),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** Contenu de la section VPN des reglages (a placer dans une [SectionCard]). */
@Composable
fun VpnSettingsContent(
    state: UiState,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    val settings = state.settings
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Public,
                contentDescription = null,
                tint = if (settings.vpnEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        state.vpnActive -> "VPN actif (hors réseau local)"
                        settings.vpnEnabled -> "VPN prêt, inactif"
                        settings.vpnConfig.isNotBlank() -> "VPN configuré, désactivé"
                        else -> "Aucun VPN créé"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (settings.freeboxLanIp.isNotBlank()) {
                        "Box jointe en ${settings.freeboxLanIp} à travers le tunnel"
                    } else {
                        "WireGuard, créé sur la Freebox"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (settings.vpnEnabled && state.vpnActive) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Text(
            VPN_EXPLANATION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.needsSettingsRight) {
            Text(
                "⚠️ La Freebox n'accorde pas encore le droit « Paramètres » : cochez-le dans " +
                    "Freebox OS → Paramètres de la Freebox → mode avancé → Gestion des accès → " +
                    "Applications → S3ARCH, puis appuyez sur « J'ai autorisé » ci-dessus.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (settings.vpnEnabled) {
            OutlinedButton(
                onClick = onDisable,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
            ) {
                Text("Désactiver le VPN", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = onEnable,
                enabled = !state.vpnBusy && !state.needsSettingsRight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) {
                if (state.vpnBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Text(
                    if (settings.vpnConfig.isBlank()) "Créer le VPN" else "Activer le VPN",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * Presente la demande d'autorisation Android (« autoriser une connexion VPN »).
 *
 * Place une seule fois dans l'application : le ViewModel depose l'Intent dans l'etat,
 * l'ecran le lance et renvoie la reponse.
 */
@Composable
fun VpnConsentEffect(state: UiState, vm: S3archViewModel) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> vm.onVpnConsentResult(context, result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(state.vpnConsentIntent) {
        val intent = state.vpnConsentIntent
        if (intent != null) launcher.launch(intent)
    }
}
