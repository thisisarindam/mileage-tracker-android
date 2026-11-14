package com.arindam.mileage

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class RulesTest {
    private fun f(odo: String, litres: String, cost: String = "100", date: String = "2025-01-01") = Fuel(date = date, odometer = odo, litres = litres, totalCost = cost, pricePerLitre = "10")
    @Test fun originalDashboardAndAnalyticsHaveDifferentDenominators() {
        val rows = listOf(f("1400", "10"), f("1000", "20"), f("1200", "5"))
        assertEquals(400.0 / 15, Mileage.dashboardMileage(rows)!!, 1e-9)
        assertEquals(400.0 / 35, Mileage.analyticsMileage(rows)!!, 1e-9)
        assertEquals(listOf(null, 40.0, 20.0), Mileage.sorted(rows).map { Mileage.perFill(rows)[it.id] })
        assertNull(Mileage.dashboardMileage(rows.take(1)))
        assertEquals("3.33", Mileage.newTotal("1.111", "3"))
        assertEquals("1.00", Mileage.newTotal("1.005", "1"))
        assertEquals("3.34", Mileage.newTotal("1.115", "3"))
    }
    @Test fun duplicateOdometersZeroLitresAndMonthlySpending() {
        val rows = listOf(f("100", "2", "10.25"), f("100", "0", "12.75"), f("150", "0", "8", "2025-02-01"))
        assertTrue(Mileage.perFill(rows).values.all { it == null })
        assertNull(Mileage.dashboardMileage(rows))
        assertEquals(0, Mileage.monthSpent(rows, YearMonth.of(2025, 1)).compareTo("23.00".toBigDecimal()))
    }
    @Test fun csvQuotesBomCrLfAndPhysicalRowNumbers() {
        val parsed = Csv.parse("\uFEFFa,b\r\n\"x, y\",\"line1\nline2 \"\"quoted\"\"\"\r\nz,w\r\n")
        assertEquals(listOf("x, y", "line1\nline2 \"quoted\""), parsed[1].cells)
        assertEquals(4, parsed[2].line)
        assertEquals(parsed.map { it.cells }, Csv.parse(Csv.write(parsed.map { it.cells })).map { it.cells })
    }
    @Test fun malformedCsvRejected() {
        listOf("a,b\n\"unfinished", "a,b\nx\"y,z", "a,b\n\"x\"y,z").forEach { assertThrows(IllegalArgumentException::class.java) { Csv.parse(it) } }
    }
    @Test fun importMatchesReorderedHeadersAndReportsInvalidRows() {
        val headers = Imports.fuelHeaders.reversed()
        val values = mapOf("id" to "source", "user_id" to "ignored", "entry_date" to "2025-01-01", "odometer_km" to "1", "total_cost" to "1.123456700", "price_per_litre" to "8.50", "litres" to "0.1234", "station_name" to "", "notes" to "", "created_at" to "2025-02-01T00:00:00Z")
        val cells = headers.map { values.getValue(it) }
        val invalid = headers.map { if (it == "entry_date") "2025-13-01" else values.getValue(it) }
        val preview = Imports.preview(Csv.write(listOf(headers, cells, cells, invalid)), true, emptySet())
        assertEquals(1, preview.ready); assertEquals(1, preview.duplicates)
        assertEquals(4, preview.errors.single().line)
        assertEquals("1.123456700", preview.fuel.single().totalCost)
        assertEquals("2025-02-01T00:00:00Z", preview.fuel.single().createdAt)
        assertEquals(0, Imports.preview(Csv.write(listOf(headers, cells)), true, setOf("source")).ready)
    }
    @Test fun syntheticExportsRoundTrip() {
        val fuel = Imports.preview(resource("fuel_entries_rows.csv"), true, emptySet())
        assertTrue(fuel.errors.toString(), fuel.errors.isEmpty())
        assertEquals(2, fuel.ready)
        assertEquals("1000.123400", fuel.fuel.first().totalCost)
        assertTrue(fuel.fuel.first().notes.contains("\n"))
        val services = Imports.preview(resource("maintenance_logs_rows.csv"), false, emptySet())
        assertEquals(1, services.ready)
        assertEquals("450.1250", services.maintenance.single().totalCost)
        assertEquals(fuel.fuel.map { it.sourceId }, Imports.preview(Imports.exportFuel(fuel.fuel), true, emptySet()).fuel.map { it.sourceId })
        assertEquals(services.maintenance.single().notes, Imports.preview(Imports.exportMaintenance(services.maintenance), false, emptySet()).maintenance.single().notes)
    }
    @Test fun backupStrictValidationAndExactRoundTrip() {
        val b = Backup(fuel = Imports.preview(resource("fuel_entries_rows.csv"), true, emptySet()).fuel, maintenance = emptyList(), preferences = Preferences())
        assertEquals(b, Backups.decode(Backups.encode(b)))
        listOf(Backups.encode(b).replace("\"version\":1", "\"version\":2"), Backups.encode(b).replace("\"currency\":\"INR\",", ""), "{}", Backups.encode(b).replace("1000.123400", "NaN")).forEach { text -> assertThrows(Exception::class.java) { Backups.decode(text) } }
        assertThrows(IllegalArgumentException::class.java) { b.copy(fuel = b.fuel + b.fuel.first()).validate() }
    }
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
}
