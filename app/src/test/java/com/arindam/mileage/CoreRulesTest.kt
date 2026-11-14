package com.arindam.mileage

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.system.measureNanoTime

class CoreRulesTest {
    private fun f(id: String, odo: String, litres: String, status: String = TankStatus.UNSPECIFIED, missed: Boolean = false, vehicle: String = DEFAULT_VEHICLE_ID, date: String = "2025-01-01", cost: String = "100", station: String = "Example") = Fuel(id = id, date = date, odometer = odo, totalCost = cost, pricePerLitre = "100", litres = litres, station = station, vehicleId = vehicle, tankStatus = status, missedEntry = missed, createdAt = "2025-01-01T00:00:00Z")
    @Test fun tankCyclesUsePartialAndClosingFuelButExcludeBaseline() {
        val rows = listOf(f("a", "1000", "10", TankStatus.FULL), f("b", "1050", "4", TankStatus.PARTIAL), f("c", "1200", "6", TankStatus.FULL), f("d", "1300", "2", TankStatus.FULL))
        val result = TankMileage.calculate(rows)
        assertEquals(ReadingKind.BASELINE, result.readings.getValue("a").kind)
        assertEquals(12.5, result.readings.getValue("b").value!!, 1e-9)
        assertEquals(ReadingKind.ESTIMATED, result.readings.getValue("b").kind)
        assertEquals(20.0, result.readings.getValue("c").value!!, 1e-9)
        assertEquals(50.0, result.readings.getValue("d").value!!, 1e-9)
        assertEquals(2, result.confirmedCycles)
        assertEquals(25.0, result.confirmedMileage!!, 1e-9) // weighted 300 km / 12 L, not mean(20,50)
    }
    @Test fun missedUnknownAndOdometerDiscontinuitiesNeverBridgeCycles() {
        val full = f("a", "1000", "10", TankStatus.FULL)
        for (middle in listOf(f("b", "1050", "4", TankStatus.PARTIAL, missed = true), f("b", "1050", "4"))) {
            val result = TankMileage.calculate(listOf(full, middle, f("c", "1200", "6", TankStatus.FULL)))
            assertEquals(0, result.confirmedCycles)
            assertEquals(ReadingKind.BASELINE, result.readings.getValue("c").kind)
        }
        val reset = TankMileage.calculate(listOf(full, f("b", "900", "4", TankStatus.FULL, date = "2025-01-02"), f("c", "1000", "5", TankStatus.FULL, date = "2025-01-03")))
        assertNull(reset.readings.getValue("b").value)
        assertEquals(20.0, reset.readings.getValue("c").value!!, 1e-9)
        val zero = TankMileage.calculate(listOf(full, f("b", "1000", "4", TankStatus.PARTIAL)))
        assertNull(zero.readings.getValue("b").value)
    }
    @Test fun vehicleHistoriesAndOriginalUnspecifiedRulesRemainSeparate() {
        val rows = listOf(f("a", "100", "10"), f("b", "200", "5"), f("c", "9000", "20", vehicle = "other"), f("d", "9200", "10", vehicle = "other"))
        val result = TankMileage.calculate(rows)
        assertEquals(20.0, result.readings.getValue("b").value!!, 1e-9)
        assertNull(result.readings.getValue("c").value)
        assertEquals(20.0, result.readings.getValue("d").value!!, 1e-9)
        assertEquals(0, result.confirmedCycles)
        assertEquals(ReadingKind.PER_FILL, result.readings.getValue("b").kind)
        assertThrows(IllegalArgumentException::class.java) { f("zero", "300", "0", TankStatus.FULL).validate() }
        f("legacy-zero", "300", "0").validate()
    }
    @Test fun filtersUseNumericCostsInclusiveDatesAndDoNotRecalculateMileage() {
        val rows = listOf(f("a", "100", "10", date = "2025-01-01", cost = "9", station = "A"), f("b", "200", "5", date = "2025-01-02", cost = "100", station = "B"), f("c", "300", "4", date = "2025-01-03", cost = "20", station = "B"))
        val service = Maintenance(id = "s", date = "2025-01-02", serviceType = "Oil change", odometer = "200", totalCost = "90")
        val index = HistoryIndex.prepare(rows, listOf(service))
        val found = index.filter(HistoryFilter(station = "B", from = "2025-01-02", to = "2025-01-03", sort = HistorySort.HIGHEST_COST), DEFAULT_VEHICLE_ID)
        assertEquals(listOf("b", "c"), found.fuel.map { it.id })
        assertEquals(20.0, TankMileage.calculate(rows).readings.getValue(found.fuel.first().id).value!!, 1e-9)
        assertEquals(listOf("s"), index.filter(HistoryFilter(query = "OIL", station = "B"), DEFAULT_VEHICLE_ID).maintenance.map { it.id })
        assertEquals("a", index.filter(HistoryFilter(sort = HistorySort.OLDEST), DEFAULT_VEHICLE_ID).fuel.first().id)
        assertThrows(IllegalArgumentException::class.java) { HistoryFilter(from = "2025-02-01", to = "2025-01-01").validate() }
        assertTrue(index.filter(HistoryFilter(query = "unmatched"), DEFAULT_VEHICLE_ID).fuel.isEmpty())
    }
    @Test fun largeHistoryIndexesAndChartsHaveBoundedRenderingData() {
        val rows = (0 until 20_000).map { i -> f("record-$i", (1000 + i * 10).toString(), "2", date = LocalDate.of(2020, 1, 1).plusDays(i.toLong()).toString(), cost = (i + 1).toString(), station = if (i % 2 == 0) "Even" else "Odd") }
        lateinit var index: HistoryIndex
        val preparation = measureNanoTime { index = HistoryIndex.prepare(rows, emptyList()) }
        var result = FilteredHistory()
        val filtering = measureNanoTime { result = index.filter(HistoryFilter(station = "Even", sort = HistorySort.HIGHEST_COST), DEFAULT_VEHICLE_ID) }
        assertEquals(10_000, result.fuel.size)
        assertEquals("record-19998", result.fuel.first().id)
        val plot = ChartPlot.from((0 until 20_000).map { it.toDouble() })
        assertEquals(120, plot.samples.size); assertEquals(0.0, plot.lowest!!, 0.0); assertEquals(19999.0, plot.highest!!, 0.0)
        assertEquals(9999.5, plot.average!!, 0.0)
        val insights = Insights.from(rows)
        assertEquals(120, insights.pricePlot.samples.size)
        assertEquals(120, insights.efficiencyPlot.samples.size)
        assertEquals(20_000, insights.lifetime.fillups)
        val tanks = TankMileage.calculate(rows)
        assertEquals(20_000, tanks.readings.size)
        assertEquals(5.0, tanks.readings.getValue("record-19999").value!!, 0.0)
        println("20k synthetic rows: index=${preparation/1_000_000}ms, indexed sort/filter=${filtering/1_000_000}ms; chart samples=${plot.samples.size}")
    }
}
