package com.s3arch.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LeakAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s3arch.app.data.TorrentResult
import com.s3arch.app.data.Tracker
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.EmptyState
import com.s3arch.app.ui.components.Pill
import com.s3arch.app.ui.components.ScreenHeader
import com.s3arch.app.util.formatBytes

@Composable
fun SearchScreen(state: UiState, vm: S3archViewModel) {
    var query by rememberSaveable { mutableStateOf(state.query) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScreenHeader(
            title = "S3ARCH",
            subtitle = "Recherche multi-trackers",
            icon = Icons.Rounded.TravelExplore,
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.setQuery(it)
                },
                modifier = Modifier.fillMaxWidth(),
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
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                Tracker.entries.forEach { tracker ->
                    val configured = state.settings.isConfigured(tracker)
                    val selected = tracker.id in state.settings.enabledTrackers
                    FilterChip(
                        selected = selected && configured,
                        onClick = {
                            if (configured) {
                                vm.toggleTracker(tracker)
                            } else {
                                vm.message("Ajoutez la clé ${tracker.label} dans les réglages")
                            }
                        },
                        label = { Text(tracker.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        enabled = true,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    "Triés du plus petit au plus grand",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (state.results.isNotEmpty()) {
                    Text(
                        "${state.results.size} résultats",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (state.searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
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

        if (state.results.isEmpty() && !state.searching) {
            if (state.searched) {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "Aucun résultat",
                    subtitle = "Essayez un autre terme ou vérifiez vos clés API dans les réglages.",
                )
            } else {
                EmptyState(
                    icon = Icons.Rounded.TravelExplore,
                    title = "Cherchez sur les trois trackers",
                    subtitle = "Les résultats de C411, TR4KER et V3X sont fusionnés et classés par taille croissante.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = state.results,
                    key = { "${it.tracker.id}-${it.id}" },
                ) { result ->
                    ResultCard(
                        result = result,
                        pending = "${result.tracker.id}:${result.id}" in state.pendingDownloads,
                        onDownload = { vm.download(result) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: TorrentResult, pending: Boolean, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
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
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    Pill(text = result.tracker.label, tint = MaterialTheme.colorScheme.secondary)
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
            Spacer(Modifier.size(8.dp))
            Box(contentAlignment = Alignment.Center) {
                if (pending) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Télécharger sur la Freebox",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
