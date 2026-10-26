package com.s3arch.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.s3arch.app.data.Tracker
import com.s3arch.app.ui.FreeboxStatus
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.LogoMark
import com.s3arch.app.ui.components.SectionCard
import com.s3arch.app.ui.components.TrackerKeyEditor

/**
 * Premier demarrage : saisie des cles API puis connexion a la Freebox
 * (autorisation officielle Freebox OS, avec appui sur la fleche du boitier).
 */
@Composable
fun SetupScreen(
    state: UiState,
    vm: S3archViewModel,
    snackbarHostState: SnackbarHostState,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var c411Key by rememberSaveable { mutableStateOf(state.settings.c411Key) }
    var c411Url by rememberSaveable { mutableStateOf(state.settings.c411Url) }
    var tr4kerKey by rememberSaveable { mutableStateOf(state.settings.tr4kerKey) }
    var tr4kerUrl by rememberSaveable { mutableStateOf(state.settings.tr4kerUrl) }
    var v3xKey by rememberSaveable { mutableStateOf(state.settings.v3xKey) }
    var v3xUrl by rememberSaveable { mutableStateOf(state.settings.v3xUrl) }
    var host by rememberSaveable { mutableStateOf(state.settings.freeboxHost) }
    var folder by rememberSaveable { mutableStateOf(state.settings.freeboxDownloadDir) }

    val hasKey = listOf(c411Key, tr4kerKey, v3xKey).any { it.isNotBlank() }
    val connected = state.setup.status == FreeboxStatus.Connected
    val busy = state.setup.status == FreeboxStatus.Connecting || state.setup.status == FreeboxStatus.WaitingApproval
    val progress by animateFloatAsState(if (step == 0) 0.5f else 1f, label = "setup-progress")

    fun persistKeys() {
        vm.saveApiKeys(
            mapOf(
                Tracker.C411 to c411Key,
                Tracker.TR4KER to tr4kerKey,
                Tracker.V3X to v3xKey,
            ),
            mapOf(
                Tracker.C411 to c411Url,
                Tracker.TR4KER to tr4kerUrl,
                Tracker.V3X to v3xUrl,
            ),
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoMark(52)
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text("S3ARCH", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Recherche torrents · Freebox · VLC",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (step == 0) "Étape 1 sur 2 · Clés API" else "Étape 2 sur 2 · Freebox",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "setup-step",
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (currentStep == 0) {
                        SectionCard(
                            title = "Vos clés API",
                            subtitle = "S3ARCH interroge C411, TR4KER et V3X. Les clés restent sur cet appareil.",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                TrackerKeyEditor(
                                    tracker = Tracker.C411,
                                    keyValue = c411Key,
                                    urlValue = c411Url,
                                    onKeyChange = { c411Key = it },
                                    onUrlChange = { c411Url = it },
                                    onTest = { vm.testTracker(Tracker.C411, c411Key, c411Url) },
                                    testEnabled = !state.searching,
                                )
                                TrackerKeyEditor(
                                    tracker = Tracker.TR4KER,
                                    keyValue = tr4kerKey,
                                    urlValue = tr4kerUrl,
                                    onKeyChange = { tr4kerKey = it },
                                    onUrlChange = { tr4kerUrl = it },
                                    onTest = { vm.testTracker(Tracker.TR4KER, tr4kerKey, tr4kerUrl) },
                                    testEnabled = !state.searching,
                                )
                                TrackerKeyEditor(
                                    tracker = Tracker.V3X,
                                    keyValue = v3xKey,
                                    urlValue = v3xUrl,
                                    onKeyChange = { v3xKey = it },
                                    onUrlChange = { v3xUrl = it },
                                    onTest = { vm.testTracker(Tracker.V3X, v3xKey, v3xUrl) },
                                    testEnabled = !state.searching,
                                )
                            }
                        }
                        Text(
                            "Une seule clé suffit pour démarrer. Vous pourrez compléter plus tard dans les réglages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SectionCard(
                            title = "Connexion à la Freebox",
                            subtitle = "S3ARCH utilise l'API officielle Freebox OS et crée un dossier dédié aux téléchargements.",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("Adresse de la Freebox") },
                                    supportingText = { Text("mafreebox.freebox.fr par défaut") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = folder,
                                    onValueChange = { folder = it },
                                    label = { Text("Dossier de téléchargement") },
                                    supportingText = { Text("/Disque dur/S3ARCH") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AnimatedVisibility(visible = state.setup.status != FreeboxStatus.Idle) {
                                    FreeboxStatusBlock(state)
                                }
                                Button(
                                    onClick = { vm.connectFreebox(host, folder) },
                                    enabled = !busy && host.isNotBlank(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    if (busy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.size(10.dp))
                                    }
                                    Text(
                                        when {
                                            connected -> "Autoriser à nouveau"
                                            busy -> "Connexion en cours…"
                                            else -> "Connecter la Freebox"
                                        },
                                    )
                                }
                            }
                        }
                        Text(
                            "Une fois la demande envoyée, validez sur la Freebox en appuyant sur la flèche → " +
                                "(menu de l'écran) dans les deux minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(96.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step == 1) {
                        TextButton(onClick = { step = 0 }) { Text("Retour") }
                    }
                    Spacer(Modifier.weight(1f))
                    if (step == 0) {
                        Button(
                            onClick = {
                                persistKeys()
                                step = 1
                            },
                            enabled = hasKey,
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Continuer")
                        }
                    } else {
                        TextButton(
                            onClick = {
                                persistKeys()
                                onFinish()
                            },
                        ) { Text("Plus tard") }
                        Spacer(Modifier.size(8.dp))
                        Button(
                            onClick = {
                                persistKeys()
                                onFinish()
                            },
                            enabled = connected,
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Terminer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeboxStatusBlock(state: UiState) {
    val setup = state.setup
    val color = when (setup.status) {
        FreeboxStatus.Connected -> MaterialTheme.colorScheme.primary
        FreeboxStatus.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (setup.status) {
            FreeboxStatus.Connected -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
            FreeboxStatus.Error -> Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
            FreeboxStatus.WaitingApproval -> Icon(
                Icons.Rounded.Router,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
            else -> CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.Transparent, CircleShape),
                strokeWidth = 2.dp,
                color = color,
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            setup.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
