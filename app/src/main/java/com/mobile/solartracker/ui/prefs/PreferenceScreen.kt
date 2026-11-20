package com.mobile.solartracker.ui.prefs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobile.solartracker.data.DbInfo
import com.mobile.solartracker.ui.AppUiState
import com.mobile.solartracker.ui.theme.Import
import com.mobile.solartracker.ui.theme.Muted
import com.mobile.solartracker.ui.theme.Surface2
import com.mobile.solartracker.util.humanSize

@Composable
fun PreferenceScreen(
    state: AppUiState,
    onSwitch: (String) -> Unit,
    onImport: (Uri) -> Unit,
    onExport: (Uri) -> Unit,
    onRestoreSeed: () -> Unit,
    onInitialize: () -> Unit,
    onDeleteDb: (String) -> Unit,
) {
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImport) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let(onExport) }

    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            ActiveDatabaseCard(
                state = state,
                onOpen = { openLauncher.launch(arrayOf("*/*")) },
                onExport = { exportLauncher.launch(state.activeName.ifBlank { "meter_logs.db" }) },
            )
        }

        item {
            DatabasesCard(
                state = state,
                onSwitch = onSwitch,
                onDelete = { pendingDelete = it },
            )
        }

        item {
            MaintenanceCard(
                activeValid = state.activeInfo?.valid != false,
                onInitialize = onInitialize,
                onRestoreSeed = onRestoreSeed,
            )
        }

        item { AboutCard() }
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete database?") },
            text = { Text("Remove $name from app storage? The original file you imported is unaffected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDb(name)
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
private fun ActiveDatabaseCard(
    state: AppUiState,
    onOpen: () -> Unit,
    onExport: () -> Unit,
) {
    val info = state.activeInfo
    SettingsCard(title = "ACTIVE DATABASE") {
        Text(
            text = info?.name ?: state.activeName.ifBlank { "none" },
            style = MaterialTheme.typography.titleMedium,
        )
        if (info != null) {
            Text(
                text = "${info.readingCount} readings · ${info.sizeBytes.humanSize()}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Text(
                text = info.path,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpen,
                modifier = Modifier.weight(1f),
            ) { Text("Open database…") }
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f),
            ) { Text("Save a copy…") }
        }
        Text(
            text = "Opening a file copies it into app storage and makes it the live database. " +
                "Use Save a copy to move edits back out.",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }
}

@Composable
private fun DatabasesCard(
    state: AppUiState,
    onSwitch: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    SettingsCard(title = "DATABASES IN APP STORAGE") {
        if (state.databases.isEmpty()) {
            Text("No databases found.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        state.databases.forEachIndexed { index, db ->
            DatabaseRow(
                info = db,
                active = db.name == state.activeName,
                onSwitch = { if (db.name != state.activeName) onSwitch(db.name) },
                onDelete = { onDelete(db.name) },
            )
            if (index != state.databases.lastIndex) {
                HorizontalDivider(color = Surface2)
            }
        }
    }
}

@Composable
private fun DatabaseRow(
    info: DbInfo,
    active: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSwitch)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = active, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(info.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${info.readingCount} readings · ${info.sizeBytes.humanSize()}" +
                    if (info.valid) "" else " · no meter_records table",
                style = MaterialTheme.typography.labelSmall,
                color = if (info.valid) Muted else Import,
            )
        }
        if (!active) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete database", tint = Muted)
            }
        }
    }
}

@Composable
private fun MaintenanceCard(
    activeValid: Boolean,
    onInitialize: () -> Unit,
    onRestoreSeed: () -> Unit,
) {
    SettingsCard(title = "MAINTENANCE") {
        if (!activeValid) {
            Text(
                text = "The active file has no meter_records table. Create it to start logging.",
                style = MaterialTheme.typography.bodySmall,
                color = Import,
            )
        }
        Button(
            onClick = onInitialize,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create schema in active file") }
        OutlinedButton(
            onClick = onRestoreSeed,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restore bundled meter_logs.db") }
        Text(
            text = "Restoring replaces the copy in app storage with the bundled 13-reading seed.",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }
}

@Composable
private fun AboutCard() {
    SettingsCard(title = "ABOUT") {
        Text("Solar Tracker 1.0", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Offline, single-user build. Reads and writes plain SQLite files directly — " +
                "no network access and no login.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Text(
            text = "Table: meter_records(id, date, import, export, solar_gen, addedon)",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
            content()
        }
    }
}
