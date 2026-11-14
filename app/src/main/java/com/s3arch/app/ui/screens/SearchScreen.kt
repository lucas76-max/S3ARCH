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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LeakAdd
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.s3arch.app.data.MediaType
import com.s3arch.app.data.SortMode
import com.s3arch.app.data.TmdbInfo
import com.s3arch.app.data.TorrentResult
import com.s3arch.app.data.TrackerSource
import com.s3arch.app.data.filteredBy
import com.s3arch.app.data.parsePublishedAt
import com.s3arch.app.data.sortedFor
import com.s3arch.app.data.withoutZeroSeeders
import com.s3arch.app.ui.S3archViewModel
import com.s3arch.app.ui.UiState
import com.s3arch.app.ui.components.EmptyState
import com.s3arch.app.ui.components.Pill
import com.s3arch.app.ui.components.ScreenHeader
import com.s3arch.app.ui.components.SearchFiltersDialog
import com.s3arch.app.ui.components.scaleOnPress
import com.s3arch.app.util.formatBytes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchScreen(state: UiState, vm: S3archViewModel) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<TorrentResult?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
    val matched = remember(withoutZero, state.settings.filters) {
        withoutZero.filteredBy(state.settings.filters)
    }
    val visible = remember(matched, state.settings.sortMode) {
        matched.sortedFor(state.settings.sortMode)
    }
    val hiddenByFilter = state.results.size - filtered.size
    val hiddenBySeeders = filtered.size - withoutZero.size
    val hiddenByAdvanced = withoutZero.size - matched.size

    // Le bandeau et les controles defilent avec la liste : en scroll, seule une barre
    // compacte reste, pour laisser toute la place aux resultats.
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 60
        }
    }

    // Affiches TMDB : 20 resultats a la fois, au fil du defilement.
    LaunchedEffect(state.settings.tmdbKey, visible.size, listState.firstVisibleItemIndex) {
        if (state.settings.tmdbKey.isNotBlank() && visible.isNotEmpty()) {
            val upTo = (listState.firstVisibleItemIndex + 6).coerceIn(1, visible.size)
            vm.requestTmdb(visible.take(upTo))
        }
    }

    if (filtersOpen) {
        SearchFiltersDialog(
            filters = state.settings.filters,
            onDismiss = { filtersOpen = false },
            onApply = { filters ->
                vm.saveFilters(filters)
                filtersOpen = false
            },
        )
    }

    detail?.let { result ->
        ResultDetailsDialog(
            result = result,
            info = state.tmdb["${result.trackerId}:${result.id}"],
            pending = "${result.trackerId}:${result.id}" in state.pendingDownloads,
            onDownload = {
                vm.download(result)
                detail = null
            },
            onDismiss = { detail = null },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = "S3ARCH",
                    subtitle = "Recherche multi-trackers",
                    icon = Icons.Rounded.TravelExplore,
                )
            }

            item(key = "controls") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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

                    AnimatedVisibility(visible = state.searching) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { state.searchProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Recherche… ${(state.searchProgress * state.searchTotal + 0.5f).toInt()}" +
                                    " / ${state.searchTotal} tracker(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

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
                        Spacer(Modifier.size(8.dp))
                        // Filtres avances : langue, qualite, audio, taille, annee, sources.
                        val filtersActive = state.settings.filters.isActive
                        Surface(
                            onClick = { filtersOpen = true },
                            shape = RoundedCornerShape(50),
                            color = if (filtersActive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.FilterList,
                                    contentDescription = null,
                                    tint = if (filtersActive) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    if (filtersActive) "Filtres actifs" else "Filtres",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (filtersActive) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
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
                            "$hiddenByFilter masqué(s) par le filtre de types (Réglages → Général)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (hiddenBySeeders > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "$hiddenBySeeders torrent(s) à 0 seeder masqué(s) (Réglages → Général)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (hiddenByAdvanced > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "$hiddenByAdvanced masqué(s) par les filtres avancés",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.searching) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
                    }
                    if (state.trackerErrors.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        state.trackerErrors.forEach { (tracker, error) ->
                            Text(
                                "$tracker : $error",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (visible.isEmpty() && !state.searching) {
                item(key = "empty") {
                    if (state.searched) {
                        if (hiddenByFilter > 0 || hiddenBySeeders > 0 || hiddenByAdvanced > 0) {
                            EmptyState(
                                icon = Icons.Rounded.Search,
                                title = "Aucun résultat affichable",
                                subtitle = buildList {
                                    if (hiddenByFilter > 0) add("$hiddenByFilter masqué(s) par le filtre de types")
                                    if (hiddenBySeeders > 0) add("$hiddenBySeeders sans seeder")
                                    if (hiddenByAdvanced > 0) add("$hiddenByAdvanced hors filtres avancés")
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
                }
            } else {
                items(
                    items = visible,
                    key = { "${it.trackerId}-${it.id}" },
                ) { result ->
                    val key = "${result.trackerId}:${result.id}"
                    ResultCard(
                        result = result,
                        info = state.tmdb[key],
                        pending = key in state.pendingDownloads,
                        onOpen = { detail = result },
                        onDownload = { vm.download(result) },
                    )
                }
            }
        }

        // Barre compacte affichee des que l'en-tete est parti : un appui remonte en haut.
        AnimatedVisibility(
            visible = collapsed,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = state.query.ifBlank { "Recherche" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${visible.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(
                        onClick = { filtersOpen = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = "Filtres",
                            tint = if (state.settings.filters.isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SwapVert,
                            contentDescription = "Remonter à la recherche",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

/** Vignette TMDB, ou remplacement neutre quand le film n'est pas reconnu. */
@Composable
private fun PosterThumb(info: TmdbInfo?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val url = info?.posterUrl
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = info.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ResultCard(
    result: TorrentResult,
    info: TmdbInfo?,
    pending: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(28.dp),
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterThumb(
                info = info,
                modifier = Modifier
                    .width(58.dp)
                    .aspectRatio(2f / 3f),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info?.title?.takeIf { it.isNotBlank() } ?: result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (info != null && info.title.isNotBlank() && info.title != result.title) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.size(8.dp))
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
                                contentDescription = "Télécharger",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Fiche complete : affiche en grand, resume, puis informations du torrent. */
@Composable
private fun ResultDetailsDialog(
    result: TorrentResult,
    info: TmdbInfo?,
    pending: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            val url = info?.largePosterUrl ?: info?.posterUrl
                            if (url != null) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = info?.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(72.dp),
                                )
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                info?.title?.takeIf { it.isNotBlank() } ?: result.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (info != null && info.year.isNotBlank()) {
                                    Pill(text = info.year, tint = MaterialTheme.colorScheme.primary)
                                }
                                if (info != null && info.voteAverage > 0.0) {
                                    Pill(
                                        text = "%.1f/10".format(Locale.FRANCE, info.voteAverage),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                Pill(
                                    text = result.mediaType.label,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            if (info != null && info.overview.isNotBlank()) {
                                Text(
                                    info.overview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                Text(
                                    if (info == null) {
                                        "Aucune fiche TMDB trouvée pour ce titre. Ajoutez une clé " +
                                            "TMDB dans les réglages pour voir les affiches et les résumés."
                                    } else {
                                        "Pas de résumé disponible pour ce titre."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Torrent", style = MaterialTheme.typography.titleMedium)
                            Text(
                                result.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                Pill(result.trackerLabel, tint = MaterialTheme.colorScheme.secondary)
                                Pill(
                                    formatBytes(result.sizeBytes),
                                    tint = MaterialTheme.colorScheme.primary,
                                    icon = Icons.Rounded.Download,
                                )
                                Pill(
                                    "${result.seeders} sources",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    icon = Icons.Rounded.Upload,
                                )
                                Pill(
                                    "${result.leechers} pairs",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    icon = Icons.Rounded.LeakAdd,
                                )
                            }
                            val published = parsePublishedAt(result.publishDate)
                            if (published > 0L) {
                                val format = remember { SimpleDateFormat("d MMMM yyyy", Locale.FRANCE) }
                                Text(
                                    "Publié le ${format.format(Date(published))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            result.category?.takeIf { it.isNotBlank() }?.let { category ->
                                Text(
                                    "Catégorie : $category",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Fermer") }
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Button(
                        onClick = onDownload,
                        enabled = !pending,
                        shape = RoundedCornerShape(50),
                    ) {
                        if (pending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(8.dp))
                        } else {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text("Télécharger")
                    }
                }
            }
        }
    }
}
