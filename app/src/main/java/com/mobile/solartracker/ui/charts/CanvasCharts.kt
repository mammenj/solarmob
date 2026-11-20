package com.mobile.solartracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.solartracker.ui.theme.GridLine
import com.mobile.solartracker.ui.theme.Muted
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

data class Series(val label: String, val color: Color, val values: List<Double>)

@Composable
fun GroupedBarChart(
    labels: List<String>,
    series: List<Series>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onTap: (Int) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = Muted, fontSize = 9.sp)

    Canvas(
        modifier
            .pointerInput(labels.size) {
                detectTapGestures { offset ->
                    if (labels.isEmpty()) return@detectTapGestures
                    val leftPad = 46.dp.toPx()
                    val rightPad = 8.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val groupW = plotW / labels.size
                    val idx = ((offset.x - leftPad) / groupW).toInt()
                    if (idx in labels.indices) onTap(idx)
                }
            }
    ) {
        if (labels.isEmpty() || series.isEmpty()) return@Canvas

        val leftPad = 46.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 26.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val maxValue = series.flatMap { it.values }.maxOrNull() ?: 0.0
        val axisMax = niceCeil(maxValue)

        drawGridAndYLabels(measurer, axisStyle, leftPad, rightPad, topPad, plotH, 0.0, axisMax)

        val groupW = plotW / labels.size
        val gap = groupW * 0.12f
        val barW = (groupW - gap * 2) / series.size

        if (selectedIndex != null && selectedIndex in labels.indices) {
            drawRect(
                color = Color.White.copy(alpha = 0.07f),
                topLeft = Offset(leftPad + groupW * selectedIndex, topPad),
                size = Size(groupW, plotH),
            )
        }

        labels.indices.forEach { groupIndex ->
            val groupLeft = leftPad + groupW * groupIndex + gap
            series.forEachIndexed { seriesIndex, s ->
                val value = s.values.getOrElse(groupIndex) { 0.0 }
                val height = (value / axisMax * plotH).toFloat().coerceIn(0f, plotH)
                drawRoundRect(
                    color = s.color,
                    topLeft = Offset(groupLeft + barW * seriesIndex, topPad + plotH - height),
                    size = Size(barW * 0.88f, height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }

        drawXLabels(
            measurer = measurer,
            style = axisStyle,
            labels = labels,
            centers = labels.indices.map { leftPad + groupW * it + groupW / 2f },
            baseline = topPad + plotH,
        )
    }
}

@Composable
fun MultiLineChart(
    labels: List<String>,
    series: List<Series>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onTap: (Int) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = Muted, fontSize = 9.sp)

    Canvas(
        modifier
            .pointerInput(labels.size) {
                detectTapGestures { offset ->
                    if (labels.isEmpty()) return@detectTapGestures
                    val leftPad = 46.dp.toPx()
                    val rightPad = 8.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val count = labels.size
                    val step = if (count == 1) plotW else plotW / (count - 1)
                    val idx = ((offset.x - leftPad) / step).toInt().coerceIn(0, count - 1)
                    if (idx in labels.indices) onTap(idx)
                }
            }
    ) {
        if (labels.isEmpty() || series.isEmpty()) return@Canvas

        val leftPad = 46.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 26.dp.toPx()
        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        val maxValue = series.flatMap { it.values }.maxOrNull() ?: 0.0
        val minValue = series.flatMap { it.values }.minOrNull() ?: 0.0
        val axisMax = niceCeil(maxValue)
        val axisMin = if (minValue < 0) -niceCeil(-minValue) else 0.0
        val span = (axisMax - axisMin).coerceAtLeast(1e-9)

        drawGridAndYLabels(measurer, axisStyle, leftPad, rightPad, topPad, plotH, axisMin, axisMax)

        val count = labels.size
        fun xFor(index: Int): Float =
            if (count == 1) leftPad + plotW / 2f
            else leftPad + plotW * index / (count - 1)

        fun yFor(value: Double): Float =
            topPad + plotH - ((value - axisMin) / span * plotH).toFloat()

        if (selectedIndex != null && selectedIndex in labels.indices) {
            val x = xFor(selectedIndex)
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(x, topPad),
                end = Offset(x, topPad + plotH),
                strokeWidth = 1.dp.toPx(),
            )
        }

        series.forEach { s ->
            val points = s.values.mapIndexed { index, value -> Offset(xFor(index), yFor(value)) }
            if (points.isNotEmpty()) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = path,
                    color = s.color,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                points.forEachIndexed { index, point ->
                    val r = if (index == selectedIndex) 5.dp.toPx() else 3.dp.toPx()
                    drawCircle(color = s.color, radius = r, center = point)
                    if (index == selectedIndex) {
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = point)
                    }
                }
            }
        }

        drawXLabels(
            measurer = measurer,
            style = axisStyle,
            labels = labels,
            centers = (0 until count).map { xFor(it) },
            baseline = topPad + plotH,
        )
    }
}

