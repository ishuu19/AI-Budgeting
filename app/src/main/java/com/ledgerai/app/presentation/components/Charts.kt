package com.ledgerai.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerai.app.presentation.theme.CategoryColors

/** Vertical bars. Colours come from the theme categorical palette unless [colors] is passed. */
@Composable
fun LBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    colors: List<Color>? = null,
) {
    val palette = colors?.takeIf { it.isNotEmpty() } ?: CategoryColors
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Row(
        modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(L.Space.x8),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((64f * (v / max)).dp.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(L.RadiusSm))
                        .background(palette[i % palette.size])
                )
                val label = labels.getOrNull(i)
                if (!label.isNullOrBlank()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = L.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Donut. Reads [CategoryColors] in composition so a theme change repaints it. */
@Composable
fun LDonut(
    parts: List<Float>,
    modifier: Modifier = Modifier,
    colors: List<Color>? = null,
) {
    val palette = colors?.takeIf { it.isNotEmpty() } ?: CategoryColors
    Canvas(modifier.size(96.dp)) {
        val total = parts.sum().takeIf { it > 0f } ?: return@Canvas
        val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Butt)
        var start = -90f
        parts.forEachIndexed { i, part ->
            if (part <= 0f) return@forEachIndexed
            val sweep = 360f * (part / total)
            drawArc(
                color = palette[i % palette.size],
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                style = stroke,
            )
            start += sweep
        }
    }
}

/** One comparison line for a hero or small block. */
@Composable
fun LSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = L.Accent,
) {
    if (values.size < 2) return
    Canvas(modifier.fillMaxWidth().height(32.dp)) {
        val min = values.min()
        val max = values.max().let { if (it == min) it + 1f else it }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / values.lastIndex
            val y = size.height * (1f - (v - min) / (max - min))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Half-circle gauge, 0 to 1. */
@Composable
fun LGauge(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = L.Accent,
) {
    Canvas(modifier.size(96.dp)) {
        val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
        drawArc(L.BoxDeep, startAngle = 180f, sweepAngle = 180f, useCenter = false, style = stroke)
        drawArc(
            color,
            startAngle = 180f,
            sweepAngle = 180f * fraction.coerceIn(0f, 1f),
            useCenter = false,
            style = stroke,
        )
    }
}
