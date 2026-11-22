package com.mobile.solartracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/**
 * Owns the "which SQLite file are we using" question.
 *
 * Readings databases are plain SQLite files copied into app-private storage.
 * They are never reshaped: the legacy `meter_records` schema is used verbatim.
 * Selecting another file is therefore just "copy it in and reopen".
 */
class DatabaseManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val databasesDir: File
        get() {
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            return File(publicDocs, "solarmob").also { it.mkdirs() }
        }

    /** Name of the active database, creating an empty one on first launch. */
    fun activeName(): String {
        ensureDefaultDatabase()
        val stored = prefs.getString(KEY_ACTIVE, null)
        if (stored != null && File(databasesDir, stored).isFile) return stored
        return createEmptyDatabase()
    }

    fun setActive(name: String) {
        prefs.edit().putString(KEY_ACTIVE, name).apply()
    }

    fun activeFile(): File = File(databasesDir, activeName())

    fun listDatabases(): List<DbInfo> =
        databasesDir.listFiles { file -> file.isFile && file.name.endsWith(".db", true) }
            ?.map { infoFor(it) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** Opens a database read-only briefly to describe it. */
    fun infoFor(file: File): DbInfo {
        var count = 0
        var valid = false
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                valid = hasMeterTable(db)
                if (valid) {
                    db.rawQuery("SELECT COUNT(*) FROM meter_records", null).use { cursor ->
                        if (cursor.moveToFirst()) count = cursor.getInt(0)
                    }
                }
            }
        } catch (_: Exception) {
            valid = false
        }
        return DbInfo(file.name, file.path, file.length(), count, valid)
    }

    fun openActive(): SQLiteDatabase {
        val file = activeFile()
        if (!file.isFile) throw IOException("Database not found: ${file.name}")
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
    }

    /** Copies a picked document into app storage and makes it active. */
    fun importFromUri(uri: Uri): DbInfo {
        val target = uniqueTarget(displayName(uri) ?: "imported.db")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Could not read the selected file")

            val info = infoFor(target)
            if (!info.valid) {
                throw IllegalArgumentException("That file has no meter_records table")
            }
            setActive(target.name)
            return info
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    /** Copies the active file to a user-chosen destination. */
    fun exportActiveTo(uri: Uri) {
        val file = activeFile()
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("Could not open the destination file")
    }

    /** Creates the meter_records table in the active file (explicit user action only). */
    fun initializeSchema() {
        SQLiteDatabase.openDatabase(activeFile().path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(CREATE_TABLE_SQL)
            db.execSQL(CREATE_INDEX_SQL)
        }
    }

    fun delete(name: String): Boolean = File(databasesDir, name).delete()

    fun hasMeterTable(db: SQLiteDatabase): Boolean {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='meter_records'",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return false
        }
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(meter_records)", null).use { cursor ->
            while (cursor.moveToNext()) columns.add(cursor.getString(1))
        }
        return columns.containsAll(REQUIRED_COLUMNS)
    }

    /** Creates an empty meter_records database on first launch if none exists. */
    fun createEmptyDatabase(): String {
        val target = File(databasesDir, DEFAULT_NAME)
        if (!target.exists()) {
            SQLiteDatabase.openOrCreateDatabase(target, null).use { db ->
                db.execSQL(CREATE_TABLE_SQL)
                db.execSQL(CREATE_INDEX_SQL)
            }
        }
        setActive(target.name)
        return target.name
    }

    private fun ensureDefaultDatabase() {
        if (prefs.getBoolean(KEY_DEFAULT_CREATED, false)) return
        val hasDatabase = databasesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".db", true)
        }?.isNotEmpty() == true
        if (!hasDatabase) {
            createEmptyDatabase()
        }
        prefs.edit().putBoolean(KEY_DEFAULT_CREATED, true).apply()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun uniqueTarget(display: String): File {
        val base = display.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "imported.db" }
        val fileName = if (base.endsWith(".db", true)) base else "$base.db"
        var candidate = File(databasesDir, fileName)
        var suffix = 2
        while (candidate.exists()) {
            val stem = fileName.dropLast(3)
            candidate = File(databasesDir, "$stem-$suffix.db")
            suffix++
        }
        return candidate
    }

    companion object {
        const val DEFAULT_NAME = "solartracker.db"
        private const val PREFS = "solartracker"
        private const val KEY_ACTIVE = "active_db"
        private const val KEY_DEFAULT_CREATED = "default_db_created"
        private val REQUIRED_COLUMNS = setOf("id", "date", "import", "export", "solar_gen", "addedon")

        private const val CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS meter_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL UNIQUE,
                "import" REAL NOT NULL,
                "export" REAL NOT NULL,
                solar_gen REAL NOT NULL,
                addedon TEXT NOT NULL
            )
        """
        private const val CREATE_INDEX_SQL = "CREATE INDEX IF NOT EXISTS idx_date ON meter_records(date)"
    }
}
