package com.mobile.solartracker.data

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface SaveResult {
    data object Inserted : SaveResult
    data object Updated : SaveResult
    data class Rejected(val message: String) : SaveResult
}

/**
 * All reads and writes go through a single mutex on the IO dispatcher, the
 * mobile equivalent of the asyncio lock used server-side. Switching databases
 * closes the old handle and opens the new one under the same lock.
 */
class ReadingsRepository(private val manager: DatabaseManager) {

    private val mutex = Mutex()
    private var db: SQLiteDatabase? = null
    private var openName: String? = null

    suspend fun openActive() = withContext(Dispatchers.IO) {
        mutex.withLock { openLocked(manager.activeName()) }
    }

    suspend fun switchTo(name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = openName ?: manager.activeName()
            try {
                openLocked(name)
            } catch (t: Throwable) {
                runCatching { openLocked(previous) }
                throw t
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            db?.close()
            db = null
            openName = null
        }
    }

    suspend fun loadAll(): List<MeterReading> = withContext(Dispatchers.IO) {
        mutex.withLock { loadAllLocked() }
    }

    suspend fun save(
        date: LocalDate,
        importUnits: Double,
        exportUnits: Double,
        solarUnits: Double,
    ): SaveResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (importUnits < 0 || exportUnits < 0 || solarUnits < 0) {
                return@withLock SaveResult.Rejected("Readings cannot be negative.")
            }

            val readings = loadAllLocked()
            val stamp = LocalDateTime.now().format(ADDED_ON)
            val existing = readings.firstOrNull { it.date == date }

            if (existing != null) {
                requireDb().execSQL(
                    """UPDATE meter_records
                       SET "import" = ?, "export" = ?, solar_gen = ?, addedon = ?
                       WHERE date = ?""",
                    arrayOf<Any>(importUnits, exportUnits, solarUnits, stamp, date.toString()),
                )
                return@withLock SaveResult.Updated
            }

            val prior = readings.lastOrNull { it.date < date }
            if (prior != null) {
                validate(importUnits, exportUnits, solarUnits, prior)?.let {
                    return@withLock SaveResult.Rejected(it)
                }
            }

            requireDb().execSQL(
                """INSERT INTO meter_records (date, "import", "export", solar_gen, addedon)
                   VALUES (?, ?, ?, ?, ?)""",
                arrayOf<Any>(date.toString(), importUnits, exportUnits, solarUnits, stamp),
            )
            SaveResult.Inserted
        }
    }

    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireDb().delete("meter_records", "id = ?", arrayOf(id.toString())) > 0
        }
    }

    /** Serialised with writes so the copied file is never half-written. */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireDb()
            manager.exportActiveTo(uri)
        }
    }

    private fun openLocked(name: String): SQLiteDatabase {
        if (openName == name && db?.isOpen == true) return db!!
        db?.close()
        db = null
        openName = null
        manager.setActive(name)
        val opened = manager.openActive()
        db = opened
        openName = name
        return opened
    }

    private fun requireDb(): SQLiteDatabase =
        db?.takeIf { it.isOpen } ?: throw IllegalStateException("No database is open")

    private fun loadAllLocked(): List<MeterReading> {
        val readings = ArrayList<MeterReading>()
        requireDb().rawQuery(
            """SELECT id, date, "import", "export", solar_gen, addedon
               FROM meter_records
               ORDER BY date ASC""",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                readings.add(
                    MeterReading(
                        id = cursor.getLong(0),
                        date = parseDate(cursor.getString(1)),
                        importUnits = cursor.getDouble(2),
                        exportUnits = cursor.getDouble(3),
                        solarUnits = cursor.getDouble(4),
                        addedOn = cursor.getString(5) ?: "",
                    )
                )
            }
        }
        return readings
    }

    private fun validate(
        importUnits: Double,
        exportUnits: Double,
        solarUnits: Double,
        prior: MeterReading,
    ): String? {
        val whenText = prior.date.toString()
        if (importUnits < prior.importUnits) {
            return "Import ${fmt(importUnits)} is lower than ${fmt(prior.importUnits)} on $whenText."
        }
        if (exportUnits < prior.exportUnits) {
            return "Export ${fmt(exportUnits)} is lower than ${fmt(prior.exportUnits)} on $whenText."
        }
        if (solarUnits < prior.solarUnits) {
            return "Solar ${fmt(solarUnits)} is lower than ${fmt(prior.solarUnits)} on $whenText."
        }
        return null
    }

    private fun parseDate(raw: String?): LocalDate {
        val text = raw?.trim().orEmpty()
        return runCatching { LocalDate.parse(text.take(10)) }
            .recoverCatching { LocalDate.parse(text.substringBefore(' ').replace('/', '-')) }
            .getOrElse { LocalDate.of(1970, 1, 1) }
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    companion object {
        private val ADDED_ON: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
