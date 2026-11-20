package com.mobile.solartracker.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.solartracker.domain.ChartPoint
import com.mobile.solartracker.domain.toChartPoint
import com.mobile.solartracker.ui.AppUiState
import com.mobile.solartracker.ui.theme.Consumption
import com.mobile.solartracker.ui.theme.Export
import com.mobile.solartracker.ui.theme.Import
import com.mobile.solartracker.ui.theme.Muted
import com.mobile.solartracker.ui.theme.Solar
import com.mobile.solartracker.util.fmt1
import com.mobile.solartracker.util.fmt2

private enum class ChartView(val label: String) {
    FLOW("Energy Flow"),
    DAILY("Daily Rates"),
    CUMULATIVE("Cumulative"),
}

@Composable
fun ChartsScreen(state: AppUiState) {
    val allPoints = remember(state.readings) { state.periods.map { it.toChartPoint() } }
    var range by rememberSaveable { mutableStateOf(12) }
    var viewName by rememberSaveable { mutableStateOf(ChartView.FLOW.name) }
    val view = ChartView.entries.firstOrNull { it.name == viewName } ?: ChartView.FLOW

    val points = remember(allPoints, range) {
        if (range <= 0 || allPoints.size <= range) allPoints else allPoints.takeLast(range)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Charts",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Per-period deltas between consecutive readings. " +
                "Showing ${if (range <= 0) "all" else "the last $range"} periods.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )

        RangeChips(selected = range, onSelect = { range = it })
        ViewChips(selected = view, onSelect = { viewName = it.name })

        var selectedIndex by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(view, range) { selectedIndex = null }

        val selectedPoint = selectedIndex?.let { points.getOrNull(it) }

        if (points.isEmpty()) {
            EmptyCard()
        } else {
            when (view) {
                ChartView.FLOW -> ChartCard(
                    title = "Energy flow (kWh per period)",
                    labels = points.map { it.axisLabel },
                    series = listOf(
                        Series("Solar", Solar, points.map { it.solar }),
                        Series("Export", Export, points.map { it.export }),
                        Series("Import", Import, points.map { it.importUnits }),
                    ),
                    line = false,
                    selectedIndex = selectedIndex,
                    onTap = { selectedIndex = it },
                )

                ChartView.DAILY -> ChartCard(
                    title = "Daily rates (kWh per day)",
                    labels = points.map { it.axisLabel },
                    series = listOf(
                        Series("Solar/day", Solar, points.map { it.solarPerDay }),
                        Series("Import/day", Import, points.map { it.importPerDay }),
                        Series("Export/day", Export, points.map { it.exportPerDay }),
                        Series("Use/day", Consumption, points.map { it.consumptionPerDay }),
                    ),
                    line = false,
                    selectedIndex = selectedIndex,
                    onTap = { selectedIndex = it },
                )

                ChartView.CUMULATIVE -> {
                    val cumulative = remember(points) { cumulativeSeries(points) }
                    ChartCard(
                        title = "Cumulative energy (kWh)",
                        labels = points.map { it.axisLabel },
                        series = cumulative,
                        line = true,
                        selectedIndex = selectedIndex,
                        onTap = { selectedIndex = it },
                    )
                }
            }

            selectedPoint?.let { point ->
                ChartDetailCard(
                    view = view,
                    point = point,
                    cumulativeValues = if (view == ChartView.CUMULATIVE) {
                        cumulativeValuesUpTo(points, selectedIndex!!)
                    } else null,
                    onDismiss = { selectedIndex = null },
                )
            }
        }
    }
}

