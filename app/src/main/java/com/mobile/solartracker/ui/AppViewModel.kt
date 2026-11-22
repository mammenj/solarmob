package com.mobile.solartracker.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.solartracker.data.DatabaseManager
import com.mobile.solartracker.data.DbInfo
import com.mobile.solartracker.data.GitHubSyncManager
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

data class SyncConfig(
    val owner: String = "mammenj",
    val repo: String = "solarmob",
    val branch: String = "main",
    val token: String = "",
) {
    val isConfigured: Boolean
        get() = owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank()
}

data class SyncState(
    val config: SyncConfig = SyncConfig(),
    val busy: Boolean = false,
    val message: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = DatabaseManager(app)
    private val repository = ReadingsRepository(manager)

    private val syncPrefs = app.getSharedPreferences("github_sync", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AppUiState())
    val state = _state.asStateFlow()

    private val _syncState = MutableStateFlow(loadSyncConfig())
    val syncState = _syncState.asStateFlow()

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

    fun pushDatabase(name: String) {
        viewModelScope.launch {
            val config = _syncState.value.config
            if (!config.isConfigured) {
                _messages.emit("Configure GitHub sync first")
                return@launch
            }
            _syncState.update { it.copy(busy = true, message = "Pushing $name…") }
            try {
                val sync = GitHubSyncManager(getApplication(), config.owner, config.repo, config.branch, config.token)
                val result = withContext(Dispatchers.IO) {
                    sync.pushFile(java.io.File(manager.databasesDir, name))
                }
                val msg = when (result) {
                    is GitHubSyncManager.PushResult.Pushed -> "Pushed $name"
                    is GitHubSyncManager.PushResult.Unchanged -> "No changes to push for $name"
                }
                _syncState.update { it.copy(busy = false, message = msg) }
                _messages.emit(msg)
            } catch (t: Throwable) {
                val msg = "Push failed: ${t.message}"
                _syncState.update { it.copy(busy = false, message = msg) }
                _messages.emit(msg)
            }
        }
    }

    fun pullDatabase(name: String) {
        viewModelScope.launch {
            val config = _syncState.value.config
            if (!config.isConfigured) {
                _messages.emit("Configure GitHub sync first")
                return@launch
            }
            _syncState.update { it.copy(busy = true, message = "Pulling $name…") }
            try {
                val sync = GitHubSyncManager(getApplication(), config.owner, config.repo, config.branch, config.token)
                val remote = withContext(Dispatchers.IO) { sync.listRemoteDatabases() }
                val target = remote.firstOrNull { it.name == name }
                if (target == null) {
                    val msg = "$name not found in the remote repository"
                    _syncState.update { it.copy(busy = false, message = msg) }
                    _messages.emit(msg)
                    return@launch
                }
                val wasActive = name == _state.value.activeName
                var pulled = false
                withContext(Dispatchers.IO) {
                    if (wasActive) repository.close()
                    try {
                        val result = sync.pullFile(target, manager.databasesDir)
                        pulled = result is GitHubSyncManager.PullResult.Pulled
                    } finally {
                        if (wasActive) repository.openActive()
                    }
                }
                reloadReadings()
                val msg = if (pulled) "Pulled $name" else "Already up to date: $name"
                _syncState.update { it.copy(busy = false, message = msg) }
                _messages.emit(msg)
            } catch (t: Throwable) {
                runCatching { repository.openActive() }
                val msg = "Pull failed: ${t.message}"
                _syncState.update { it.copy(busy = false, message = msg) }
                _messages.emit(msg)
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

    // ---------------------------------------------------------------- GitHub sync

    private fun loadSyncConfig(): SyncState {
        return SyncState(
            config = SyncConfig(
                owner = syncPrefs.getString("owner", "mammenj").orEmpty().ifBlank { "mammenj" },
                repo = syncPrefs.getString("repo", "solarmob").orEmpty().ifBlank { "solarmob" },
                branch = syncPrefs.getString("branch", "main").orEmpty().ifBlank { "main" },
                token = syncPrefs.getString("token", "").orEmpty(),
            )
        )
    }

    fun updateSyncConfig(config: SyncConfig) {
        syncPrefs.edit()
            .putString("owner", config.owner)
            .putString("repo", config.repo)
            .putString("branch", config.branch)
            .putString("token", config.token)
            .apply()
        _syncState.value = _syncState.value.copy(config = config, message = null)
    }

    fun testSyncConnection() {
        viewModelScope.launch {
            val config = _syncState.value.config
            if (!config.isConfigured) {
                _messages.emit("Fill in owner, repo and token first")
                return@launch
            }
            _syncState.update { it.copy(busy = true, message = null) }
            try {
                val manager = GitHubSyncManager(getApplication(), config.owner, config.repo, config.branch, config.token)
                val msg = withContext(Dispatchers.IO) { manager.testConnection() }
                _syncState.update { it.copy(busy = false, message = msg) }
                _messages.emit(msg)
            } catch (t: Throwable) {
                val msg = t.message ?: "Connection failed"
                _syncState.update { it.copy(busy = false, message = msg) }
                _messages.emit(msg)
            }
        }
    }

    fun pushAllDatabases() {
        viewModelScope.launch {
            val config = _syncState.value.config
            if (!config.isConfigured) {
                _messages.emit("Configure GitHub sync first")
                return@launch
            }
            _syncState.update { it.copy(busy = true, message = "Pushing…") }
            try {
                val sync = GitHubSyncManager(getApplication(), config.owner, config.repo, config.branch, config.token)
                val files = withContext(Dispatchers.IO) { manager.listDatabases().map { java.io.File(it.path) } }
                val pushed = mutableListOf<String>()
                val unchanged = mutableListOf<String>()
                withContext(Dispatchers.IO) {
                    repository.close()
                    try {
                        for (file in files) {
                            when (sync.pushFile(file)) {
                                is GitHubSyncManager.PushResult.Pushed -> pushed.add(file.name)
                                is GitHubSyncManager.PushResult.Unchanged -> unchanged.add(file.name)
                            }
                        }
                    } finally {
                        repository.openActive()
                    }
                }
                reloadReadings()
                val msg = buildString {
                    if (pushed.isNotEmpty()) append("Pushed ${pushed.size}")
                    if (unchanged.isNotEmpty()) {
                        if (pushed.isNotEmpty()) append(", ")
                        append("${unchanged.size} unchanged")
                    }
                    if (pushed.isEmpty() && unchanged.isEmpty()) append("Nothing to push")
                }
                _syncState.update { it.copy(busy = false, message = msg) }
                _messages.emit(msg)
            } catch (t: Throwable) {
                runCatching { repository.openActive() }
                val msg = "Push failed: ${t.message}"
                _syncState.update { it.copy(busy = false, message = msg) }
                _messages.emit(msg)
            }
        }
    }

    fun pullAllDatabases() {
        viewModelScope.launch {
            val config = _syncState.value.config
            if (!config.isConfigured) {
                _messages.emit("Configure GitHub sync first")
                return@launch
            }
            _syncState.update { it.copy(busy = true, message = "Pulling…") }
            try {
                val sync = GitHubSyncManager(getApplication(), config.owner, config.repo, config.branch, config.token)
                val remote = withContext(Dispatchers.IO) { sync.listRemoteDatabases() }
                if (remote.isEmpty()) {
                    val msg = "No databases found in the remote repository"
                    _syncState.update { it.copy(busy = false, message = msg) }
                    _messages.emit(msg)
                    return@launch
                }
                val pulled = mutableListOf<String>()
                val unchanged = mutableListOf<String>()
                withContext(Dispatchers.IO) {
                    repository.close()
                    try {
                        for (r in remote) {
                            when (sync.pullFile(r, manager.databasesDir)) {
                                is GitHubSyncManager.PullResult.Pulled -> pulled.add(r.name)
                                is GitHubSyncManager.PullResult.Unchanged -> unchanged.add(r.name)
                            }
                        }
                    } finally {
                        repository.openActive()
                    }
                }
                reloadReadings()
                val msg = buildString {
                    if (pulled.isNotEmpty()) append("Pulled ${pulled.size}")
                    if (unchanged.isNotEmpty()) {
                        if (pulled.isNotEmpty()) append(", ")
                        append("${unchanged.size} up to date")
                    }
                    if (pulled.isEmpty() && unchanged.isEmpty()) append("Nothing to pull")
                }
                _syncState.update { it.copy(busy = false, message = msg) }
                _messages.emit(msg)
            } catch (t: Throwable) {
                runCatching { repository.openActive() }
                val msg = "Pull failed: ${t.message}"
                _syncState.update { it.copy(busy = false, message = msg) }
                _messages.emit(msg)
            }
        }
    }
}
