package com.arindam.mileage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryControls(data: ScreenData, filter: HistoryFilter, change: (HistoryFilter) -> Unit, busy: Boolean, maintenance: Boolean) {
    val focus = LocalFocusManager.current
    var sheet by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(filter.query, { change(filter.copy(query = it)) }, label = { Text("Search history") }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (filter.query.isNotEmpty()) IconButton(onClick = { change(filter.copy(query = "")) }) { Icon(Icons.Outlined.Close, "Clear search") } }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { focus.clearFocus(); sheet = true }, enabled = !busy) { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Filter & Sort") }
            if (filter.active) TextButton(onClick = { change(HistoryFilter()) }) { Text("Clear filters") }
        }
        if (filter.station != null && !maintenance) Text("Station: ${filter.station.ifBlank { "Unknown" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (filter.from.isNotEmpty() || filter.to.isNotEmpty()) Text("${filter.from.ifBlank { "Any date" }} → ${filter.to.ifBlank { "Any date" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (sheet) {
        var draft by remember { mutableStateOf(filter) }
        var stations by remember { mutableStateOf(false) }
        var dateField by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Filter & Sort", style = MaterialTheme.typography.titleLarge)
                if (!maintenance) OutlinedButton(onClick = { stations = true }, modifier = Modifier.fillMaxWidth()) { Text(draft.station?.let { "Station: ${it.ifBlank { "Unknown" }}" } ?: "All stations") }
                Text("Date range", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { dateField = "from" }, modifier = Modifier.weight(1f)) { Text(draft.from.ifBlank { "From date" }) }
                    OutlinedButton(onClick = { dateField = "to" }, modifier = Modifier.weight(1f)) { Text(draft.to.ifBlank { "To date" }) }
                }
                if (draft.from.isNotEmpty() || draft.to.isNotEmpty()) TextButton(onClick = { draft = draft.copy(from = "", to = "") }) { Text("Clear dates") }
                HistorySort.entries.forEach { order ->
                    Row(Modifier.fillMaxWidth().clickable { draft = draft.copy(sort = order) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = draft.sort == order, onClick = { draft = draft.copy(sort = order) }); Text(order.label)
                    }
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { try { draft.validate(); change(draft); sheet = false } catch (e: Exception) { error = e.message ?: "Check date range" } }, modifier = Modifier.fillMaxWidth()) { Text("Apply filters") }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (stations) AlertDialog(onDismissRequest = { stations = false }, title = { Text("Choose station") }, text = {
            DialogMotion()
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item("all") { TextButton(onClick = { draft = draft.copy(station = null); stations = false }) { Text("All stations") } }
                items(data.stations, key = { "station:$it" }) { station -> TextButton(onClick = { draft = draft.copy(station = station); stations = false }, modifier = Modifier.fillMaxWidth()) { Text(station.ifBlank { "Unknown" }) } }
            }
        }, confirmButton = { TextButton(onClick = { stations = false }) { Text("Cancel") } })
        dateField?.let { field ->
            val date = if (field == "from") draft.from else draft.to
            val picker = rememberDatePickerState(initialSelectedDateMillis = runCatching { LocalDate.parse(date).toEpochDay() * 86_400_000 }.getOrNull())
            DatePickerDialog(onDismissRequest = { dateField = null }, confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                val selected = Instant.ofEpochMilli(picker.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate().toString()
                draft = if (field == "from") draft.copy(from = selected) else draft.copy(to = selected)
                dateField = null
            }) { Text("Select") } }, dismissButton = { TextButton(onClick = { dateField = null }) { Text("Cancel") } }) { DialogMotion(); DatePicker(picker) }
        }
    }
}
