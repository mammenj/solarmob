package com.mobile.solartracker

import com.mobile.solartracker.data.MeterReading
import com.mobile.solartracker.domain.ReadingPeriod
import com.mobile.solartracker.domain.toChartPoint
import com.mobile.solartracker.domain.totals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DomainTest {

    private fun reading(date: String, importUnits: Double, exportUnits: Double, solarUnits: Double) =
        MeterReading(
            id = 0,
            date = LocalDate.parse(date),
            importUnits = importUnits,
            exportUnits = exportUnits,
            solarUnits = solarUnits,
        )

    private val first = reading("2026-01-01", 100.0, 50.0, 200.0)
    private val second = reading("2026-01-08", 110.0, 65.0, 260.0)

    @Test
    fun `period metrics match libsolar`() {
        val period = ReadingPeriod(first, second)

        assertEquals(7, period.days)
        assertEquals(10.0, period.importDiff, 1e-9)
        assertEquals(15.0, period.exportDiff, 1e-9)
        assertEquals(60.0, period.solarYield, 1e-9)
        assertEquals(5.0, period.netBalance, 1e-9)
        assertEquals(55.0, period.consumption, 1e-9)
        assertEquals(55.0 / 7.0, period.avgDailyConsumption, 1e-9)
    }

    @Test
    fun `weekly extrapolation normalises to seven days`() {
        val period = ReadingPeriod(first, second)

        assertEquals(10.0, period.weeklyImport, 1e-9)
        assertEquals(15.0, period.weeklyExport, 1e-9)
        assertEquals(60.0, period.weeklySolarYield, 1e-9)
        assertEquals(55.0, period.weeklyConsumption, 1e-9)
        assertEquals(5.0, period.weeklyNetBalance, 1e-9)
    }

    @Test
    fun `chart point percentages and rates match dashboard service`() {
        val point = ReadingPeriod(first, second).toChartPoint()

        assertEquals(81.8, point.selfSufficiencyPct, 1e-9)
        assertEquals(75.0, point.selfConsumptionPct, 1e-9)
        assertEquals(8.57, point.solarPerDay, 1e-9)
        assertEquals(1.43, point.importPerDay, 1e-9)
        assertEquals(2.14, point.exportPerDay, 1e-9)
        assertEquals(7.86, point.consumptionPerDay, 1e-9)
        assertEquals(45.0, point.selfConsumed, 1e-9)
    }

    @Test
    fun `chart percentages are zero when denominators are zero`() {
        val flat = reading("2026-02-01", 10.0, 10.0, 10.0)
        val point = ReadingPeriod(flat, flat).toChartPoint()

        assertEquals(0.0, point.selfSufficiencyPct, 1e-9)
        assertEquals(0.0, point.selfConsumptionPct, 1e-9)
    }

    @Test
    fun `totals sum every period`() {
        val third = reading("2026-01-15", 130.0, 90.0, 320.0)
        val totals = listOf(
            ReadingPeriod(first, second),
            ReadingPeriod(second, third),
        ).totals()

        assertEquals(14, totals.days)
        assertEquals(30.0, totals.importUnits, 1e-9)
        assertEquals(40.0, totals.exportUnits, 1e-9)
        assertEquals(120.0, totals.solarUnits, 1e-9)
        assertEquals(10.0, totals.balance, 1e-9)
        assertEquals(110.0, totals.consumption, 1e-9)
    }
}
