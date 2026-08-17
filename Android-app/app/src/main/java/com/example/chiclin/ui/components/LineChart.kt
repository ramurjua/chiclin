package com.example.chiclin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class LineSeries(
    val label: String,
    val values: List<Double>,
    val color: Color,
    val visible: Boolean = true
)

/**
 * Multi-series line chart: X axis = [xLabels] (one per month), Y axis = money with a fixed
 * axis scale, and a tap-anywhere tooltip so exact values are always available even without
 * printed gridline numbers matching every point.
 */
@Composable
fun LineChart(
    xLabels: List<String>,
    series: List<LineSeries>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 200.dp,
    pointSpacing: Dp = 70.dp,
    yAxisWidth: Dp = 60.dp,
    emptyLabel: String = "Se necesitan al menos 2 meses con datos."
) {
    val visibleSeries = series.filter { it.visible && it.values.isNotEmpty() }

    if (xLabels.size < 2 || visibleSeries.isEmpty()) {
        Text(emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(16.dp))
        return
    }

    val maxVal = (visibleSeries.flatMap { it.values }.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
    val totalWidth = pointSpacing * (xLabels.size - 1)
    val scrollState = rememberScrollState()
    var selectedIndex by remember(xLabels) { mutableStateOf<Int?>(null) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highlightColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier) {
        selectedIndex?.let { idx ->
            ChartTooltipCard(
                title = xLabels[idx],
                rows = visibleSeries.map { s ->
                    s.color to "${s.label}: ${"%.2f".format(s.values.getOrElse(idx) { 0.0 })} €"
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val axisSteps = 4

        Row {
            Column(
                modifier = Modifier.width(yAxisWidth).height(chartHeight),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in axisSteps downTo 0) {
                    val value = maxVal * i / axisSteps
                    Text(
                        "${"%.0f".format(value)} €",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }

            Box(modifier = Modifier.horizontalScroll(scrollState)) {
                Canvas(
                    modifier = Modifier
                        .width(totalWidth)
                        .height(chartHeight)
                        .pointerInput(xLabels) {
                            detectTapGestures { offset ->
                                val stepPx = size.width / (xLabels.size - 1)
                                val idx = (offset.x / stepPx).roundToInt().coerceIn(0, xLabels.size - 1)
                                selectedIndex = if (selectedIndex == idx) null else idx
                            }
                        }
                ) {
                    for (i in 0..axisSteps) {
                        val y = size.height - size.height * i / axisSteps
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }

                    val stepX = size.width / (xLabels.size - 1)

                    selectedIndex?.let { idx ->
                        val x = stepX * idx
                        drawLine(
                            color = highlightColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2f
                        )
                    }

                    visibleSeries.forEach { s ->
                        val points = s.values.mapIndexed { i, v ->
                            Offset(stepX * i, size.height - (v / maxVal * size.height).toFloat())
                        }
                        for (i in 0 until points.size - 1) {
                            drawLine(
                                color = s.color,
                                start = points[i],
                                end = points[i + 1],
                                strokeWidth = 5f,
                                cap = StrokeCap.Round
                            )
                        }
                        points.forEach { p ->
                            drawCircle(color = s.color, radius = 6f, center = p)
                        }
                    }
                }
            }
        }

        Row {
            Spacer(modifier = Modifier.width(yAxisWidth))
            Box(modifier = Modifier.horizontalScroll(scrollState)) {
                Box(
                    modifier = Modifier
                        .width(totalWidth)
                        .height(28.dp)
                ) {
                    xLabels.forEachIndexed { i, label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .width(pointSpacing)
                                .offset(x = pointSpacing * i - pointSpacing / 2)
                        )
                    }
                }
            }
        }
    }
}
