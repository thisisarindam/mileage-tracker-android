package com.arindam.mileage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditor(kind: String, fuel: Fuel?, service: Maintenance?, busy: Boolean, currency: String, cancel: () -> Unit, saveFuel: (Fuel) -> Unit, saveService: (Maintenance) -> Unit) {
    var date by rememberSaveable { mutableStateOf(fuel?.date ?: service?.date ?: LocalDate.now().toString()) }
    var odo by rememberSaveable { mutableStateOf(fuel?.odometer ?: service?.odometer ?: "") }
    var litres by rememberSaveable { mutableStateOf(fuel?.litres ?: "") }
    var price by rememberSaveable { mutableStateOf(fuel?.pricePerLitre ?: "") }
    var cost by rememberSaveable { mutableStateOf(fuel?.totalCost ?: service?.totalCost ?: "") }
    var title by rememberSaveable { mutableStateOf(fuel?.station ?: service?.serviceType ?: "") }
    var notes by rememberSaveable { mutableStateOf(fuel?.notes ?: service?.notes ?: "") }
    var error by remember { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = runCatching { LocalDate.parse(date).toEpochDay() * 86_400_000 }.getOrNull())
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                date = Instant.ofEpochMilli(picker.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate().toString()
                showDatePicker = false
            }) { Text("Select") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
    fun recalculate() { cost = runCatching { Mileage.newTotal(litres, price) }.getOrDefault("") }
    Dialog(onDismissRequest = { if (!busy) cancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            TopAppBar(title = { Text(if (fuel != null || service != null) "Edit record" else if (kind == "fuel") "Add Fuel Entry" else "Add Service Log") }, navigationIcon = { TextButton(shape = RoundedCornerShape(100.dp), enabled = !busy, onClick = cancel) { Text("Cancel") } })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("All values use kilometres and litres.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Outlined.CalendarMonth, "Choose date") } }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(odo, { odo = it }, label = { Text("Odometer (km)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (kind == "fuel") {
                    OutlinedTextField(litres, { litres = it; recalculate() }, label = { Text("Litres") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(price, { price = it; recalculate() }, label = { Text("Price per litre ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                OutlinedTextField(cost, { cost = it }, readOnly = kind == "fuel", label = { Text("Total cost ($currency)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (kind == "fuel") Text("Calculated to 2 decimals when litres or price change. Imported costs stay unchanged until those fields change.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it }, label = { Text(if (kind == "fuel") "Station (optional)" else "Service type") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
            Button(shape = RoundedCornerShape(100.dp), enabled = !busy, modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = {
                try {
                    if (kind == "fuel") {
                        val f = (fuel ?: Fuel(date = date, odometer = odo, litres = litres, pricePerLitre = price, totalCost = cost)).copy(date = date, odometer = odo, litres = litres, pricePerLitre = price, totalCost = cost, station = title, notes = notes)
                        f.validate(); saveFuel(f)
                    } else {
                        val m = (service ?: Maintenance(date = date, serviceType = title, odometer = odo, totalCost = cost)).copy(date = date, serviceType = title, odometer = odo, totalCost = cost, notes = notes)
                        m.validate(); saveService(m)
                    }
                } catch (e: Exception) { error = e.message ?: "Check the fields" }
            }) { Text(if (fuel != null || service != null) "Update Entry" else if (kind == "fuel") "Save Entry" else "Save Log") }
        } }
    }
}