@Composable
private fun ChartDetailCard(
    view: ChartView,
    point: ChartPoint,
    cumulativeValues: List<Double>?,
    onDismiss: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            when (view) {
                ChartView.FLOW -> {
                    DetailRow("Solar", point.solar.fmt2(), Solar, "kWh")
                    DetailRow("Export", point.export.fmt2(), Export, "kWh")
                    DetailRow("Import", point.importUnits.fmt2(), Import, "kWh")
                    DetailRow("Self-sufficiency", point.selfSufficiencyPct.fmt1(), Solar, "%")
                    DetailRow("Self-consumption", point.selfConsumptionPct.fmt1(), Export, "%")
                    Text(
                        text = "${point.days} days in this period",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                ChartView.DAILY -> {
                    DetailRow("Solar/day", point.solarPerDay.fmt2(), Solar, "kWh")
                    DetailRow("Import/day", point.importPerDay.fmt2(), Import, "kWh")
                    DetailRow("Export/day", point.exportPerDay.fmt2(), Export, "kWh")
                    DetailRow("Use/day", point.consumptionPerDay.fmt2(), Consumption, "kWh")
                    Text(
                        text = "${point.days} days in this period",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                ChartView.CUMULATIVE -> {
                    val values = cumulativeValues ?: emptyList()
                    DetailRow("Solar (cumulative)", values.getOrElse(0) { 0.0 }.fmt2(), Solar, "kWh")
                    DetailRow("Export (cumulative)", values.getOrElse(1) { 0.0 }.fmt2(), Export, "kWh")
                    DetailRow("Import (cumulative)", values.getOrElse(2) { 0.0 }.fmt2(), Import, "kWh")
                    DetailRow("Consumption (cumulative)", values.getOrElse(3) { 0.0 }.fmt2(), Consumption, "kWh")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, color: androidx.compose.ui.graphics.Color, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(
            text = "$value $unit",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun cumulativeValuesUpTo(points: List<ChartPoint>, index: Int): List<Double> {
    var solar = 0.0
    var export = 0.0
    var imported = 0.0
    var consumption = 0.0
    for (i in 0..index.coerceIn(0, points.lastIndex)) {
        solar += points[i].solar
        export += points[i].export
        imported += points[i].importUnits
        consumption += points[i].consumption
    }
    return listOf(solar, export, imported, consumption)
}

@Composable
private fun RangeChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(6 to "6", 12 to "12", 24 to "24", 0 to "All").forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ViewChips(selected: ChartView, onSelect: (ChartView) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChartView.entries.forEach { view ->
            FilterChip(
                selected = selected == view,
                onClick = { onSelect(view) },
                label = { Text(view.label) },
            )
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    labels: List<String>,
    series: List<Series>,
    line: Boolean,
    selectedIndex: Int? = null,
    onTap: (Int) -> Unit = {},
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Tap a point for details.",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
            Legend(series)
            if (line) {
                MultiLineChart(
                    labels = labels,
                    series = series,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    selectedIndex = selectedIndex,
                    onTap = onTap,
                )
            } else {
                GroupedBarChart(
                    labels = labels,
                    series = series,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    selectedIndex = selectedIndex,
                    onTap = onTap,
                )
            }
        }
    }
}

@Composable
private fun Legend(series: List<Series>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        series.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(item.color, CircleShape)
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }
    }
}

@Composable
private fun EmptyCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Nothing to chart yet", style = MaterialTheme.typography.titleSmall)
            Text(
                "Charts need at least two readings so a period can be calculated.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }
}

private fun cumulativeSeries(points: List<ChartPoint>): List<Series> {
    var solarTotal = 0.0
    var exportTotal = 0.0
    var importTotal = 0.0
    var consumptionTotal = 0.0

    val solar = ArrayList<Double>(points.size)
    val export = ArrayList<Double>(points.size)
    val imported = ArrayList<Double>(points.size)
    val consumption = ArrayList<Double>(points.size)

    points.forEach { point ->
        solarTotal += point.solar
        exportTotal += point.export
        importTotal += point.importUnits
        consumptionTotal += point.consumption

        solar.add(solarTotal)
        export.add(exportTotal)
        imported.add(importTotal)
        consumption.add(consumptionTotal)
    }

    return listOf(
        Series("Solar", Solar, solar),
        Series("Export", Export, export),
        Series("Import", Import, imported),
        Series("Consumption", Consumption, consumption),
    )
}
