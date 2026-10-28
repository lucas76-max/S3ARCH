package com.s3arch.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.s3arch.app.data.KeyImport

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
import com.s3arch.app.ui.components.PhoneSetupCard
import com.s3arch.app.ui.components.ScreenHeader
import com.s3arch.app.ui.components.SectionCard
import com.s3arch.app.ui.components.StatusDot
import com.s3arch.app.ui.components.TrackerChoice
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
    var zxxKey by rememberSaveable { mutableStateOf(state.settings.zxxClubKey) }
    var zxxUrl by rememberSaveable { mutableStateOf(state.settings.zxxClubUrl) }
    var host by rememberSaveable { mutableStateOf(state.settings.freeboxHost) }
    var folder by rememberSaveable { mutableStateOf(state.settings.freeboxDownloadDir) }
    var showAddTracker by remember { mutableStateOf(false) }
    val context = LocalContext.current

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

    fun setKey(tracker: Tracker, value: String) {
        when (tracker) {
            Tracker.C411 -> c411Key = value
            Tracker.TR4KER -> tr4kerKey = value
            Tracker.V3X -> v3xKey = value
            Tracker.ASTRATORRENT -> astraKey = value
            Tracker.YGG_REBORN -> yggKey = value
            Tracker.ZXX_CLUB -> zxxKey = value
        }
    }

    fun setUrl(tracker: Tracker, value: String) {
        when (tracker) {
            Tracker.C411 -> c411Url = value
            Tracker.TR4KER -> tr4kerUrl = value
            Tracker.V3X -> v3xUrl = value
            Tracker.ASTRATORRENT -> astraUrl = value
            Tracker.YGG_REBORN -> yggUrl = value
            Tracker.ZXX_CLUB -> zxxUrl = value
        }
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

    var settingsQuery by rememberSaveable { mutableStateOf("") }

    /**
     * Un reglage reste affiche si le filtre est vide, s'il est contenu dans ses libelles,
     * ou si tous les mots saisis y figurent (recherche « couleur accent » par exemple).
     */
    fun show(haystack: String): Boolean {
        val needle = settingsQuery.trim().lowercase()
        if (needle.isEmpty()) return true
        if (haystack.contains(needle)) return true
        val words = needle.split(' ').filter { it.length >= 2 }
        return words.isNotEmpty() && words.all { haystack.contains(it) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OutlinedTextField(
                value = settingsQuery,
                onValueChange = { settingsQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Rechercher un réglage…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (settingsQuery.isNotEmpty()) {
                        IconButton(onClick = { settingsQuery = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }

        if (show("réglages reglages parametres preferences")) {
        item {
            ScreenHeader(
                title = "Réglages",
                subtitle = "Clés API, trackers tiers et Freebox",
                eyebrow = "S3ARCH",
                icon = Icons.Rounded.Settings,
            )
        }
        }

        // Telechargement direct sur l'appareil : dossier, batterie, notifications.
        if (show("téléchargement téléphone telephone dossier stockage espace batterie notifications destination optimisation")) {
            item {
                PhoneSetupCard(
                    state = state,
                    vm = vm,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (show("trackers trackers cles api logos c411 tr4ker v3x astratorrent ygg zxx club")) {
        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Trackers",
                subtitle = "Cochez ceux que S3ARCH doit interroger ; leurs clés apparaissent en dessous.",
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
                                    selected = tracker.id in state.settings.enabledTrackers,
                                    onToggle = { vm.toggleEnabledTracker(tracker.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        if (state.settings.enabledTrackers.isEmpty()) {
                            "Aucun tracker actif : la recherche ne renverra aucun résultat."
                        } else {
                            "${state.settings.enabledTrackers.size} tracker(s) actif(s) sur ${Tracker.entries.size}."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }

        if (state.settings.enabledTrackers.isNotEmpty()) {
            if (show("clés api cles api c411 tr4ker v3x astratorrent ygg zxx club")) {
            item {
                SectionCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = "Clés API",
                    subtitle = "Uniquement pour les trackers actifs. Les clés restent sur cet appareil.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Tracker.entries
                            .filter { it.id in state.settings.enabledTrackers }
                            .forEach { tracker ->
                                TrackerKeyEditor(
                                    tracker = tracker,
                                    keyValue = keyOf(tracker),
                                    urlValue = urlOf(tracker),
                                    onKeyChange = { value -> setKey(tracker, value) },
                                    onUrlChange = { value -> setUrl(tracker, value) },
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
                        Button(
                            onClick = {
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
            }
        }

        if (show("importer des clés importer import fichier texte cles api txt")) {
        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Importer des clés",
                subtitle = "Un fichier texte évite de ressaisir chaque clé API.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Format attendu : une ligne par tracker, « NOM = clé ». Noms reconnus : " +
                            "C411, TR4KER, V3X, ASTRATORRENT, YGG_REBORN, ZXX_CLUB. " +
                            "Le séparateur « : » fonctionne aussi et les lignes commençant par " +
                            "« # » sont ignorées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "V3X = ma-cle\nC411 = ma-cle\nYGG_REBORN = ma-passkey",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    val importer = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri != null) {
                            val text = runCatching {
                                context.contentResolver.openInputStream(uri)
                                    ?.bufferedReader()
                                    ?.use { it.readText() }
                            }.getOrNull()
                            val parsed = runCatching { KeyImport.parse(text.orEmpty()) }
                                .getOrDefault(emptyList())
                            if (parsed.isEmpty()) {
                                vm.message("Aucune clé reconnue dans ce fichier")
                            } else {
                                parsed.forEach { entry -> setKey(entry.tracker, entry.key) }
                                vm.saveApiKeys(
                                    Tracker.entries.associateWith { keyOf(it) },
                                    Tracker.entries.associateWith { urlOf(it) },
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { importer.launch(arrayOf("text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(
                            Icons.Rounded.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Choisir un fichier .txt")
                    }
                }
            }
        }
        }

        if (show("trackers tiers tracker tiers torznab personnalise endpoint")) {
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
        }

        if (show("types de contenus types contenus films series musique ebooks applications jeux xxx autres filtre")) {
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
        }

        if (show("résultats resultats seeder torrents filtre masquer")) {
        item {
            SectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "Résultats",
                subtitle = "Filtres appliqués à la liste de recherche.",
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { vm.setHideZeroSeeders(!state.settings.hideZeroSeeders) }
                        .padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Masquer les torrents à 0 seeder", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.settings.hideZeroSeeders) {
                                "Les résultats sans source sont masqués ; le nombre caché est indiqué sous la barre de recherche."
                            } else {
                                "Tous les résultats sont affichés, même sans source."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Switch(
                        checked = state.settings.hideZeroSeeders,
                        onCheckedChange = { vm.setHideZeroSeeders(it) },
                    )
                }
            }
        }
        }

        if (show("freebox freebox adresse dossier telechargement session autoriser connecter")) {
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
        }

        if (state.needsSettingsRight) {
            if (show("droits freebox autorisation settings parametres")) {
            item {
                FreeboxRightsCard(
                    state = state,
                    onOpenBox = { openFreeboxOs(context, state.settings.freeboxHost) },
                    onRetry = { vm.retryAfterFreeboxRights(context) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            }
        }

        if (show("accès hors du réseau local vpn wireguard hors reseau local distance tunnel")) {
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
        }

        if (show("apparence apparence couleurs dynamiques material you accent palette theme")) {
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
        }

        if (show("journaux journaux logs journal copier effacer diagnostic")) {
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
        }

        if (show("a propos about version github logo application avertissement")) {
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
