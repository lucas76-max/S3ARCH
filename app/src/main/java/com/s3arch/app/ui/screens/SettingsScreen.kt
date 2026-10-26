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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.s3arch.app.data.Tracker
import com.s3arch.app.ui.FreeboxStatus
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.SectionCard
import com.s3arch.app.ui.components.ScreenHeader
import com.s3arch.app.ui.components.StatusDot
import com.s3arch.app.ui.components.TrackerKeyEditor

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "Réglages",
                subtitle = "Clés API et connexion Freebox",
                icon = Icons.Rounded.Settings,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Clés API des trackers",
                subtitle = "Demandées au premier lancement, modifiables à tout moment.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Enregistrer les clés")
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text("Dossier de téléchargement") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val connected = state.setup.status == FreeboxStatus.Connected
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
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("Session") }
                        Button(
                            onClick = {
                                vm.saveDownloadFolder(folder)
                                vm.connectFreebox(host, folder)
                            },
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(14.dp),
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
                title = "À propos",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("S3ARCH 1.0.0 · Material 3", style = MaterialTheme.typography.bodyMedium)
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
                            "Les clés API sont stockées uniquement sur cet appareil. VLC est requis pour la lecture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
