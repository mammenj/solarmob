package com.mobile.solartracker.domain

import com.mobile.solartracker.data.MeterReading
import java.time.temporal.ChronoUnit

/**
 * Metrics for the gap between two consecutive cumulative readings.
 * Direct port of libsolar.ReadingPeriod.
 */
data class ReadingPeriod(val start: MeterReading, val end: MeterReading) {

    val days: Int = ChronoUnit.DAYS.between(start.date, end.date).toInt()

    val importDiff: Double = end.importUnits - start.importUnits
    val exportDiff: Double = end.exportUnits - start.exportUnits
    val solarYield: Double = end.solarUnits - start.solarUnits

    /** Positive means the grid received more than it gave. */
    val netBalance: Double = exportDiff - importDiff

    val consumption: Double = (solarYield + importDiff) - exportDiff

    val avgDailyConsumption: Double
        get() = if (days > 0) consumption / days else consumption

    private fun weekly(value: Double): Double =
        if (days <= 0) 0.0 else (value / days * 7).round2()

    val weeklyImport: Double get() = weekly(importDiff)
    val weeklyExport: Double get() = weekly(exportDiff)
    val weeklySolarYield: Double get() = weekly(solarYield)
    val weeklyConsumption: Double get() = weekly(consumption)
    val weeklyNetBalance: Double get() = weekly(netBalance)
}
