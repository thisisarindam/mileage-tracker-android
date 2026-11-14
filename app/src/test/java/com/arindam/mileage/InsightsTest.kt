package com.arindam.mileage

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class InsightsTest {
    private fun fill(date: String, odo: String, litres: String, cost: String, station: String = "Example") = Fuel(date = date, odometer = odo, litres = litres, totalCost = cost, pricePerLitre = "100", station = station)
    @Test fun screenSpecificRulesMatchReferenceAcrossMonthBoundaries() {
        val rows = listOf(fill("2025-03-01", "600", "20", "2000"), fill("2025-01-01", "100", "10", "1000"), fill("2025-02-01", "300", "5", "500"))
        val result = Insights.from(rows, YearMonth.of(2025, 3))
        assertEquals(500.0 / 3, result.averageMonthlyDistance, 1e-9)
        assertEquals(20.0, result.dashboardMileage!!, 1e-9)
        assertEquals(500.0 / 35, result.lifetime.mileage!!, 1e-9)
        assertEquals(0, result.previous.distance.signum()) // Analytics has one fill this month.
        assertEquals("200", result.historyPrevious.distance.toPlainString()) // History uses nearest lower odometer.
        assertEquals(40.0, result.historyPrevious.mileage!!, 1e-9)
        assertEquals(listOf(2.5, 2000.0 / 300), result.runningCost)
        assertEquals(listOf(40.0, 15.0), result.efficiencyTrend)
        assertEquals(500.0 / 25, result.stationEfficiency.single().mileage, 1e-9)
        assertEquals("Example", result.mostUsedStation)
    }
    @Test fun januaryUsesPreviousDecemberAndZeroIntervalsAreExcluded() {
        val result = Insights.from(listOf(fill("2024-12-01", "100", "10", "1000"), fill("2024-12-02", "100", "0", "0"), fill("2025-01-01", "200", "5", "500")), YearMonth.of(2025, 1))
        assertEquals("1000", result.previous.spent.toPlainString())
        assertEquals(1, result.year.fillups)
        assertEquals(1, result.runningCost.size)
        assertEquals(20.0, result.efficiencyTrend.single(), 1e-9)
        assertEquals(0.0, Insights.from(emptyList()).averageMonthlyDistance, 0.0)
    }
}
