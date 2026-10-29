package com.s3arch.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhoneAndroid
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s3arch.app.data.FreeboxPaths
import com.s3arch.app.data.TARGET_FREEBOX
import com.s3arch.app.data.TARGET_PHONE
import com.s3arch.app.data.Tracker
import com.s3arch.app.data.TrackerSource
import com.s3arch.app.ui.FreeboxStatus
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.AddTrackerDialog
import com.s3arch.app.ui.components.LogoMark
import com.s3arch.app.ui.components.PhoneSetupCard
import com.s3arch.app.ui.components.SectionCard
import com.s3arch.app.ui.components.TrackerKeyEditor
import com.s3arch.app.ui.components.TrackerChoice
import com.s3arch.app.ui.components.TrackerLogo
import com.s3arch.app.ui.components.scaleOnPress

/**
 * Premier demarrage : choix des trackers (logos), cles API, puis connexion Freebox.
 *
 * Le VPN n'est volontairement plus propose ici : il reste disponible dans les reglages
 * pour ceux qui en ont besoin.
 */
@Composable
fun SetupScreen(
    state: UiState,
    vm: S3archViewModel,
    snackbarHostState: SnackbarHostState,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var selectedTrackers by remember { mutableStateOf(state.settings.enabledTrackers) }
    var c411Key by rememberSaveable { mutableStateOf(state.settings.c411Key) }
    var c411Url by rememberSaveable { mutableStateOf(state.settings.c411Url) }
    var tr4kerKey by rememberSaveable { mutableStateOf(state.settings.tr4kerKey) }
    var tr4kerUrl by rememberSaveable { mutableStateOf(state.settings.tr4kerUrl) }
    var v3xKey by rememberSaveable { mutableStateOf(state.settings.v3xKey) }
    var v3xUrl by rememberSaveable { mutableStateOf(state.settings.v3xUrl) }
    var astraKey by rememberSaveable { mutableStateOf(state.settings.astraTorrentKey) }
    var astraUrl by rememberSaveable { mutableStateOf(state.settings.astraTorrentUrl) }
    var yggKey by rememberSaveable { mutableStateOf(state.settings.yggRebornKey) }
    var yggUrl by rememberSaveable { mutableStateOf(state.settings.yggRebornUrl) }
    var zxxKey by rememberSaveable { mutableStateOf(state.settings.zxxClubKey) }
    var zxxUrl by rememberSaveable { mutableStateOf(state.settings.zxxClubUrl) }
    var host by rememberSaveable { mutableStateOf(state.settings.freeboxHost) }
    var folder by rememberSaveable { mutableStateOf(state.settings.freeboxDownloadDir) }
    var showAddTracker by remember { mutableStateOf(false) }
    var selectedTargets by remember { mutableStateOf(state.settings.downloadTargets) }

    fun keyOf(tracker: Tracker): String = when (tracker) {
        Tracker.C411 -> c411Key
        Tracker.TR4KER -> tr4kerKey
        Tracker.V3X -> v3xKey
        Tracker.ASTRATORRENT -> astraKey
        Tracker.YGG_REBORN -> yggKey
        Tracker.ZXX_CLUB -> zxxKey
    }

    fun urlOf(tracker: Tracker): String = when (tracker) {
        Tracker.C411 -> c411Url
        Tracker.TR4KER -> tr4kerUrl
        Tracker.V3X -> v3xUrl
        Tracker.ASTRATORRENT -> astraUrl
        Tracker.YGG_REBORN -> yggUrl
        Tracker.ZXX_CLUB -> zxxUrl
    }

    val chosen = Tracker.entries.filter { it.id in selectedTrackers }
    val hasKey = chosen.any { keyOf(it).isNotBlank() } ||
        state.settings.customTrackers.any { it.key.isNotBlank() }
    val connected = state.setup.status == FreeboxStatus.Connected
    val busy = state.setup.status == FreeboxStatus.Connecting ||
        state.setup.status == FreeboxStatus.WaitingApproval
    val phoneSelected = TARGET_PHONE in selectedTargets
    // Un dossier choisi est obligatoire pour telecharger sur le telephone.
    val phoneFolderReady = !phoneSelected || state.settings.phoneTreeUri.isNotBlank()
    val freeboxReady = TARGET_FREEBOX !in selectedTargets || connected
    val progress by animateFloatAsState(
        targetValue = step / 3f,
        animationSpec = spring(),
        label = "setup-progress",
    )

    fun persistKeys() {
        vm.saveApiKeys(
            mapOf(
                Tracker.C411 to c411Key,
                Tracker.TR4KER to tr4kerKey,
                Tracker.V3X to v3xKey,
                Tracker.ASTRATORRENT to astraKey,
                Tracker.YGG_REBORN to yggKey,
                Tracker.ZXX_CLUB to zxxKey,
            ),
            mapOf(
                Tracker.C411 to c411Url,
                Tracker.TR4KER to tr4kerUrl,
                Tracker.V3X to v3xUrl,
                Tracker.ASTRATORRENT to astraUrl,
                Tracker.YGG_REBORN to yggUrl,
                Tracker.ZXX_CLUB to zxxUrl,
            ),
        )
        vm.setEnabledTrackers(selectedTrackers)
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
        // Le fond du bandeau couvre la barre d'etat : plus de zone vide en haut.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Fond opaque des le bord haut : le bandeau couvre la barre d'etat
                    // au lieu de laisser une bande vide au-dessus du logo.
                    .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 0.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoMark(56)
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text("S3ARCH", style = MaterialTheme.typography.displaySmall)
                        Text(
                            "Recherche · Téléphone · Freebox",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Defilement horizontal : les quatre etapes restent visibles sur les
                // petits ecrans, au lieu d'etre coupees.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StepPill(index = 1, label = "Destination", active = step == 0)
                    StepPill(index = 2, label = "Trackers", active = step == 1)
                    StepPill(index = 3, label = "Clés API", active = step == 2)
                    StepPill(
                        index = 4,
                        label = when {
                            phoneSelected && TARGET_FREEBOX in selectedTargets -> "Configuration"
                            phoneSelected -> "Téléphone"
                            else -> "Freebox"
                        },
                        active = step == 3,
                    )
                }
                Spacer(Modifier.height(14.dp))
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
                // Contenu colle en haut : jamais centre verticalement.
                contentAlignment = Alignment.TopStart,
                label = "setup-step",
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (currentStep) {
                        0 -> {
                            SectionCard(
                                title = "Où télécharger ?",
                                subtitle = "Une ou deux destinations. Modifiable à tout moment dans les réglages.",
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Une carte par ligne : les deux libelles restent lisibles.
                                    DestinationChoice(
                                        icon = Icons.Rounded.PhoneAndroid,
                                        title = "Téléphone",
                                        subtitle = "Directement sur cet appareil, sans Freebox",
                                        selected = TARGET_PHONE in selectedTargets,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        selectedTargets = toggleTarget(selectedTargets, TARGET_PHONE)
                                    }
                                    DestinationChoice(
                                        icon = Icons.Rounded.CloudDownload,
                                        title = "Freebox",
                                        subtitle = "La box télécharge et conserve les fichiers",
                                        selected = TARGET_FREEBOX in selectedTargets,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        selectedTargets = toggleTarget(selectedTargets, TARGET_FREEBOX)
                                    }
                                }
                            }
                            Text(
                                if (phoneSelected) {
                                    "Le téléphone ne demande aucune clé supplémentaire : les mêmes clés API " +
                                        "servent pour les deux destinations. Vous configurerez le dossier et les " +
                                        "autorisations à la dernière étape."
                                } else {
                                    "La Freebox télécharge à votre place : les fichiers restent sur son disque " +
                                        "et le téléphone peut fermer l'application."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        1 -> {
                            SectionCard(
                                title = "Vos trackers",
                                subtitle = "Choisissez ceux que S3ARCH doit interroger. Modifiable à tout moment.",
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Tracker.entries.chunked(2).forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            row.forEach { tracker ->
                                                TrackerChoice(
                                                    tracker = tracker,
                                                    selected = tracker.id in selectedTrackers,
                                                    onToggle = {
                                                        selectedTrackers = if (tracker.id in selectedTrackers) {
                                                            selectedTrackers - tracker.id
                                                        } else {
                                                            selectedTrackers + tracker.id
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            if (row.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            Text(
                                "Les clés API seront demandées à l'étape suivante pour les trackers " +
                                    "sélectionnés. Une seule clé suffit pour démarrer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        2 -> {
                            if (chosen.isEmpty()) {
                                Text(
                                    "Aucun tracker sélectionné : revenez à l'étape précédente.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                SectionCard(
                                    title = "Vos clés API",
                                    subtitle = "Les clés restent sur cet appareil.",
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                        chosen.forEach { tracker ->
                                            TrackerKeyEditor(
                                                tracker = tracker,
                                                keyValue = keyOf(tracker),
                                                urlValue = urlOf(tracker),
                                                onKeyChange = { value ->
                                                    when (tracker) {
                                                        Tracker.C411 -> c411Key = value
                                                        Tracker.TR4KER -> tr4kerKey = value
                                                        Tracker.V3X -> v3xKey = value
                                                        Tracker.ASTRATORRENT -> astraKey = value
                                                        Tracker.YGG_REBORN -> yggKey = value
                                                        Tracker.ZXX_CLUB -> zxxKey = value
                                                    }
                                                },
                                                onUrlChange = { value ->
                                                    when (tracker) {
                                                        Tracker.C411 -> c411Url = value
                                                        Tracker.TR4KER -> tr4kerUrl = value
                                                        Tracker.V3X -> v3xUrl = value
                                                        Tracker.ASTRATORRENT -> astraUrl = value
                                                        Tracker.YGG_REBORN -> yggUrl = value
                                                        Tracker.ZXX_CLUB -> zxxUrl = value
                                                    }
                                                },
                                                onTest = {
                                                    vm.testTracker(
                                                        TrackerSource(
                                                            tracker.id,
                                                            tracker.label,
                                                            urlOf(tracker),
                                                            keyOf(tracker),
                                                            tracker,
                                                        ),
                                                    )
                                                },
                                                testEnabled = !state.searching,
                                            )
                                        }

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

                                TextButton(onClick = { showAddTracker = true }) {
                                    Text(
                                        "Ajouter un tracker Torznab tiers",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    )
                                }

                                Text(
                                    "Une seule clé suffit pour démarrer. Vous pourrez compléter plus tard " +
                                        "dans les réglages.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        else -> {
                            if (phoneSelected) {
                                PhoneSetupCard(state = state, vm = vm, showTargets = false)
                                Spacer(Modifier.height(4.dp))
                            }
                            // Aucune etape Freebox si la box n'a pas ete choisie.
                            if (TARGET_FREEBOX in selectedTargets) {
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
                                        supportingText = { Text(FreeboxPaths.DEFAULT_DIR) },
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
                            Text(
                                "Le VPN hors du réseau local se configure ensuite dans Réglages → " +
                                    "Accès hors du réseau local.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            }
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
                    if (step > 0) {
                        TextButton(onClick = { step -= 1 }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Retour")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    when (step) {
                        0 -> Button(
                            onClick = {
                                vm.setDownloadTargets(selectedTargets)
                                step = 1
                            },
                            enabled = selectedTargets.isNotEmpty(),
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(50),
                        ) { Text("Continuer", style = MaterialTheme.typography.labelLarge) }

                        1 -> Button(
                            onClick = {
                                vm.setEnabledTrackers(selectedTrackers)
                                step = 2
                            },
                            enabled = selectedTrackers.isNotEmpty(),
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(50),
                        ) { Text("Continuer", style = MaterialTheme.typography.labelLarge) }

                        2 -> Button(
                            onClick = {
                                persistKeys()
                                step = 3
                            },
                            enabled = hasKey,
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(50),
                        ) { Text("Continuer", style = MaterialTheme.typography.labelLarge) }

                        else -> {
                            TextButton(
                                onClick = {
                                    vm.setDownloadTargets(selectedTargets)
                                    persistKeys()
                                    onFinish()
                                },
                            ) { Text("Plus tard") }
                            Spacer(Modifier.size(8.dp))
                            Button(
                                onClick = {
                                    vm.setDownloadTargets(selectedTargets)
                                    persistKeys()
                                    onFinish()
                                },
                                enabled = phoneFolderReady && freeboxReady,
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
}

/** Une destination proposée au premier lancement. */
@Composable
private fun DestinationChoice(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onToggle,
        modifier = modifier.border(
            if (selected) 2.dp else 1.dp,
            if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(26.dp),
        ),
        shape = RoundedCornerShape(26.dp),
        color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = accent)
            }
        }
    }
}

/** Au moins une destination doit rester active. */
private fun toggleTarget(current: Set<String>, target: String): Set<String> {
    val next = if (target in current) current - target else current + target
    return next.ifEmpty { setOf(target) }
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
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
