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
import androidx.compose.runtime.Composable
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
                )

                ChartView.CUMULATIVE -> {
                    val cumulative = remember(points) { cumulativeSeries(points) }
                    ChartCard(
                        title = "Cumulative energy (kWh)",
                        labels = points.map { it.axisLabel },
                        series = cumulative,
                        line = true,
                    )
                }
            }
        }
    }
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
            Legend(series)
            if (line) {
                MultiLineChart(
                    labels = labels,
                    series = series,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                )
            } else {
                GroupedBarChart(
                    labels = labels,
                    series = series,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
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
