package com.s3arch.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s3arch.app.data.Tracker
import com.s3arch.app.data.TrackerSource
import com.s3arch.app.ui.FreeboxStatus
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.AddTrackerDialog
import com.s3arch.app.ui.components.FreeboxRightsCard
import com.s3arch.app.ui.components.LogoMark
import com.s3arch.app.ui.components.SectionCard
import com.s3arch.app.ui.components.VpnProposalCard
import com.s3arch.app.ui.components.TrackerKeyEditor
import com.s3arch.app.ui.components.openFreeboxOs
import com.s3arch.app.ui.components.scaleOnPress
import androidx.compose.foundation.interaction.MutableInteractionSource

/**
 * Premier demarrage : cles API puis connexion Freebox (autorisation officielle).
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
    var showAddTracker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val hasKey = listOf(c411Key, tr4kerKey, v3xKey).any { it.isNotBlank() } ||
        state.settings.customTrackers.any { it.key.isNotBlank() }
    val connected = state.setup.status == FreeboxStatus.Connected
    val busy = state.setup.status == FreeboxStatus.Connecting ||
        state.setup.status == FreeboxStatus.WaitingApproval
    val progress by animateFloatAsState(
        targetValue = if (step == 0) 0.5f else 1f,
        animationSpec = spring(),
        label = "setup-progress",
    )

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

    if (showAddTracker) {
        AddTrackerDialog(
            onDismiss = { showAddTracker = false },
            onConfirm = { label, url, key ->
                vm.addCustomTracker(label, url, key)
                showAddTracker = false
            },
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
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoMark(56)
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text("S3ARCH", style = MaterialTheme.typography.displaySmall)
                        Text(
                            "Recherche · Freebox · VLC",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepPill(index = 1, label = "Clés API", active = step == 0)
                    StepPill(index = 2, label = "Freebox", active = step == 1)
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // weight(1f) : indispensable pour que la barre d'action reste visible.
            AnimatedContent(
                targetState = step,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                                    onTest = {
                                        vm.testTracker(
                                            TrackerSource(
                                                Tracker.C411.id,
                                                Tracker.C411.label,
                                                c411Url,
                                                c411Key,
                                                Tracker.C411,
                                            ),
                                        )
                                    },
                                    testEnabled = !state.searching,
                                )
                                TrackerKeyEditor(
                                    tracker = Tracker.TR4KER,
                                    keyValue = tr4kerKey,
                                    urlValue = tr4kerUrl,
                                    onKeyChange = { tr4kerKey = it },
                                    onUrlChange = { tr4kerUrl = it },
                                    onTest = {
                                        vm.testTracker(
                                            TrackerSource(
                                                Tracker.TR4KER.id,
                                                Tracker.TR4KER.label,
                                                tr4kerUrl,
                                                tr4kerKey,
                                                Tracker.TR4KER,
                                            ),
                                        )
                                    },
                                    testEnabled = !state.searching,
                                )
                                TrackerKeyEditor(
                                    tracker = Tracker.V3X,
                                    keyValue = v3xKey,
                                    urlValue = v3xUrl,
                                    onKeyChange = { v3xKey = it },
                                    onUrlChange = { v3xUrl = it },
                                    onTest = {
                                        vm.testTracker(
                                            TrackerSource(
                                                Tracker.V3X.id,
                                                Tracker.V3X.label,
                                                v3xUrl,
                                                v3xKey,
                                                Tracker.V3X,
                                            ),
                                        )
                                    },
                                    testEnabled = !state.searching,
                                )

                                if (state.settings.customTrackers.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        state.settings.customTrackers.forEach { custom ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Rounded.Add,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.size(8.dp))
                                                Text(
                                                    "${custom.label} · ${custom.url}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Ajout d'un tracker tiers : texte discret.
                        TextButton(onClick = { showAddTracker = true }) {
                            Text(
                                "Ajouter un tracker Torznab tiers",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            )
                        }

                        Text(
                            "Une seule clé suffit pour démarrer. Vous pourrez compléter plus tard dans les réglages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SectionCard(
                            title = "Connexion à la Freebox",
                            subtitle = "API officielle Freebox OS. Un dossier S3ARCH sera créé pour vos téléchargements.",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("Adresse de la Freebox") },
                                    supportingText = { Text("mafreebox.freebox.fr par défaut") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = folder,
                                    onValueChange = { folder = it },
                                    label = { Text("Dossier de téléchargement") },
                                    supportingText = { Text("/Disque dur/S3ARCH") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AnimatedVisibility(visible = state.setup.status != FreeboxStatus.Idle) {
                                    FreeboxStatusBlock(state)
                                }
                                ConnectButton(
                                    label = when {
                                        connected -> "Autoriser à nouveau"
                                        busy -> "Connexion en cours…"
                                        else -> "Connecter la Freebox"
                                    },
                                    enabled = !busy && host.isNotBlank(),
                                    busy = busy,
                                    onClick = { vm.connectFreebox(host, folder) },
                                )
                            }
                        }
                        Text(
                            "Une fois la demande envoyée, validez sur la Freebox en appuyant sur la flèche → " +
                                "dans les deux minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Proposition de VPN : une seule fois, refusable.
                        if (connected && !state.settings.vpnPromptAnswered &&
                            state.settings.vpnConfig.isBlank()
                        ) {
                            VpnProposalCard(
                                enabled = !state.needsSettingsRight,
                                busy = state.vpnBusy,
                                onEnable = { vm.enableVpn(context) },
                                onDecline = { vm.dismissVpnPrompt() },
                            )
                        }

                        // La box refuse /vpn/ sans le droit « Paramètres » : on l'explique
                        // ici, avant meme que l'utilisateur ne tente la creation.
                        if (connected && state.needsSettingsRight) {
                            FreeboxRightsCard(
                                state = state,
                                onOpenBox = { openFreeboxOs(context, state.settings.freeboxHost) },
                                onRetry = { vm.retryAfterFreeboxRights(context) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step == 1) {
                        TextButton(onClick = { step = 0 }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Retour")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (step == 0) {
                        Button(
                            onClick = {
                                persistKeys()
                                step = 1
                            },
                            enabled = hasKey,
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text("Continuer", style = MaterialTheme.typography.labelLarge)
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
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Terminer", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectButton(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scaleOnPress(interaction),
        shape = RoundedCornerShape(50),
        interactionSource = interaction,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.size(10.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StepPill(index: Int, label: String, active: Boolean) {
    val container = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "$index",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content.copy(alpha = 0.7f),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
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
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(16.dp),
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
