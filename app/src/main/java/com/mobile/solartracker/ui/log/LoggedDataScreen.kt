package com.mobile.solartracker.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.solartracker.data.MeterReading
import com.mobile.solartracker.domain.ReadingPeriod
import com.mobile.solartracker.ui.AppUiState
import com.mobile.solartracker.ui.theme.Consumption
import com.mobile.solartracker.ui.theme.Export
import com.mobile.solartracker.ui.theme.Import
import com.mobile.solartracker.ui.theme.Muted
import com.mobile.solartracker.ui.theme.Solar
import com.mobile.solartracker.util.displayDate
import com.mobile.solartracker.util.fmt2

private const val PAGE_SIZE = 10

@Composable
fun LoggedDataScreen(state: AppUiState, onDelete: (Long) -> Unit) {
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var page by rememberSaveable { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<MeterReading?>(null) }

    val chronological = state.readings
    val previousById = remember(chronological) {
        chronological.mapIndexed { index, reading ->
            reading.id to chronological.getOrNull(index - 1)
        }.toMap()
    }
    val ordered = remember(chronological, newestFirst) {
        if (newestFirst) chronological.asReversed() else chronological
    }

    val pageCount = maxOf(1, (ordered.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val safePage = page.coerceIn(0, pageCount - 1)
    val visible = ordered.drop(safePage * PAGE_SIZE).take(PAGE_SIZE)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SummaryCard(state) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Readings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {
                        newestFirst = !newestFirst
                        page = 0
                    },
                    label = { Text(if (newestFirst) "Newest first" else "Oldest first") },
                )
            }
        }

        if (ordered.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("No readings yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Add your first reading from the Entry tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
            }
        }

        items(visible, key = { it.id }) { reading ->
            ReadingCard(
                reading = reading,
                previous = previousById[reading.id],
                onDelete = { pendingDelete = reading },
            )
        }

        if (ordered.size > PAGE_SIZE) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { page = safePage - 1 },
                        enabled = safePage > 0,
                    ) { Text("Previous") }
                    Text(
                        text = "Page ${safePage + 1} of $pageCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                    TextButton(
                        onClick = { page = safePage + 1 },
                        enabled = safePage < pageCount - 1,
                    ) { Text("Next") }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }

    pendingDelete?.let { reading ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete reading?") },
            text = {
                Text(
                    "Remove the reading from ${reading.date.displayDate()}? " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(reading.id)
                        pendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SummaryCard(state: AppUiState) {
    val totals = state.totals
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${state.periods.size} periods · ${totals.days} days · " +
                    "${state.readings.size} readings",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Import", totals.importUnits.fmt2(), null, Import, Modifier.weight(1f))
                Metric("Export", totals.exportUnits.fmt2(), null, Export, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Solar", totals.solarUnits.fmt2(), null, Solar, Modifier.weight(1f))
                Metric("Consumption", totals.consumption.fmt2(), null, Consumption, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(
                    label = "Net balance",
                    value = (if (totals.balance >= 0) "+" else "") + totals.balance.fmt2(),
                    delta = null,
                    color = if (totals.balance >= 0) Export else Import,
                    modifier = Modifier.weight(1f),
                )
                Metric("Days", totals.days.toString(), null, Muted, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReadingCard(
    reading: MeterReading,
    previous: MeterReading?,
    onDelete: () -> Unit,
) {
    val period = previous?.let { ReadingPeriod(it, reading) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reading.date.displayDate(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = period?.let {
                            "${it.days} days · ${it.consumption.fmt2()} kWh consumed"
                        } ?: "First reading",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(
                    label = "Import",
                    value = reading.importUnits.fmt2(),
                    delta = period?.let { "+" + it.importDiff.fmt2() },
                    color = Import,
                    modifier = Modifier.weight(1f),
                )
                Metric(
                    label = "Export",
                    value = reading.exportUnits.fmt2(),
                    delta = period?.let { "+" + it.exportDiff.fmt2() },
                    color = Export,
                    modifier = Modifier.weight(1f),
                )
                Metric(
                    label = "Solar",
                    value = reading.solarUnits.fmt2(),
                    delta = period?.let { "+" + it.solarYield.fmt2() },
                    color = Solar,
                    modifier = Modifier.weight(1f),
                )
            }
            period?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric(
                        label = "Avg/day",
                        value = it.avgDailyConsumption.fmt2(),
                        delta = null,
                        color = Consumption,
                        modifier = Modifier.weight(1f),
                    )
                    Metric(
                        label = "Net balance",
                        value = (if (it.netBalance >= 0) "+" else "") + it.netBalance.fmt2(),
                        delta = null,
                        color = if (it.netBalance >= 0) Export else Import,
                        modifier = Modifier.weight(1f),
                    )
                    Metric(
                        label = "Consumption",
                        value = it.consumption.fmt2(),
                        delta = null,
                        color = Consumption,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    delta: String?,
    color: Color,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        if (delta != null) {
            Text(delta, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}
