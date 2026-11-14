package com.arindam.mileage

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(data: ScreenData, busy: Boolean, addFuel: () -> Unit, addService: () -> Unit, editFuel: (Fuel) -> Unit, deleteFuel: (Fuel) -> Unit, editService: (Maintenance) -> Unit, deleteService: (Maintenance) -> Unit, filtered: FilteredHistory = FilteredHistory(data.activeVehicle.id, data.fuel, data.maintenance), filter: HistoryFilter = HistoryFilter(), changeFilter: (HistoryFilter) -> Unit = {}) {
    var maintenance by rememberSaveable { mutableStateOf(false) }
    val currency = data.preferences.currency
    val ready = filtered.vehicleId == data.activeVehicle.id
    val fuelRows = if (ready) filtered.fuel else emptyList()
    val serviceRows = if (ready) filtered.maintenance else emptyList()
    val motion = LocalMotionEnabled.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("last-month-summary") {
            val stats = data.insights.historyPrevious
            GlassCard {
                Text("Last Month Summary (${data.insights.month.minusMonths(1).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))})", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                Row {
                    Metric("Spent", money(stats.spent, currency), Modifier.weight(1f), centered = true)
                    Metric("Efficiency", "${formatMileage(stats.mileage ?: 0.0)} km/L", Modifier.weight(1f), tint = MileageGreen, centered = true)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row {
                    Metric("Distance", "${shortNumber(stats.distance)} km", Modifier.weight(1f), centered = true)
                    Metric("Volume", "${formatMileage(stats.litres.toDouble())} L", Modifier.weight(1f), centered = true)
                }
            }
        }
        item("history-tabs") {
            Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(4.dp)) {
                    listOf("Fuel Fillups", "Maintenance").forEachIndexed { index, title ->
                        val isSelected = maintenance == (index == 1)
                        Surface(Modifier.weight(1f).semantics { selected = isSelected }.clickable(role = Role.Tab) { maintenance = index == 1 }, shape = RoundedCornerShape(100.dp), color = if (isSelected) FuelBlue else Color.Transparent) {
                            Box(Modifier.heightIn(min = 48.dp).padding(12.dp), contentAlignment = Alignment.Center) {
                                Text(title, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        item("history-controls") { HistoryControls(data, filter, changeFilter, busy, maintenance) }
        item("result-count") {
            val shown = if (maintenance) serviceRows.size else fuelRows.size
            val total = if (maintenance) data.maintenance.size else data.fuel.size
            Text(if (!ready || filtered.applied != filter) "Updating history…" else "$shown of $total ${if (maintenance) "service records" else "fill-ups"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item("add-history-record") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = if (maintenance) addService else addFuel, enabled = !busy) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (maintenance) "Add Service Log" else "Add Fuel Entry")
                }
            }
        }
        if (ready && filtered.applied == filter && (if (maintenance) serviceRows.isEmpty() && data.maintenance.isNotEmpty() else fuelRows.isEmpty() && data.fuel.isNotEmpty())) item("no-matches") {
            EmptyHistory("No matching records", "Change the search, date or station filter to see more records.", Icons.Outlined.SearchOff, { changeFilter(HistoryFilter()) }, "Clear filters")
        }
        if (maintenance) {
            if (data.maintenance.isEmpty() && data.loaded) item("empty-service") { EmptyHistory("No service logs yet", "Keep track of oil changes, repairs and regular servicing.", Icons.Outlined.Build, addService, "Add Service Log") }
            items(serviceRows, key = { "service-${it.id}" }) { record -> MaintenanceRecordCard(record, currency, busy, { editService(record) }, { deleteService(record) }, Modifier.animateItem(fadeInSpec = if (motion) tween(120) else null, placementSpec = if (motion) tween(180) else null, fadeOutSpec = if (motion) tween(90) else null)) }
        } else {
            if (data.fuel.isEmpty() && data.loaded) item("empty-fuel") { EmptyHistory("No fuel entries yet", "Add a fill-up or import your existing CSV in Settings.", Icons.Outlined.LocalGasStation, addFuel, "Add Fuel Entry") }
            items(fuelRows, key = { "fuel-${it.id}" }) { record -> FuelRecordCard(record, currency, data.perFill[record.id], busy = busy, edit = { editFuel(record) }, delete = { deleteFuel(record) }, reading = data.tanks.readings[record.id], modifier = Modifier.animateItem(fadeInSpec = if (motion) tween(120) else null, placementSpec = if (motion) tween(180) else null, fadeOutSpec = if (motion) tween(90) else null)) }
        }
    }
}
@Composable
fun FuelRecordCard(fuel: Fuel, currency: String, mileage: Double?, modifier: Modifier = Modifier, busy: Boolean = false, edit: (() -> Unit)? = null, delete: (() -> Unit)? = null, onOpen: (() -> Unit)? = null, reading: TankReading? = null) {
    val shownMileage = if (reading != null) reading.value else mileage
    val readingColor = if (reading?.kind == ReadingKind.ESTIMATED) ServiceAmber else MileageGreen
    val readingInk = Color(0xFF102014)
    GlassCard(modifier.then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.Outlined.LocalGasStation, FuelBlue)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(money(fuel.totalCost.toBigDecimal(), currency, if (edit == null) 0 else 2), style = MaterialTheme.typography.titleLarge)
                Text(fuel.station.ifBlank { "Unknown" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(displayDate(fuel.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (shownMileage != null) Surface(color = readingColor, shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatMileage(shownMileage), color = readingInk, style = MaterialTheme.typography.titleMedium)
                    Text(reading?.kind?.label ?: "Per-fill", color = readingInk, style = MaterialTheme.typography.labelSmall)
                    Text("km/L", color = readingInk, style = MaterialTheme.typography.labelSmall)
                }
            } else if (reading != null && reading.kind != ReadingKind.PER_FILL) Badge(reading.kind.label)
        }
        if (edit != null) {
            Text("${fuel.odometer} km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            reading?.let { Text(it.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (fuel.notes.isNotBlank()) Text(fuel.notes, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("${fuel.litres} L @ ${currencySymbol(currency)}${fuel.pricePerLitre}/L", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = edit, enabled = !busy, contentPadding = PaddingValues(horizontal = 16.dp)) { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Edit") }
                IconButton(onClick = { delete?.invoke() }, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "Delete fuel entry", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
@Composable
fun MaintenanceRecordCard(record: Maintenance, currency: String, busy: Boolean, edit: () -> Unit, delete: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.Outlined.Build, ServiceAmber)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(money(record.totalCost.toBigDecimal(), currency, 2), style = MaterialTheme.typography.titleLarge)
                Text(record.serviceType, style = MaterialTheme.typography.titleSmall)
                Text("${displayDate(record.date)} • ${record.odometer} km", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (record.notes.isNotBlank()) Text(record.notes, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = edit, enabled = !busy) { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Edit") }
            IconButton(onClick = delete, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "Delete service record", tint = MaterialTheme.colorScheme.error) }
        }
    }
}