private fun DrawScope.drawGridAndYLabels(
    measurer: TextMeasurer,
    style: TextStyle,
    leftPad: Float,
    rightPad: Float,
    topPad: Float,
    plotH: Float,
    axisMin: Double,
    axisMax: Double,
) {
    val steps = 4
    val span = (axisMax - axisMin).coerceAtLeast(1e-9)
    for (step in 0..steps) {
        val value = axisMin + span * step / steps
        val y = topPad + plotH - ((value - axisMin) / span * plotH).toFloat()
        drawLine(
            color = GridLine,
            start = Offset(leftPad, y),
            end = Offset(size.width - rightPad, y),
            strokeWidth = 1f,
        )
        val layout = measurer.measure(AnnotatedString(formatAxis(value)), style = style)
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                x = leftPad - layout.size.width - 6.dp.toPx(),
                y = y - layout.size.height / 2f,
            ),
        )
    }
}

private fun DrawScope.drawXLabels(
    measurer: TextMeasurer,
    style: TextStyle,
    labels: List<String>,
    centers: List<Float>,
    baseline: Float,
) {
    if (labels.isEmpty()) return
    val stride = maxOf(1, ceil(labels.size / 4.0).toInt())

    data class Label(val x: Float, val width: Float, val layout: TextLayoutResult)

    val candidates = labels.indices
        .filter { it % stride == 0 || it == labels.lastIndex }
        .map { index ->
            val layout = measurer.measure(AnnotatedString(labels[index]), style = style)
            val center = centers.getOrElse(index) { 0f }
            val x = (center - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            Label(x, layout.size.width.toFloat(), layout)
        }

    val gap = 6.dp.toPx()
    val visible = ArrayList<Label>()
    candidates.forEachIndexed { index, candidate ->
        if (visible.isEmpty()) {
            visible.add(candidate)
        } else {
            val previous = visible.last()
            val separate = candidate.x - (previous.x + previous.width) >= gap
            when {
                separate -> visible.add(candidate)
                index == candidates.lastIndex -> visible[visible.lastIndex] = candidate
            }
        }
    }

    visible.forEach { label ->
        drawText(
            textLayoutResult = label.layout,
            topLeft = Offset(label.x, baseline + 6.dp.toPx()),
        )
    }
}

private fun niceCeil(value: Double): Double {
    if (value <= 0.0) return 1.0
    val exponent = floor(log10(value))
    val base = 10.0.pow(exponent)
    val normalized = value / base
    val nice = when {
        normalized <= 1.0 -> 1.0
        normalized <= 1.2 -> 1.2
        normalized <= 1.5 -> 1.5
        normalized <= 2.0 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 3.0 -> 3.0
        normalized <= 4.0 -> 4.0
        normalized <= 5.0 -> 5.0
        normalized <= 6.0 -> 6.0
        normalized <= 8.0 -> 8.0
        else -> 10.0
    }
    return nice * base
}

private fun formatAxis(value: Double): String {
    if (value == 0.0) return "0"
    return if (abs(value - round(value)) < 1e-9) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}
