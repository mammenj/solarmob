package com.mobile.solartracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.solartracker.data.DatabaseManager
import com.mobile.solartracker.data.DbInfo
import com.mobile.solartracker.data.MeterReading
import com.mobile.solartracker.data.ReadingsRepository
import com.mobile.solartracker.data.SaveResult
import com.mobile.solartracker.domain.ReadingPeriod
import com.mobile.solartracker.domain.Totals
import com.mobile.solartracker.domain.totals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class AppUiState(
    val loading: Boolean = true,
    val activeName: String = "",
    val databases: List<DbInfo> = emptyList(),
    val readings: List<MeterReading> = emptyList(),
    val error: String? = null,
) {
    val periods: List<ReadingPeriod>
        get() = readings.zipWithNext { start, end -> ReadingPeriod(start, end) }

    val totals: Totals
        get() = periods.totals()

    val activeInfo: DbInfo?
        get() = databases.firstOrNull { it.name == activeName }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = DatabaseManager(app)
    private val repository = ReadingsRepository(manager)

    private val _state = MutableStateFlow(AppUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                repository.openActive()
                val snapshot = loadSnapshot()
                _state.update { snapshot.copy(loading = false) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(loading = false, error = t.message ?: "Could not open the database")
                }
            }
        }
    }

    fun switchDatabase(name: String) {
        viewModelScope.launch {
            try {
                repository.switchTo(name)
                val snapshot = loadSnapshot()
                _state.update { snapshot.copy(loading = false) }
                _messages.emit("Using $name")
            } catch (t: Throwable) {
                _messages.emit("Could not open $name: ${t.message}")
            }
        }
    }

    fun saveReading(date: LocalDate, importUnits: Double, exportUnits: Double, solarUnits: Double) {
        viewModelScope.launch {
            val result = try {
                repository.save(date, importUnits, exportUnits, solarUnits)
            } catch (t: Throwable) {
                SaveResult.Rejected(t.message ?: "Save failed")
            }
            when (result) {
                SaveResult.Inserted -> {
                    reloadReadings()
                    _messages.emit("Reading saved")
                }
                SaveResult.Updated -> {
                    reloadReadings()
                    _messages.emit("Reading for that date updated")
                }
                is SaveResult.Rejected -> _messages.emit(result.message)
            }
        }
    }

    fun deleteReading(id: Long) {
        viewModelScope.launch {
            try {
                if (repository.delete(id)) {
                    reloadReadings()
                    _messages.emit("Reading deleted")
                }
            } catch (t: Throwable) {
                _messages.emit("Delete failed: ${t.message}")
            }
        }
    }

    fun importDatabase(uri: Uri) {
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { manager.importFromUri(uri) }
                repository.switchTo(info.name)
                val snapshot = loadSnapshot()
                _state.update { snapshot.copy(loading = false) }
                _messages.emit("Opened ${info.name}")
            } catch (t: Throwable) {
                _messages.emit("Import failed: ${t.message}")
            }
        }
    }

    fun exportDatabase(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.exportTo(uri)
                _messages.emit("Copy saved")
            } catch (t: Throwable) {
                _messages.emit("Export failed: ${t.message}")
            }
        }
    }

    fun restoreSeedDatabase() {
        viewModelScope.launch {
            try {
                repository.close()
                val name = withContext(Dispatchers.IO) { manager.restoreSeed() }
                repository.openActive()
                val snapshot = loadSnapshot()
                _state.update { snapshot.copy(loading = false) }
                _messages.emit("Restored $name")
            } catch (t: Throwable) {
                _messages.emit("Restore failed: ${t.message}")
            }
        }
    }

    fun initializeActiveSchema() {
        viewModelScope.launch {
            try {
                repository.close()
                withContext(Dispatchers.IO) { manager.initializeSchema() }
                repository.openActive()
                val snapshot = loadSnapshot()
                _state.update { snapshot.copy(loading = false) }
                _messages.emit("Schema created")
            } catch (t: Throwable) {
                _messages.emit("Initialise failed: ${t.message}")
            }
        }
    }

    fun deleteDatabase(name: String) {
        viewModelScope.launch {
            if (name == _state.value.activeName) {
                _messages.emit("Switch away from the active database before deleting it")
                return@launch
            }
            try {
                withContext(Dispatchers.IO) { manager.delete(name) }
                val snapshot = loadSnapshot()
                _state.update { snapshot.copy(loading = false) }
                _messages.emit("Deleted $name")
            } catch (t: Throwable) {
                _messages.emit("Delete failed: ${t.message}")
            }
        }
    }

    private suspend fun reloadReadings() {
        val readings = repository.loadAll()
        _state.update { it.copy(readings = readings, loading = false) }
    }

    private suspend fun loadSnapshot(): AppUiState {
        val readings = repository.loadAll()
        val databases = withContext(Dispatchers.IO) { manager.listDatabases() }
        val active = withContext(Dispatchers.IO) { manager.activeName() }
        return AppUiState(
            loading = false,
            activeName = active,
            databases = databases,
            readings = readings,
        )
    }
}
