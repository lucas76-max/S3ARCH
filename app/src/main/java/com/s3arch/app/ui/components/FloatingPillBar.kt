package com.s3arch.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Une entree de la pastille de navigation. */
data class PillTab(val label: String, val icon: ImageVector)

/**
 * Barre de navigation flottante en forme de pastille.
 *
 * Toutes les pages montrent leur icone ; seule la page courante affiche son nom, a gauche
 * de son icone. La pastille teintee qui encadre le nom et l'icone est le fond de l'entree
 * selectionnee : comme les largeurs s'animent, elle glisse d'une page a l'autre pendant
 * que le nom de l'ancienne page s'efface et que celui de la nouvelle apparait.
 */
@Composable
fun FloatingPillBar(
    tabs: List<PillTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val active = index == selected
                // L'entree active prend presque deux fois plus de place : le fond teinte
                // glisse donc vers elle au lieu de sauter.
                val weight by animateFloatAsState(
                    targetValue = if (active) 2.1f else 1f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                    label = "pillWeight",
                )
                val background by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        Color.Transparent
                    },
                    label = "pillBackground",
                )
                Row(
                    modifier = Modifier
                        .weight(weight)
                        .height(50.dp)
                        .clip(CircleShape)
                        .background(background)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = active,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.size(7.dp))
                        }
                    }
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
