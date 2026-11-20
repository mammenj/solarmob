package com.mobile.solartracker.data

import java.time.LocalDate

/**
 * One cumulative meter snapshot, mirroring the `meter_records` row used by the
 * Python web app. Values are lifetime totals, not per-day usage.
 */
data class MeterReading(
    val id: Long = 0L,
    val date: LocalDate,
    val importUnits: Double,
    val exportUnits: Double,
    val solarUnits: Double,
    val addedOn: String = "",
)
