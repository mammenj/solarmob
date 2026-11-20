package com.mobile.solartracker.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobile.solartracker.ui.AppUiState
import com.mobile.solartracker.ui.theme.Export
import com.mobile.solartracker.ui.theme.Import
import com.mobile.solartracker.ui.theme.Muted
import com.mobile.solartracker.ui.theme.Solar
import com.mobile.solartracker.util.displayDate
import com.mobile.solartracker.util.fmt2
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    state: AppUiState,
    onSave: (LocalDate, Double, Double, Double) -> Unit,
) {
    var dateIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date = remember(dateIso) {
        runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now())
    }

    var importText by rememberSaveable { mutableStateOf("") }
    var exportText by rememberSaveable { mutableStateOf("") }
    var solarText by rememberSaveable { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    val importValue = importText.toDoubleOrNull()
    val exportValue = exportText.toDoubleOrNull()
    val solarValue = solarText.toDoubleOrNull()
    val inputsValid = importValue != null && exportValue != null && solarValue != null &&
        importValue >= 0 && exportValue >= 0 && solarValue >= 0

    val latest = state.readings.lastOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "New reading",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Enter cumulative meter totals. A date that already exists is updated in place.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )

        if (latest != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "LATEST READING",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                    Text(
                        text = latest.date.displayDate(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LatestValue("Import", latest.importUnits.fmt2(), Import, Modifier.weight(1f))
                        LatestValue("Export", latest.exportUnits.fmt2(), Export, Modifier.weight(1f))
                        LatestValue("Solar", latest.solarUnits.fmt2(), Solar, Modifier.weight(1f))
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = date.displayDate(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { showPicker = true }
            )
        }

        DecimalField(
            label = "Grid import",
            value = importText,
            onValueChange = { importText = cleanDecimal(it) },
            hint = latest?.let { "Last: ${it.importUnits.fmt2()} kWh" },
        )
        DecimalField(
            label = "Grid export",
            value = exportText,
            onValueChange = { exportText = cleanDecimal(it) },
            hint = latest?.let { "Last: ${it.exportUnits.fmt2()} kWh" },
        )
        DecimalField(
            label = "Solar generation",
            value = solarText,
            onValueChange = { solarText = cleanDecimal(it) },
            hint = latest?.let { "Last: ${it.solarUnits.fmt2()} kWh" },
        )

        Spacer(Modifier.height(2.dp))

        Button(
            onClick = { onSave(date, importValue!!, exportValue!!, solarValue!!) },
            enabled = inputsValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Save reading")
        }

        Text(
            text = "Values must not be lower than the previous reading for that date range.",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            dateIso = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        showPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun LatestValue(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun DecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text("kWh") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = { if (hint != null) Text(hint) },
        isError = value.isNotBlank() && value.toDoubleOrNull() == null,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Keeps digits and at most one decimal point. */
private fun cleanDecimal(raw: String): String {
    val digitsAndDot = raw.filter { it.isDigit() || it == '.' }
    val firstDot = digitsAndDot.indexOf('.')
    return if (firstDot == -1) {
        digitsAndDot
    } else {
        digitsAndDot.substring(0, firstDot + 1) +
            digitsAndDot.substring(firstDot + 1).replace(".", "")
    }
}
