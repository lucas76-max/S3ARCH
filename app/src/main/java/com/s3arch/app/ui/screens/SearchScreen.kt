package com.s3arch.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LeakAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s3arch.app.data.MediaType
import com.s3arch.app.data.SortMode
import com.s3arch.app.data.TorrentResult
import com.s3arch.app.data.TrackerSource
import com.s3arch.app.data.sortedFor
import com.s3arch.app.data.withoutZeroSeeders
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.EmptyState
import com.s3arch.app.ui.components.Pill
import com.s3arch.app.ui.components.ScreenHeader
import com.s3arch.app.ui.components.scaleOnPress
import com.s3arch.app.util.formatBytes

@Composable
fun SearchScreen(state: UiState, vm: S3archViewModel) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val sources = state.settings.sources()

    // Filtre facultatif par type de contenu (ensemble vide = tout afficher), puis tri choisi.
    val activeTypes = state.settings.enabledTypes
    val filtered = if (activeTypes.isEmpty()) {
        state.results
    } else {
        state.results.filter { it.mediaType.id in activeTypes }
    }
    val withoutZero = remember(filtered, state.settings.hideZeroSeeders) {
        filtered.withoutZeroSeeders(state.settings.hideZeroSeeders)
    }
    val visible = remember(withoutZero, state.settings.sortMode) {
        withoutZero.sortedFor(state.settings.sortMode)
    }
    val hiddenByFilter = state.results.size - filtered.size
    val hiddenBySeeders = filtered.size - withoutZero.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScreenHeader(
            title = "S3ARCH",
            subtitle = "Recherche multi-trackers",
            eyebrow = "Torrents · Freebox · VLC",
            icon = Icons.Rounded.TravelExplore,
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.setQuery(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 62.dp),
                placeholder = { Text("Film, série, album, logiciel…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            vm.setQuery("")
                        }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                sources.forEach { source ->
                    val configured = source.isConfigured
                    val selected = source.id in state.settings.enabledTrackers
                    SourceChip(
                        label = source.label,
                        selected = selected && configured,
                        configured = configured,
                        onClick = {
                            if (configured) {
                                vm.toggleTracker(source)
                            } else {
                                vm.message("Ajoutez la clé ${source.label} dans les réglages")
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Surface(
                        onClick = { sortMenuOpen = true },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.SwapVert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                state.settings.sortMode.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                Icons.Rounded.ArrowDropDown,
                                contentDescription = "Changer le tri",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    vm.setSortMode(mode)
                                    sortMenuOpen = false
                                },
                                leadingIcon = {
                                    if (mode == state.settings.sortMode) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    } else {
                                        Spacer(Modifier.size(18.dp))
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.results.isNotEmpty()) {
                    Text(
                        "${visible.size} résultats",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (hiddenByFilter > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$hiddenByFilter masqué(s) par le filtre de types (Réglages → Types de contenus)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hiddenBySeeders > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$hiddenBySeeders torrent(s) à 0 seeder masqué(s) (Réglages → Résultats)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }

        AnimatedVisibility(visible = state.trackerErrors.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                state.trackerErrors.forEach { (tracker, error) ->
                    Text(
                        "$tracker : $error",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (visible.isEmpty() && !state.searching) {
            if (state.searched) {
                if (hiddenByFilter > 0 || hiddenBySeeders > 0) {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "Aucun résultat affichable",
                        subtitle = buildList {
                            if (hiddenByFilter > 0) add("$hiddenByFilter masqué(s) par le filtre de types")
                            if (hiddenBySeeders > 0) add("$hiddenBySeeders sans seeder")
                        }.joinToString(" · ") + ". Ajustez les filtres dans les réglages.",
                    )
                } else {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "Aucun résultat",
                        subtitle = "Essayez un autre terme ou vérifiez vos clés API dans les réglages.",
                    )
                }
            } else {
                EmptyState(
                    icon = Icons.Rounded.TravelExplore,
                    title = "Cherchez sur tous vos trackers",
                    subtitle = "Les résultats sont fusionnés et classés par taille croissante, du plus léger au plus lourd.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = visible,
                    key = { "${it.trackerId}-${it.id}" },
                ) { result ->
                    ResultCard(
                        result = result,
                        pending = "${result.trackerId}:${result.id}" in state.pendingDownloads,
                        onDownload = { vm.download(result) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceChip(
    label: String,
    selected: Boolean,
    configured: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val container = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        configured -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = container,
        interactionSource = interaction,
        modifier = Modifier.scaleOnPress(interaction),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ResultCard(result: TorrentResult, pending: Boolean, onDownload: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(28.dp),
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    Pill(text = result.trackerLabel, tint = MaterialTheme.colorScheme.secondary)
                    if (result.mediaType != MediaType.OTHER) {
                        Pill(
                            text = result.mediaType.label,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Pill(
                        text = formatBytes(result.sizeBytes),
                        tint = MaterialTheme.colorScheme.primary,
                        icon = Icons.Rounded.Download,
                    )
                    Pill(
                        text = "${result.seeders}",
                        tint = MaterialTheme.colorScheme.tertiary,
                        icon = Icons.Rounded.Upload,
                    )
                    Pill(
                        text = "${result.leechers}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = Icons.Rounded.LeakAdd,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Box(contentAlignment = Alignment.Center) {
                if (pending) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                } else {
                    Surface(
                        onClick = onDownload,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        interactionSource = interaction,
                        modifier = Modifier
                            .size(52.dp)
                            .scaleOnPress(interaction),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "Télécharger sur la Freebox",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
