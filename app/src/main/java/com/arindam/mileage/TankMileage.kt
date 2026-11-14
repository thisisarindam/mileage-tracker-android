package com.arindam.mileage

import java.math.BigDecimal

enum class ReadingKind(val label: String) { PER_FILL("Per-fill"), BASELINE("Baseline"), CONFIRMED("Confirmed"), ESTIMATED("Estimate"), UNAVAILABLE("No baseline") }
data class TankReading(val value: Double?, val kind: ReadingKind, val detail: String = "")
data class TankSummary(
    val readings: Map<String, TankReading> = emptyMap(),
    val confirmedMileage: Double? = null,
    val confirmedCycles: Int = 0,
    val confirmedDistance: BigDecimal = BigDecimal.ZERO,
    val confirmedLitres: BigDecimal = BigDecimal.ZERO
)
/** Our documented local algorithm, not an assertion about the other APK's unrecovered formulas. */
object TankMileage {
    fun calculate(records: List<Fuel>): TankSummary {
        val readings = linkedMapOf<String, TankReading>()
        var totalDistance = BigDecimal.ZERO
        var totalLitres = BigDecimal.ZERO
        var cycles = 0
        records.groupBy { it.vehicleId }.values.forEach { rows ->
            val legacy = Mileage.perFill(rows)
            var baseline: Fuel? = null
            var previous: Fuel? = null
            var litres = BigDecimal.ZERO
            rows.sortedWith(compareBy<Fuel> { it.date }.thenBy { it.odometer.toBigDecimal() }.thenBy { it.createdAt }.thenBy { it.id }).forEach { row ->
                val discontinuity = previous?.let { row.odometer.toBigDecimal() <= it.odometer.toBigDecimal() } ?: false
                if (row.missedEntry || discontinuity || row.tankStatus == TankStatus.UNSPECIFIED) { baseline = null; litres = BigDecimal.ZERO }
                val start = baseline
                val reading = when {
                    row.tankStatus == TankStatus.UNSPECIFIED -> TankReading(legacy[row.id], ReadingKind.PER_FILL, "Tank status unspecified; original per-fill calculation")
                    start == null -> {
                        if (row.tankStatus == TankStatus.FULL) {
                            baseline = row
                            TankReading(null, ReadingKind.BASELINE, "Full tank starts a new cycle; its fuel is excluded from the next cycle")
                        } else TankReading(null, ReadingKind.UNAVAILABLE, "A full-tank baseline is needed first")
                    }
                    else -> {
                        litres += row.litres.toBigDecimal()
                        val distance = row.odometer.toBigDecimal() - start.odometer.toBigDecimal()
                        val mileage = quotient(distance, litres)
                        if (row.tankStatus == TankStatus.FULL) {
                            if (mileage != null) { totalDistance += distance; totalLitres += litres; cycles++ }
                            baseline = row; litres = BigDecimal.ZERO
                            TankReading(mileage, ReadingKind.CONFIRMED, "Distance since the previous full tank divided by partial and closing-fill litres")
                        } else TankReading(mileage, ReadingKind.ESTIMATED, "Running estimate since the last full tank; it is not a confirmed full-tank result")
                    }
                }
                readings[row.id] = if (row.missedEntry || discontinuity) reading.copy(detail = "Cycle reset: ${if (row.missedEntry) "missed entry" else "odometer did not increase"}. ${reading.detail}") else reading
                previous = row
            }
        }
        return TankSummary(readings, quotient(totalDistance, totalLitres), cycles, totalDistance, totalLitres)
    }
}
