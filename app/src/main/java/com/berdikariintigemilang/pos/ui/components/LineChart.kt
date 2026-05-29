package com.berdikariintigemilang.pos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grafik garis sederhana berbasis Canvas (tanpa dependency eksternal).
 *
 * Titik diletakkan di tengah tiap kolom agar lurus dengan label di bawahnya.
 * Tampilan dibuat bersih: area gradien lembut + garis membulat + titik, dengan
 * titik terakhir disorot.
 */
@Composable
fun LineChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    chartHeight: Dp = 150.dp
) {
    if (values.isEmpty()) return
    val baselineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val areaTop = lineColor.copy(alpha = 0.22f)
    val areaBottom = lineColor.copy(alpha = 0f)
    val dotRing = MaterialTheme.colorScheme.surface

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val n = values.size
            val topPad = 12.dp.toPx()
            val bottomPad = 10.dp.toPx()
            val chartH = size.height - topPad - bottomPad
            val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
            val colW = size.width / n

            fun xAt(i: Int) = colW * (i + 0.5f)
            fun yAt(v: Float) = topPad + chartH * (1f - v / maxV)

            val baselineY = topPad + chartH
            val line = Path()
            val area = Path()
            values.forEachIndexed { i, v ->
                val x = xAt(i)
                val y = yAt(v)
                if (i == 0) {
                    line.moveTo(x, y)
                    area.moveTo(x, baselineY)
                    area.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    area.lineTo(x, y)
                }
            }
            area.lineTo(xAt(n - 1), baselineY)
            area.close()

            // Area gradien lembut di bawah garis.
            drawPath(
                area,
                brush = Brush.verticalGradient(listOf(areaTop, areaBottom), startY = topPad, endY = baselineY)
            )
            // Garis dasar tipis.
            drawLine(
                color = baselineColor,
                start = Offset(0f, baselineY),
                end = Offset(size.width, baselineY),
                strokeWidth = 1.dp.toPx()
            )
            // Garis tren.
            if (n > 1) {
                drawPath(
                    line,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            // Titik tiap hari; titik terakhir disorot.
            values.forEachIndexed { i, v ->
                val center = Offset(xAt(i), yAt(v))
                val last = i == n - 1
                drawCircle(color = dotRing, radius = (if (last) 6f else 4f).dp.toPx(), center = center)
                drawCircle(color = lineColor, radius = (if (last) 4f else 2.5f).dp.toPx(), center = center)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
