package com.s3arch.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.s3arch.app.data.AudioCodec
import com.s3arch.app.data.ReleaseLanguage
import com.s3arch.app.data.SearchFilters
import com.s3arch.app.data.VideoQuality

private const val GO = 1024.0 * 1024.0 * 1024.0

/**
 * Filtres avances, partages entre la recherche et les reglages.
 *
 * Les choix d'un meme groupe se combinent en « ou » (1080p **ou** 720p), les groupes
 * entre eux en « et » (langue **et** qualite). Aucun choix = pas de filtre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchFiltersDialog(
    filters: SearchFilters,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    var languages by remember(filters) { mutableStateOf(filters.languages) }
    var qualities by remember(filters) { mutableStateOf(filters.videoQualities) }
    var audio by remember(filters) { mutableStateOf(filters.audioCodecs) }
    var minSize by remember(filters) {
        mutableStateOf(if (filters.minSizeBytes > 0) formatGo(filters.minSizeBytes) else "")
    }
    var maxSize by remember(filters) {
        mutableStateOf(if (filters.maxSizeBytes > 0) formatGo(filters.maxSizeBytes) else "")
    }
    var yearMin by remember(filters) {
        mutableStateOf(if (filters.yearMin > 0) filters.yearMin.toString() else "")
    }
    var yearMax by remember(filters) {
        mutableStateOf(if (filters.yearMax > 0) filters.yearMax.toString() else "")
    }
    var minSeeders by remember(filters) {
        mutableStateOf(if (filters.minSeeders > 0) filters.minSeeders.toString() else "")
    }

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
                Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp)) {
                    Text("Filtres avancés", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Appliqués aux noms de fichiers des torrents. Les choix d'une même " +
                            "catégorie se cumulent en « ou ».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 22.dp,
                        end = 22.dp,
                        top = 16.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        FilterGroup(
                            title = "Langue",
                            subtitle = "Version audio ou sous-titrage annoncé dans le nom.",
                        ) {
                            ReleaseLanguage.entries.forEach { language ->
                                FilterChip(
                                    selected = language.id in languages,
                                    onClick = {
                                        languages = if (language.id in languages) {
                                            languages - language.id
                                        } else {
                                            languages + language.id
                                        }
                                    },
                                    label = {
                                        Column {
                                            Text(language.label, style = MaterialTheme.typography.labelLarge)
                                            Text(
                                                language.meaning,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

                    item {
                        FilterGroup(
                            title = "Qualité vidéo",
                            subtitle = "Définition, source et encodage.",
                        ) {
                            VideoQuality.entries.forEach { quality ->
                                FilterChip(
                                    selected = quality.id in qualities,
                                    onClick = {
                                        qualities = if (quality.id in qualities) {
                                            qualities - quality.id
                                        } else {
                                            qualities + quality.id
                                        }
                                    },
                                    label = {
                                        Column {
                                            Text(quality.label, style = MaterialTheme.typography.labelLarge)
                                            Text(
                                                quality.meaning,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

                    item {
                        FilterGroup(
                            title = "Audio",
                            subtitle = "Codec et nombre de canaux.",
                        ) {
                            AudioCodec.entries.forEach { codec ->
                                FilterChip(
                                    selected = codec.id in audio,
                                    onClick = {
                                        audio = if (codec.id in audio) audio - codec.id else audio + codec.id
                                    },
                                    label = {
                                        Column {
                                            Text(codec.label, style = MaterialTheme.typography.labelLarge)
                                            Text(
                                                codec.meaning,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

                    item {
                        FilterGroup(
                            title = "Taille",
                            subtitle = "En Go (1 Go = 1024 Mo). Laissez vide pour ne pas borner.",
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = minSize,
                                    onValueChange = { minSize = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                                    label = { Text("Minimum (Go)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = maxSize,
                                    onValueChange = { maxSize = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                                    label = { Text("Maximum (Go)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    item {
                        FilterGroup(
                            title = "Année",
                            subtitle = "Année de sortie présente dans le nom du fichier.",
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = yearMin,
                                    onValueChange = { yearMin = it.filter { c -> c.isDigit() }.take(4) },
                                    label = { Text("Depuis") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = yearMax,
                                    onValueChange = { yearMax = it.filter { c -> c.isDigit() }.take(4) },
                                    label = { Text("Jusqu'à") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    item {
                        FilterGroup(
                            title = "Sources",
                            subtitle = "Nombre minimum de seeders (0 = pas de filtre).",
                        ) {
                            OutlinedTextField(
                                value = minSeeders,
                                onValueChange = { minSeeders = it.filter { c -> c.isDigit() }.take(5) },
                                label = { Text("Seeders minimum") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        onClick = {
                            languages = emptySet()
                            qualities = emptySet()
                            audio = emptySet()
                            minSize = ""
                            maxSize = ""
                            yearMin = ""
                            yearMax = ""
                            minSeeders = ""
                        },
                    ) { Text("Réinitialiser") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Button(
                        onClick = {
                            onApply(
                                SearchFilters(
                                    languages = languages,
                                    videoQualities = qualities,
                                    audioCodecs = audio,
                                    minSizeBytes = parseGo(minSize),
                                    maxSizeBytes = parseGo(maxSize),
                                    yearMin = yearMin.toIntOrNull() ?: 0,
                                    yearMax = yearMax.toIntOrNull() ?: 0,
                                    minSeeders = minSeeders.toIntOrNull() ?: 0,
                                ),
                            )
                        },
                        shape = RoundedCornerShape(50),
                    ) { Text("Appliquer") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

private fun parseGo(text: String): Long {
    val value = text.replace(',', '.').toDoubleOrNull() ?: return 0L
    if (value <= 0.0) return 0L
    return (value * GO).toLong()
}

private fun formatGo(bytes: Long): String {
    val value = bytes / GO
    return if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
}

/** Petit resume lisible, affiche a cote du bouton de filtres. */
@Composable
fun FiltersBadge(filters: SearchFilters) {
    val text = if (filters.isActive) "Filtres actifs" else "Filtres"
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (filters.isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/** Description en clair des filtres, pour les reglages. */
@Composable
fun FiltersSummaryText(filters: SearchFilters) {
    Column {
        Text(
            filters.summary(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (filters.isActive) {
            Spacer(Modifier.height(4.dp))
            Text(
                buildList {
                    if (filters.languages.isNotEmpty()) {
                        add(
                            "Langues : " + filters.languages.mapNotNull { id ->
                                ReleaseLanguage.entries.firstOrNull { it.id == id }?.label
                            }.joinToString(", "),
                        )
                    }
                    if (filters.videoQualities.isNotEmpty()) {
                        add(
                            "Qualités : " + filters.videoQualities.mapNotNull { id ->
                                VideoQuality.entries.firstOrNull { it.id == id }?.label
                            }.joinToString(", "),
                        )
                    }
                    if (filters.audioCodecs.isNotEmpty()) {
                        add(
                            "Audio : " + filters.audioCodecs.mapNotNull { id ->
                                AudioCodec.entries.firstOrNull { it.id == id }?.label
                            }.joinToString(", "),
                        )
                    }
                    if (filters.minSizeBytes > 0 || filters.maxSizeBytes > 0) {
                        val min = if (filters.minSizeBytes > 0) "${formatGo(filters.minSizeBytes)} Go" else "0"
                        val max = if (filters.maxSizeBytes > 0) "${formatGo(filters.maxSizeBytes)} Go" else "∞"
                        add("Taille : $min → $max")
                    }
                    if (filters.yearMin > 0 || filters.yearMax > 0) {
                        val min = if (filters.yearMin > 0) filters.yearMin.toString() else "…"
                        val max = if (filters.yearMax > 0) filters.yearMax.toString() else "…"
                        add("Année : $min → $max")
                    }
                    if (filters.minSeeders > 0) add("Seeders ≥ ${filters.minSeeders}")
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.size(2.dp))
    }
}
