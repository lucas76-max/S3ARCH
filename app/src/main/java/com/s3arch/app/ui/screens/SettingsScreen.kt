package com.s3arch.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s3arch.app.BuildConfig
import com.s3arch.app.data.MediaType
import com.s3arch.app.data.Tracker
import com.s3arch.app.data.TrackerSource
import com.s3arch.app.data.UpdateRepository
import com.s3arch.app.ui.FreeboxStatus
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.AddTrackerDialog
import com.s3arch.app.ui.components.CustomTrackerRow
import com.s3arch.app.ui.components.FreeboxRightsCard
import com.s3arch.app.ui.components.LogoMark
import com.s3arch.app.ui.components.ScreenHeader
import com.s3arch.app.ui.components.SectionCard
import com.s3arch.app.ui.components.StatusDot
import com.s3arch.app.ui.components.TrackerKeyEditor
import com.s3arch.app.ui.components.VpnSettingsContent
import com.s3arch.app.ui.components.openFreeboxOs
import com.s3arch.app.ui.theme.AccentPalette
import com.s3arch.app.ui.theme.AccentPalettes
import com.s3arch.app.util.AppLog
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: UiState, vm: S3archViewModel) {
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
    var host by rememberSaveable { mutableStateOf(state.settings.freeboxHost) }
    var folder by rememberSaveable { mutableStateOf(state.settings.freeboxDownloadDir) }
    var showAddTracker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showAddTracker) {
        AddTrackerDialog(
            onDismiss = { showAddTracker = false },
            onConfirm = { label, url, key ->
                vm.addCustomTracker(label, url, key)
                showAddTracker = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "Réglages",
                subtitle = "Clés API, trackers tiers et Freebox",
                eyebrow = "S3ARCH",
                icon = Icons.Rounded.Settings,
            )
        }

        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Clés API",
                subtitle = "Demandées au premier lancement, modifiables à tout moment.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    TrackerKeyEditor(
                        tracker = Tracker.ASTRATORRENT,
                        keyValue = astraKey,
                        urlValue = astraUrl,
                        onKeyChange = { astraKey = it },
                        onUrlChange = { astraUrl = it },
                        onTest = {
                            vm.testTracker(
                                TrackerSource(
                                    Tracker.ASTRATORRENT.id,
                                    Tracker.ASTRATORRENT.label,
                                    astraUrl,
                                    astraKey,
                                    Tracker.ASTRATORRENT,
                                ),
                            )
                        },
                        testEnabled = !state.searching,
                    )
                    TrackerKeyEditor(
                        tracker = Tracker.YGG_REBORN,
                        keyValue = yggKey,
                        urlValue = yggUrl,
                        onKeyChange = { yggKey = it },
                        onUrlChange = { yggUrl = it },
                        onTest = {
                            vm.testTracker(
                                TrackerSource(
                                    Tracker.YGG_REBORN.id,
                                    Tracker.YGG_REBORN.label,
                                    yggUrl,
                                    yggKey,
                                    Tracker.YGG_REBORN,
                                ),
                            )
                        },
                        testEnabled = !state.searching,
                    )
                    Button(
                        onClick = {
                            vm.saveApiKeys(
                                mapOf(
                                    Tracker.C411 to c411Key,
                                    Tracker.TR4KER to tr4kerKey,
                                    Tracker.V3X to v3xKey,
                                    Tracker.ASTRATORRENT to astraKey,
                                    Tracker.YGG_REBORN to yggKey,
                                ),
                                mapOf(
                                    Tracker.C411 to c411Url,
                                    Tracker.TR4KER to tr4kerUrl,
                                    Tracker.V3X to v3xUrl,
                                    Tracker.ASTRATORRENT to astraUrl,
                                    Tracker.YGG_REBORN to yggUrl,
                                ),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("Enregistrer les clés", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Trackers tiers",
                subtitle = "Tout endpoint compatible Torznab. Compatibilité non garantie.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.settings.customTrackers.isEmpty()) {
                        Text(
                            "Aucun tracker tiers ajouté.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.settings.customTrackers.forEach { custom ->
                            CustomTrackerRow(
                                tracker = custom,
                                onTest = {
                                    vm.testTracker(
                                        TrackerSource(custom.id, custom.label, custom.url, custom.key),
                                    )
                                },
                                onRemove = { vm.removeCustomTracker(custom.id) },
                            )
                        }
                    }
                    TextButton(onClick = { showAddTracker = true }) {
                        Text(
                            "Ajouter un tracker Torznab tiers",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Types de contenus",
                subtitle = "Filtrage facultatif : seuls les types cochés apparaissent dans la recherche.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MediaType.entries.forEach { type ->
                            FilterChip(
                                selected = type.id in state.settings.enabledTypes,
                                onClick = { vm.toggleType(type) },
                                label = { Text(type.label) },
                            )
                        }
                    }
                    Text(
                        if (state.settings.enabledTypes.isEmpty()) {
                            "Aucun filtre : tous les types sont affichés."
                        } else {
                            "Filtre actif : ${state.settings.enabledTypes.size} type(s) affiché(s)."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.settings.enabledTypes.isNotEmpty()) {
                        TextButton(onClick = { vm.clearTypeFilter() }) {
                            Text("Désactiver le filtre")
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Freebox",
                subtitle = "API officielle Freebox OS, dossier de destination et session.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Adresse de la Freebox") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text("Dossier de téléchargement") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val color = when (state.setup.status) {
                            FreeboxStatus.Connected -> MaterialTheme.colorScheme.primary
                            FreeboxStatus.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        StatusDot(color = color)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = when {
                                state.setup.detail.isNotBlank() -> state.setup.detail
                                state.settings.freeboxConfigured -> "Freebox autorisée (session à rouvrir si besoin)"
                                else -> "Freebox non connectée"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.setup.status == FreeboxStatus.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { vm.reconnectFreebox() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                        ) { Text("Session") }
                        Button(
                            onClick = {
                                vm.saveDownloadFolder(folder)
                                vm.connectFreebox(host, folder)
                            },
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(50),
                        ) { Text("Réautoriser") }
                    }
                    Text(
                        "La réautorisation demande d'appuyer sur la flèche → de la Freebox pour confirmer S3ARCH.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.needsSettingsRight) {
            item {
                FreeboxRightsCard(
                    state = state,
                    onOpenBox = { openFreeboxOs(context, state.settings.freeboxHost) },
                    onRetry = { vm.retryAfterFreeboxRights(context) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Accès hors du réseau local",
                subtitle = "VPN WireGuard créé automatiquement sur votre Freebox.",
            ) {
                VpnSettingsContent(
                    state = state,
                    onEnable = { vm.enableVpn(context) },
                    onDisable = { vm.disableVpn(context) },
                )
            }
        }

        item {
            val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val dynamicActive = dynamicSupported && state.settings.dynamicColor

            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Apparence",
                subtitle = if (dynamicActive) {
                    "Les couleurs suivent votre fond d'écran."
                } else {
                    "Choisissez la couleur d'accent de l'application."
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Toute la ligne réagit au clic : un appui sur le libellé ou l'icône
                    // bascule le mode, au lieu de viser l'interrupteur seul.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(enabled = dynamicSupported) {
                                vm.setDynamicColor(!state.settings.dynamicColor)
                            }
                            .padding(vertical = 4.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Couleurs dynamiques", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    !dynamicSupported -> "Nécessite Android 12 ou plus"
                                    state.settings.dynamicColor -> "Palette Material You de l'appareil"
                                    else -> "Palette d'accent personnalisée"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = dynamicActive,
                            onCheckedChange = { vm.setDynamicColor(it) },
                            enabled = dynamicSupported,
                        )
                    }

                    if (!dynamicActive) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Couleur d'accent",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AccentPalettes.forEach { palette ->
                                    AccentSwatch(
                                        palette = palette,
                                        selected = state.settings.accentColor == palette.id,
                                        onClick = { vm.setAccentColor(palette.id) },
                                    )
                                }
                            }
                            Text(
                                "La palette s'applique immédiatement à toute l'application.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            val logs by AppLog.entries.collectAsState()
            val clipboard = LocalClipboardManager.current
            val logScroll = rememberScrollState()
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty()) logScroll.animateScrollTo(logScroll.maxValue)
            }

            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Journaux",
                subtitle = "Dernières opérations trackers et Freebox. Clés API, passkeys et jetons sont masqués.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 280.dp)
                                .verticalScroll(logScroll)
                                .padding(12.dp),
                        ) {
                            if (logs.isEmpty()) {
                                Text(
                                    "Aucun evenement pour l'instant. Lancez une recherche ou un telechargement.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = logs.takeLast(200).joinToString("\n") { it.format() },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    Text(
                        "Journal de la session en cours (${logs.size}/500 entrees). " +
                            "Copiez-le pour diagnostiquer un refus de la Freebox ou d'un tracker.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(AppLog.asText())) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            enabled = logs.isNotEmpty(),
                        ) { Text("Copier") }
                        OutlinedButton(
                            onClick = { AppLog.clear() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            enabled = logs.isNotEmpty(),
                        ) { Text("Effacer") }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(30.dp),
                    ),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "S3ARCH",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LogoMark(104)

                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Application utilisant les APIs officielles ou non officielles de " +
                                "différents trackers, ainsi que l'API Freebox OS pour lancer " +
                                "directement les téléchargements.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }

                    OutlinedButton(
                        onClick = { openPage(context, UpdateRepository.GITHUB_URL) },
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "github.com/${UpdateRepository.REPO}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    Text(
                        "Cette application est un outil de téléchargement de fichiers pair-à-pair. " +
                            "Elle n'incite ni ne cautionne le piratage. Vous êtes seul responsable de " +
                            "l'usage que vous en faites et du contenu que vous téléchargez ou partagez. " +
                            "Il vous appartient de vérifier que vous détenez les droits nécessaires sur " +
                            "les œuvres concernées et que votre utilisation respecte la législation en " +
                            "vigueur dans votre pays.",
                        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Petit nuancier rond : un appui applique la palette. */
@Composable
private fun AccentSwatch(
    palette: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = palette.lightPrimary,
            border = if (selected) {
                BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Palette sélectionnée",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Text(
            palette.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ouvre un lien externe sans faire planter l'application si aucun navigateur n'existe. */
private fun openPage(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { AppLog.warn("UI", "Ouverture du lien impossible : ${it.message}") }
}
