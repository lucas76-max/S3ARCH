package com.s3arch.app.ui.screens

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.s3arch.app.data.MediaType
import com.s3arch.app.data.Tracker
import com.s3arch.app.data.TrackerSource
import com.s3arch.app.ui.FreeboxStatus
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.AddTrackerDialog
import com.s3arch.app.ui.components.CustomTrackerRow
import com.s3arch.app.ui.components.ScreenHeader
import com.s3arch.app.ui.components.SectionCard
import com.s3arch.app.ui.components.StatusDot
import com.s3arch.app.ui.components.TrackerKeyEditor
import com.s3arch.app.util.AppLog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: UiState, vm: S3archViewModel) {
    var c411Key by rememberSaveable { mutableStateOf(state.settings.c411Key) }
    var c411Url by rememberSaveable { mutableStateOf(state.settings.c411Url) }
    var tr4kerKey by rememberSaveable { mutableStateOf(state.settings.tr4kerKey) }
    var tr4kerUrl by rememberSaveable { mutableStateOf(state.settings.tr4kerUrl) }
    var v3xKey by rememberSaveable { mutableStateOf(state.settings.v3xKey) }
    var v3xUrl by rememberSaveable { mutableStateOf(state.settings.v3xUrl) }
    var host by rememberSaveable { mutableStateOf(state.settings.freeboxHost) }
    var folder by rememberSaveable { mutableStateOf(state.settings.freeboxDownloadDir) }
    var showAddTracker by remember { mutableStateOf(false) }

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
                    Button(
                        onClick = {
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

        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Apparence",
                subtitle = "Material You : les couleurs suivent votre fond d'écran.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Couleurs dynamiques", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                "Palette Material You de l'appareil"
                            } else {
                                "Nécessite Android 12 ou plus"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        onCheckedChange = { vm.setDynamicColor(it) },
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
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
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "À propos",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("S3ARCH 1.4.0 · Material 3 Expressive", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Les clés API restent sur cet appareil. VLC est requis pour la lecture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
