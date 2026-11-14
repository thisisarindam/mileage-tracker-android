package com.arindam.mileage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun VehicleSwitcher(data: ScreenData, busy: Boolean, select: (String) -> Unit, manage: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { expanded = true }, enabled = !busy && data.loaded) {
                Icon(Icons.Outlined.DirectionsCar, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(data.activeVehicle.name, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Outlined.ExpandMore, "Select vehicle", Modifier.size(20.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                data.vehicles.forEach { summary ->
                    DropdownMenuItem(text = { Text(summary.vehicle.name) }, onClick = { select(summary.vehicle.id); expanded = false },
                        trailingIcon = { if (summary.vehicle.id == data.activeVehicle.id) Icon(Icons.Outlined.Check, "Selected") })
                }
            }
        }
        TextButton(onClick = manage, enabled = !busy && data.loaded) { Text("Garage") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleManager(data: ScreenData, busy: Boolean, dismiss: () -> Unit, select: (String) -> Unit, save: (Vehicle, Boolean, () -> Unit) -> Unit, delete: (Vehicle) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var vehicleJson by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Vehicle?>(null) }
    val vehicle = remember(vehicleJson) { vehicleJson?.let { Json.decodeFromString<Vehicle>(it) } }
    if (!editing) Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogMotion()
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                TopAppBar(title = { Text("My Vehicles") }, navigationIcon = { IconButton(onClick = dismiss, enabled = !busy) { Icon(Icons.Outlined.Close, "Close garage") } }, actions = {
                    IconButton(onClick = { vehicleJson = null; editing = true }, enabled = !busy) { Icon(Icons.Outlined.Add, "Add vehicle") }
                })
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item("info") { Text("Each vehicle has its own fuel, service history and analytics. Switching vehicles never moves records.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(data.vehicles, key = { it.vehicle.id }) { summary ->
                        val v = summary.vehicle
                        GlassCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconTile(Icons.Outlined.DirectionsCar, FuelBlue)
                                Column(Modifier.weight(1f)) {
                                    Text(v.name, style = MaterialTheme.typography.titleMedium)
                                    if (v.model.isNotBlank()) Text(v.model, style = MaterialTheme.typography.bodySmall)
                                    if (v.registration.isNotBlank()) Text(v.registration, style = MaterialTheme.typography.bodySmall)
                                    Text("${summary.fuelCount} fill-ups · ${summary.maintenanceCount} services", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { select(v.id); dismiss() }, enabled = !busy && v.id != data.activeVehicle.id) { Text(if (v.id == data.activeVehicle.id) "Active" else "Select") }
                                IconButton(onClick = { vehicleJson = Json.encodeToString(v); editing = true }, enabled = !busy) { Icon(Icons.Outlined.Edit, "Edit ${v.name}") }
                                IconButton(onClick = { deleting = v }, enabled = !busy && data.vehicles.size > 1 && summary.fuelCount == 0 && summary.maintenanceCount == 0) { Icon(Icons.Outlined.DeleteOutline, "Delete ${v.name}", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    item("protection") { Text("A vehicle can only be deleted when it has no records, and at least one vehicle must remain.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    if (editing) {
        var name by rememberSaveable(vehicleJson) { mutableStateOf(vehicle?.name ?: "") }
        var model by rememberSaveable(vehicleJson) { mutableStateOf(vehicle?.model ?: "") }
        var registration by rememberSaveable(vehicleJson) { mutableStateOf(vehicle?.registration ?: "") }
        var error by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { if (!busy) editing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            DialogMotion()
            Surface(Modifier.fillMaxSize()) { Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                TopAppBar(title = { Text(if (vehicle == null) "Add Vehicle" else "Edit Vehicle") }, navigationIcon = {
                    TextButton(onClick = { editing = false }, enabled = !busy) { Text("Cancel") }
                })
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text("Vehicle name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(model, { if (it.length <= 80) model = it }, label = { Text("Model (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(registration, { if (it.length <= 32) registration = it }, label = { Text("Registration (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                }
                Button(modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = {
                    try {
                        val v = (vehicle ?: Vehicle(name = name.trim())).copy(name = name.trim(), model = model.trim(), registration = registration.trim())
                        v.validate(); save(v, vehicle != null) { editing = false }
                    } catch (e: Exception) { error = e.message ?: "Check vehicle details" }
                }, enabled = !busy) { Text("Save Vehicle") }
            } }
        }
    }
    deleting?.let { v -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove ${v.name}?") }, text = { DialogMotion(); Text("This empty vehicle will be removed. No records will be deleted.") },
        confirmButton = { TextButton(onClick = { delete(v); deleting = null }, enabled = !busy) { Text("Remove") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}
