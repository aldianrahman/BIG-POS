package com.berdikariintigemilang.pos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grafik garis di mana SETIAP titik = satu transaksi (urut: pertama → terakhir),
 * dan tinggi titik = nilai transaksi tersebut. Ketuk / geser pada grafik untuk
 * melihat nilai & waktu transaksi yang dipilih. Tanpa dependency eksternal.
 */
@Composable
fun TransactionsChart(
    values: List<Float>,
    labels: List<String>,
    valueText: (Float) -> String,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    chartHeight: Dp = 170.dp
) {
    if (values.isEmpty()) return
    var selected by remember(values) { mutableStateOf(values.lastIndex) }
    val sel = selected.coerceIn(0, values.lastIndex)

    val baselineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val guideColor = MaterialTheme.colorScheme.outline
    val areaTop = lineColor.copy(alpha = 0.20f)
    val areaBottom = lineColor.copy(alpha = 0f)
    val dotRing = MaterialTheme.colorScheme.surface
    val dimDot = lineColor.copy(alpha = 0.5f)

    Column(modifier) {
        // Tooltip: nilai + waktu transaksi terpilih.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                valueText(values[sel]),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = lineColor
            )
            labels.getOrNull(sel)?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    "· $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "transaksi ke-${sel + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(values.size) {
                    val n = values.size
                    detectTapGestures { offset ->
                        selected = (offset.x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1)
                    }
                }
                .pointerInput(values.size) {
                    val n = values.size
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            selected = (offset.x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1)
                        }
                    ) { change, _ ->
                        change.consume()
                        selected = (change.position.x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1)
                    }
                }
        ) {
            val n = values.size
            val topPad = 14.dp.toPx()
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
                    line.moveTo(x, y); area.moveTo(x, baselineY); area.lineTo(x, y)
                } else {
                    line.lineTo(x, y); area.lineTo(x, y)
                }
            }
            area.lineTo(xAt(n - 1), baselineY)
            area.close()

            drawPath(area, brush = Brush.verticalGradient(listOf(areaTop, areaBottom), startY = topPad, endY = baselineY))
            drawLine(baselineColor, Offset(0f, baselineY), Offset(size.width, baselineY), strokeWidth = 1.dp.toPx())
            if (n > 1) {
                drawPath(line, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // Garis penanda vertikal pada transaksi terpilih.
            val selX = xAt(sel)
            val selY = yAt(values[sel])
            drawLine(guideColor, Offset(selX, topPad), Offset(selX, baselineY), strokeWidth = 1.dp.toPx())

            // Titik tiap transaksi (dilewati bila terlalu banyak agar tidak penuh).
            if (n <= 40) {
                values.forEachIndexed { i, v ->
                    drawCircle(dimDot, radius = 2.5.dp.toPx(), center = Offset(xAt(i), yAt(v)))
                }
            }
            // Titik terpilih disorot.
            drawCircle(dotRing, radius = 7.dp.toPx(), center = Offset(selX, selY))
            drawCircle(lineColor, radius = 4.5.dp.toPx(), center = Offset(selX, selY))
        }
        Spacer(Modifier.height(6.dp))
        // Rentang waktu: transaksi pertama → terakhir.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(labels.firstOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(labels.lastOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
