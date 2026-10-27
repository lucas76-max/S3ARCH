package com.s3arch.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Barre de progression « vague » : le remplissage est traverse par deux sinusoides
 * qui glissent en continu, comme les indicateurs de chargement Material.
 *
 * [progress] a `null` produit une vague indeterminee (tache pas encore synchronisee).
 */
@Composable
fun WaveProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    waveColor: Color = Color.White,
    height: Dp = 10.dp,
) {
    val transition = rememberInfiniteTransition(label = "wave-progress")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-phase",
    )
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-shift",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50)),
    ) {
        val h = size.height
        val width = size.width
        val baseline = h * 0.52f
        val amplitude = h * 0.22f
        val wavelength = (width / 3.2f).coerceAtLeast(24f)

        drawRect(color = trackColor)

        fun wave(startX: Float, endX: Float, offset: Float, waveHeight: Float): Path {
            val path = Path()
            path.moveTo(startX, baseline)
            var x = startX
            while (x <= endX) {
                val angle = (x / wavelength) * 2f * Math.PI.toFloat() + offset
                val y = (baseline + sin(angle.toDouble()).toFloat() * waveHeight)
                    .coerceIn(0f, h)
                path.lineTo(x, y)
                x += 2f
            }
            return path
        }

        if (progress == null) {
            // Bande glissante : une vague lumineuse parcourt la piste.
            val band = width * 0.42f
            val start = -band + (width + band) * shift
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = 0.9f), Color.Transparent),
                    startX = start,
                    endX = start + band,
                ),
                topLeft = Offset(start, 0f),
                size = Size(band, h),
            )
            val crest = wave(
                startX = start,
                endX = start + band,
                offset = phase,
                waveHeight = amplitude,
            )
            drawPath(
                path = crest,
                color = waveColor.copy(alpha = 0.35f),
                style = Stroke(width = h * 0.28f),
            )
            return@Canvas
        }

        val filled = width * progress.coerceIn(0f, 1f)
        if (filled <= 0f) return@Canvas

        drawRect(
            brush = Brush.horizontalGradient(
                listOf(color, color.copy(alpha = 0.78f), color),
                startX = 0f,
                endX = filled,
            ),
            topLeft = Offset(0f, 0f),
            size = Size(filled, h),
        )

        drawPath(
            path = wave(startX = 0f, endX = filled, offset = phase, waveHeight = amplitude),
            color = waveColor.copy(alpha = 0.30f),
            style = Stroke(width = h * 0.34f),
        )
        drawPath(
            path = wave(
                startX = 0f,
                endX = filled,
                offset = phase + (Math.PI / 2.4).toFloat(),
                waveHeight = amplitude * 0.7f,
            ),
            color = waveColor.copy(alpha = 0.18f),
            style = Stroke(width = h * 0.22f),
        )
    }
}
