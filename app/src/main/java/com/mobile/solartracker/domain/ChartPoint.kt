package com.mobile.solartracker.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One chart-ready period, ported from dashboard.get_chart_data().
 */
data class ChartPoint(
    val startDate: LocalDate,
    val label: String,
    val axisLabel: String,
    val days: Int,
    val solar: Double,
    val export: Double,
    val importUnits: Double,
    val netBalance: Double,
    val consumption: Double,
    val selfSufficiencyPct: Double,
    val selfConsumptionPct: Double,
    val solarPerDay: Double,
    val importPerDay: Double,
    val exportPerDay: Double,
    val consumptionPerDay: Double,
    val selfConsumed: Double,
)

private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

fun ReadingPeriod.toChartPoint(): ChartPoint {
    val selfSufficiency =
        if (consumption > 0) (consumption - importDiff) / consumption * 100 else 0.0
    val selfConsumption =
        if (solarYield > 0) (solarYield - exportDiff) / solarYield * 100 else 0.0

    return ChartPoint(
        startDate = start.date,
        label = start.date.format(LONG_DATE),
        axisLabel = start.date.format(SHORT_DATE),
        days = days,
        solar = solarYield.round2(),
        export = exportDiff.round2(),
        importUnits = importDiff.round2(),
        netBalance = netBalance.round2(),
        consumption = consumption.round2(),
        selfSufficiencyPct = selfSufficiency.round1(),
        selfConsumptionPct = selfConsumption.round1(),
        solarPerDay = if (days > 0) (solarYield / days).round2() else 0.0,
        importPerDay = if (days > 0) (importDiff / days).round2() else 0.0,
        exportPerDay = if (days > 0) (exportDiff / days).round2() else 0.0,
        consumptionPerDay = if (days > 0) (consumption / days).round2() else 0.0,
        selfConsumed = (solarYield - exportDiff).round2(),
    )
}
