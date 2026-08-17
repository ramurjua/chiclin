package com.example.chiclin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.sqrt

val chartPalette = listOf(
    Color(0xFF1E88E5), Color(0xFFE53935), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFFDD835), Color(0xFF6D4C41),
    Color(0xFFD81B60), Color(0xFF3949AB), Color(0xFF7CB342), Color(0xFFF4511E)
)

@Composable
fun PieChart(
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    emptyLabel: String = "Sin datos"
) {
    val total = data.sumOf { it.second }
    if (data.isEmpty() || total <= 0.0) {
        Text(emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(16.dp))
        return
    }

    var selectedIndex by remember(data) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        selectedIndex?.let { idx ->
            val (label, value) = data[idx]
            val pct = value / total * 100
            ChartTooltipCard(
                title = null,
                rows = listOf(
                    chartPalette[idx % chartPalette.size] to
                        "$label: ${"%.2f".format(value)} € (${"%.1f".format(pct)}%)"
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val diameter = minOf(size.width, size.height).toFloat()
                        val cx = (size.width - diameter) / 2f + diameter / 2f
                        val cy = (size.height - diameter) / 2f + diameter / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        if (sqrt(dx * dx + dy * dy) > diameter / 2f) return@detectTapGestures

                        // drawArc's angle 0 = 3 o'clock, clockwise; our first slice starts at -90 (12 o'clock).
                        val rawDeg = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
                        val tappedDeg = (rawDeg + 90) % 360

                        var cumulative = 0.0
                        var found: Int? = null
                        for (i in data.indices) {
                            val sweep = data[i].second / total * 360.0
                            if (tappedDeg >= cumulative && tappedDeg < cumulative + sweep) {
                                found = i
                                break
                            }
                            cumulative += sweep
                        }
                        selectedIndex = if (selectedIndex == found) null else found
                    }
                }
        ) {
            var startAngle = -90f
            val diameter = minOf(size.width, size.height)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            data.forEachIndexed { index, (_, value) ->
                val sweep = (value / total * 360.0).toFloat()
                drawArc(
                    color = chartPalette[index % chartPalette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = Size(diameter, diameter)
                )
                if (selectedIndex == index) {
                    drawArc(
                        color = Color.Black.copy(alpha = 0.6f),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = 6f)
                    )
                }
                startAngle += sweep
            }
        }

        data.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedIndex = if (selectedIndex == index) null else index }
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = chartPalette[index % chartPalette.size])
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
                val pct = value / total * 100
                Text(
                    text = "%.2f €  (%.1f%%)".format(value, pct),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
