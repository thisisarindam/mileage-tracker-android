package com.arindam.mileage

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

data class PeriodStats(
    val spent: BigDecimal = BigDecimal.ZERO,
    val distance: BigDecimal = BigDecimal.ZERO,
    val litres: BigDecimal = BigDecimal.ZERO,
    val mileage: Double? = null,
    val fillups: Int = 0
)
data class StationEfficiency(val name: String, val mileage: Double)
data class Insights(
    val month: YearMonth = YearMonth.now(),
    val current: PeriodStats = PeriodStats(),
    val previous: PeriodStats = PeriodStats(),
    val historyPrevious: PeriodStats = PeriodStats(),
    val year: PeriodStats = PeriodStats(),
    val lifetime: PeriodStats = PeriodStats(),
    val dashboardMileage: Double? = null,
    val averageMonthlyDistance: Double = 0.0,
    val priceTrend: List<Double> = emptyList(),
    val runningCost: List<Double> = emptyList(),
    val efficiencyTrend: List<Double> = emptyList(),
    val mostUsedStation: String = "Unknown",
    val stationEfficiency: List<StationEfficiency> = emptyList()
) {
    val pricePlot = ChartPlot.from(priceTrend)
    val costPlot = ChartPlot.from(runningCost)
    val efficiencyPlot = ChartPlot.from(efficiencyTrend)
    companion object {
        private fun stats(rows: List<Fuel>) = PeriodStats(
            Mileage.spent(rows), Mileage.distance(rows), rows.sumOf { it.litres.toBigDecimal() },
            Mileage.analyticsMileage(rows), rows.size
        )
        // These are the reference's actual screen-specific rules, including History's single-fill fallback.
        fun from(rows: List<Fuel>, month: YearMonth = YearMonth.now()): Insights {
            fun inMonth(m: YearMonth) = rows.filter { YearMonth.from(LocalDate.parse(it.date)) == m }
            val previousRows = inMonth(month.minusMonths(1))
            val previous = stats(previousRows)
            val sorted = Mileage.sorted(rows)
            val previousDistance = if (previousRows.size == 1) {
                val odo = previousRows.single().odometer.toBigDecimal()
                sorted.lastOrNull { it.odometer.toBigDecimal() < odo }?.let { odo - it.odometer.toBigDecimal() } ?: BigDecimal.ZERO
            } else previous.distance
            val chronological = rows.sortedBy { it.date }
            val intervals = chronological.zipWithNext().mapNotNull { (a, b) ->
                val distance = b.odometer.toBigDecimal() - a.odometer.toBigDecimal()
                val litres = b.litres.toBigDecimal()
                if (distance.signum() > 0 && litres.signum() > 0) Triple(b, distance, litres) else null
            }
            val brand = intervals.filter { it.first.station.isNotBlank() && it.first.station != "Unknown" }.groupBy { it.first.station }.map { (station, runs) ->
                StationEfficiency(station, quotient(runs.sumOf { it.second }, runs.sumOf { it.third })!!)
            }
            val span = if (sorted.size < 2) 1L else (ChronoUnit.MONTHS.between(YearMonth.from(LocalDate.parse(sorted.first().date)), YearMonth.from(LocalDate.parse(sorted.last().date))) + 1).coerceAtLeast(1)
            val counts = rows.filter { it.station.isNotBlank() && it.station != "Unknown" }.groupingBy { it.station }.eachCount()
            return Insights(
                month, stats(inMonth(month)), previous,
                previous.copy(distance = previousDistance, mileage = quotient(previousDistance, previous.litres)),
                stats(rows.filter { LocalDate.parse(it.date).year == month.year }), stats(rows),
                Mileage.dashboardMileage(rows), Mileage.distance(rows).toDouble() / span,
                chronological.map { it.pricePerLitre.toDouble() },
                intervals.map { it.first.totalCost.toDouble() / it.second.toDouble() },
                intervals.map { it.second.toDouble() / it.third.toDouble() },
                counts.maxByOrNull { it.value }?.key ?: "Unknown", brand
            )
        }
    }
}
fun quotient(a: BigDecimal, b: BigDecimal): Double? = if (a.signum() > 0 && b.signum() > 0) a.divide(b, 12, RoundingMode.HALF_UP).toDouble() else null
fun currencySymbol(currency: String) = when (currency) { "INR" -> "₹"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; else -> currency }
fun money(amount: BigDecimal, currency: String, decimals: Int = 0) = currencySymbol(currency) + String.format(Locale.ENGLISH, "%,.${decimals}f", amount)
fun shortNumber(amount: BigDecimal) = String.format(Locale.ENGLISH, "%,.0f", amount)

/** Fully prepared on the calculation dispatcher; drawing never scans a large history. */
data class ChartPlot(val samples: List<Double> = emptyList(), val lowest: Double? = null, val highest: Double? = null, val average: Double? = null, val count: Int = 0) {
    companion object {
        fun from(values: List<Double>): ChartPlot {
            if (values.isEmpty()) return ChartPlot()
            val samples = if (values.size <= 120) values.toList() else (0..119).map { values[it * values.lastIndex / 119] }
            return ChartPlot(samples, values.min(), values.max(), values.average(), values.size)
        }
    }
}
