package com.mobile.solartracker.domain

/** Aggregate figures across every period, matching dashboard.get_totals(). */
data class Totals(
    val days: Int,
    val importUnits: Double,
    val exportUnits: Double,
    val solarUnits: Double,
    val balance: Double,
    val consumption: Double,
)

fun List<ReadingPeriod>.totals(): Totals = Totals(
    days = sumOf { it.days },
    importUnits = sumOf { it.importDiff },
    exportUnits = sumOf { it.exportDiff },
    solarUnits = sumOf { it.solarYield },
    balance = sumOf { it.netBalance },
    consumption = sumOf { it.consumption },
)
