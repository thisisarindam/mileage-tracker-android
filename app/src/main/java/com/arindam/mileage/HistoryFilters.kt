package com.arindam.mileage

import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

enum class HistorySort(val label: String) { NEWEST("Newest first"), OLDEST("Oldest first"), HIGHEST_COST("Highest cost") }
data class HistoryFilter(val query: String = "", val station: String? = null, val from: String = "", val to: String = "", val sort: HistorySort = HistorySort.NEWEST) {
    fun validate() {
        if (from.isNotEmpty()) LocalDate.parse(from)
        if (to.isNotEmpty()) LocalDate.parse(to)
        require(from.isEmpty() || to.isEmpty() || from <= to) { "Start date must not be after end date" }
    }
    val active get() = query.isNotBlank() || station != null || from.isNotEmpty() || to.isNotEmpty() || sort != HistorySort.NEWEST
}
data class IndexedRecord<T>(val record: T, val date: String, val amount: BigDecimal, val odometer: BigDecimal, val text: String, val station: String = "", val id: String)
data class HistoryIndex(val fuel: List<IndexedRecord<Fuel>> = emptyList(), val maintenance: List<IndexedRecord<Maintenance>> = emptyList()) {
    companion object {
        fun prepare(fuel: List<Fuel>, maintenance: List<Maintenance>) = HistoryIndex(
            fuel.map { IndexedRecord(it, it.date, it.totalCost.toBigDecimal(), it.odometer.toBigDecimal(), listOf(it.station, it.notes, it.date, it.odometer, it.totalCost).joinToString(" ").lowercase(Locale.ROOT), it.station, it.id) },
            maintenance.map { IndexedRecord(it, it.date, it.totalCost.toBigDecimal(), it.odometer.toBigDecimal(), listOf(it.serviceType, it.notes, it.date, it.odometer, it.totalCost).joinToString(" ").lowercase(Locale.ROOT), id = it.id) }
        )
    }
    private fun <T> orders(rows: List<IndexedRecord<T>>): Map<HistorySort, List<IndexedRecord<T>>> = HistorySort.entries.associateWith { order ->
        val comparator = when (order) {
            HistorySort.NEWEST -> compareByDescending<IndexedRecord<T>> { it.date }.thenByDescending { it.odometer }.thenBy { it.id }
            HistorySort.OLDEST -> compareBy<IndexedRecord<T>> { it.date }.thenBy { it.odometer }.thenBy { it.id }
            HistorySort.HIGHEST_COST -> compareByDescending<IndexedRecord<T>> { it.amount }.thenByDescending { it.date }.thenBy { it.id }
        }
        rows.sortedWith(comparator)
    }
    private val fuelOrders by lazy { orders(fuel) }
    private val serviceOrders by lazy { orders(maintenance) }
    fun filter(filter: HistoryFilter, vehicleId: String): FilteredHistory {
        filter.validate()
        val needle = filter.query.trim().lowercase(Locale.ROOT)
        fun <T> apply(rows: List<IndexedRecord<T>>, station: Boolean): List<T> = rows.asSequence().filter {
            (needle.isEmpty() || it.text.contains(needle)) && (!station || filter.station == null || it.station == filter.station) &&
                (filter.from.isEmpty() || it.date >= filter.from) && (filter.to.isEmpty() || it.date <= filter.to)
        }.map { it.record }.toList()
        return FilteredHistory(vehicleId, apply(fuelOrders.getValue(filter.sort), true), apply(serviceOrders.getValue(filter.sort), false), filter)
    }
}
data class FilteredHistory(val vehicleId: String = DEFAULT_VEHICLE_ID, val fuel: List<Fuel> = emptyList(), val maintenance: List<Maintenance> = emptyList(), val applied: HistoryFilter = HistoryFilter())
