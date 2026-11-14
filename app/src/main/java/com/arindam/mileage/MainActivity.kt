package com.arindam.mileage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme = lightColorScheme()) { MileageScreen() } } }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MileageScreen(model: AppModel = viewModel()) {
    val data by model.data.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val preview by model.importPreview.collectAsStateWithLifecycle()
    val restore by model.restorePreview.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<String?>(null) }
    var editingFuel by remember { mutableStateOf<Fuel?>(null) }
    var editingService by remember { mutableStateOf<Maintenance?>(null) }
    var deletingFuel by remember { mutableStateOf<Fuel?>(null) }
    var deletingService by remember { mutableStateOf<Maintenance?>(null) }
    var inputKind by rememberSaveable { mutableStateOf("fuel") }
    var outputKind by rememberSaveable { mutableStateOf("json") }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.open(it, inputKind) } }
    val jsonSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { model.export(it, "json") } }
    val csvSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { model.export(it, outputKind) } }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { if (message.isNotEmpty()) { snackbar.showSnackbar(message); model.message.value = "" } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Mileage Tracker") }) },
        bottomBar = { NavigationBar { listOf("Fuel", "Maintenance", "Settings").forEachIndexed { i, name -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(listOf("F", "M", "S")[i]) }, label = { Text(name) }) } } },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { if (tab < 2 && data.loaded) FloatingActionButton(modifier = Modifier.semantics { contentDescription = if (tab == 0) "Add fill-up" else "Add service" }, onClick = { if (!busy) { editingFuel = null; editingService = null; editor = if (tab == 0) "fuel" else "maintenance" } }) { Text("+", style = MaterialTheme.typography.headlineMedium) } }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy || !data.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Your fuel overview", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); Text(data.summary) } } }
                    if (data.fuel.isEmpty() && data.loaded) item { Text("No fill-ups yet. Tap + to log fuel, or import a CSV in Settings.") }
                    items(data.fuel, key = { it.id }) { f ->
                        RecordCard(title = "${data.preferences.currency} ${f.totalCost} • ${f.station.ifBlank { "Fill-up" }}", details = "${f.date} • ${f.odometer} km\n${f.litres} L @ ${f.pricePerLitre}/L\nMileage: ${formatMileage(data.perFill[f.id])} km/L", notes = f.notes, created = f.createdAt, enabled = !busy,
                            edit = { editingFuel = f; editor = "fuel" }, delete = { deletingFuel = f })
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("Service history", style = MaterialTheme.typography.headlineSmall) }
                    if (data.maintenance.isEmpty() && data.loaded) item { Text("No service records. Tap + to add one.") }
                    items(data.maintenance, key = { it.id }) { m -> RecordCard("${m.serviceType} • ${data.preferences.currency} ${m.totalCost}", "${m.date} • ${m.odometer} km", m.notes, m.createdAt, !busy, { editingService = m; editor = "maintenance" }, { deletingService = m }) }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Local preferences", style = MaterialTheme.typography.titleLarge)
                    Text("Kilometres • litres. Data stays on this device. No account or automatic cloud backup.")
                    Text("Currency: ${data.preferences.currency}")
                    Row { listOf("INR", "USD", "EUR", "GBP").forEach { code -> TextButton(onClick = { model.currency(code) }, enabled = !busy) { Text(code) } } }
                    Text("Changing currency changes the label only; amounts are never converted.", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("Import & Backup", style = MaterialTheme.typography.titleLarge)
                    Text("Choose locally downloaded files for airplane mode. CSV headers are matched automatically; filenames do not matter. Limit: 20 MiB per opened file.")
                    Button(onClick = { inputKind = "fuel"; open.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Import Fuel CSV") }
                    Button(onClick = { inputKind = "maintenance"; open.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Import Maintenance CSV") }
                    HorizontalDivider()
                    Text("JSON • full backup", style = MaterialTheme.typography.titleMedium)
                    Text("Includes both histories, preferences, and original import IDs. Restore replaces everything after confirmation.")
                    Button(onClick = { jsonSave.launch("mileage-tracker-backup.json") }, enabled = !busy) { Text("Save full JSON backup") }
                    OutlinedButton(onClick = { inputKind = "json"; open.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Restore full JSON backup") }
                    HorizontalDivider()
                    Text("CSV • per-history spreadsheet exports", style = MaterialTheme.typography.titleMedium)
                    Text("CSV is not a full backup. It does not contain preferences or local record identity.")
                    OutlinedButton(onClick = { outputKind = "fuel"; csvSave.launch("fuel_entries_rows.csv") }, enabled = !busy) { Text("Export fuel history CSV") }
                    OutlinedButton(onClick = { outputKind = "maintenance"; csvSave.launch("maintenance_logs_rows.csv") }, enabled = !busy) { Text("Export maintenance history CSV") }
                }
            }
        }
    }
    editor?.let { kind -> RecordEditor(kind, editingFuel, editingService, busy, data.preferences.currency, cancel = { editor = null }, saveFuel = { model.save(it, editingFuel != null) { editor = null } }, saveService = { model.save(it, editingService != null) { editor = null } }) }
    if (deletingFuel != null || deletingService != null) AlertDialog(onDismissRequest = { if (!busy) { deletingFuel = null; deletingService = null } }, title = { Text("Delete record?") }, text = { Text("This cannot be undone. Save a JSON backup first if you need to keep it.") }, confirmButton = { TextButton(enabled = !busy, onClick = { deletingFuel?.let(model::delete); deletingService?.let(model::delete); deletingFuel = null; deletingService = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deletingFuel = null; deletingService = null }) { Text("Cancel") } })
    preview?.let { p -> AlertDialog(onDismissRequest = { if (!busy) model.importPreview.value = null }, title = { Text("CSV import preview") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item { Text("${p.ready} ready • ${p.duplicates} already imported (including repeated IDs in this file) • ${p.errors.size} invalid\nOnly valid, new rows will be saved. Local edits will be preserved.") }
            items(p.errors, key = { it.line }) { Text("Row ${it.line}: ${it.reason}", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = p.ready > 0 && !busy, onClick = model::confirmImport) { Text("Import ${p.ready} records") } }, dismissButton = { TextButton(enabled = !busy, onClick = { model.importPreview.value = null }) { Text("Cancel") } }) }
    restore?.let { b -> AlertDialog(onDismissRequest = { if (!busy) model.restorePreview.value = null }, title = { Text("Replace all local data?") }, text = { Text("Validated backup v${b.version}: ${b.fuel.size} fill-ups, ${b.maintenance.size} services, ${b.preferences.currency}, metric.\n\nThis replaces ${data.fuel.size} fill-ups, ${data.maintenance.size} services and your preferences. Save a backup first. Empty histories in the backup will clear those histories.") }, confirmButton = { TextButton(enabled = !busy, onClick = model::confirmRestore) { Text("Replace and restore") } }, dismissButton = { TextButton(enabled = !busy, onClick = { model.restorePreview.value = null }) { Text("Cancel") } }) }
}

@Composable
private fun RecordCard(title: String, details: String, notes: String, created: String, enabled: Boolean, edit: () -> Unit, delete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(details)
        if (notes.isNotBlank()) Text(notes)
        Text("Created: $created", style = MaterialTheme.typography.bodySmall)
        Row { TextButton(enabled = enabled, onClick = edit) { Text("Edit") }; TextButton(enabled = enabled, onClick = delete) { Text("Delete") } }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditor(kind: String, fuel: Fuel?, service: Maintenance?, busy: Boolean, currency: String, cancel: () -> Unit, saveFuel: (Fuel) -> Unit, saveService: (Maintenance) -> Unit) {
    var date by rememberSaveable { mutableStateOf(fuel?.date ?: service?.date ?: LocalDate.now().toString()) }
    var odo by rememberSaveable { mutableStateOf(fuel?.odometer ?: service?.odometer ?: "") }
    var litres by rememberSaveable { mutableStateOf(fuel?.litres ?: "") }
    var price by rememberSaveable { mutableStateOf(fuel?.pricePerLitre ?: "") }
    var cost by rememberSaveable { mutableStateOf(fuel?.totalCost ?: service?.totalCost ?: "") }
    var title by rememberSaveable { mutableStateOf(fuel?.station ?: service?.serviceType ?: "") }
    var notes by rememberSaveable { mutableStateOf(fuel?.notes ?: service?.notes ?: "") }
    var error by remember { mutableStateOf("") }
    fun recalculate() { cost = runCatching { Mileage.newTotal(litres, price) }.getOrDefault("") }
    Dialog(onDismissRequest = { if (!busy) cancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            TopAppBar(title = { Text(if (fuel != null || service != null) "Edit record" else if (kind == "fuel") "Log a fill-up" else "Log service") }, navigationIcon = { TextButton(enabled = !busy, onClick = cancel) { Text("Cancel") } })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Date and creation time are stored separately. All values use km and litres.")
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = {
                try {
                    if (kind == "fuel") {
                        val f = (fuel ?: Fuel(date = date, odometer = odo, litres = litres, pricePerLitre = price, totalCost = cost)).copy(date = date, odometer = odo, litres = litres, pricePerLitre = price, totalCost = cost, station = title, notes = notes)
                        f.validate(); saveFuel(f)
                    } else {
                        val m = (service ?: Maintenance(date = date, serviceType = title, odometer = odo, totalCost = cost)).copy(date = date, serviceType = title, odometer = odo, totalCost = cost, notes = notes)
                        m.validate(); saveService(m)
                    }
                } catch (e: Exception) { error = e.message ?: "Check the fields" }
            }) { Text("Save record") }
        } }
    }
}
