package com.arindam.mileage

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MileageScreen() }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MileageScreen(model: AppModel = viewModel(factory = AppModel.Factory)) {
    val data by model.data.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val preview by model.importPreview.collectAsStateWithLifecycle()
    val restore by model.restorePreview.collectAsStateWithLifecycle()
    val filters by model.historyFilter.collectAsStateWithLifecycle()
    val filtered by model.filteredHistory.collectAsStateWithLifecycle()
    var garage by rememberSaveable { mutableStateOf(false) }
    var editorVehicleId by rememberSaveable { mutableStateOf(DEFAULT_VEHICLE_ID) }
    var pickerVehicleId by rememberSaveable { mutableStateOf(DEFAULT_VEHICLE_ID) }
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(busy, data.loaded) {
        showProgress = !data.loaded
        if (busy && data.loaded) { delay(160); showProgress = true }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var chooseEntry by rememberSaveable { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var fuelJson by rememberSaveable { mutableStateOf<String?>(null) }
    var serviceJson by rememberSaveable { mutableStateOf<String?>(null) }
    val editingFuel = remember(fuelJson) { fuelJson?.let { Json.decodeFromString<Fuel>(it) } }
    val editingService = remember(serviceJson) { serviceJson?.let { Json.decodeFromString<Maintenance>(it) } }
    var deletingFuel by remember { mutableStateOf<Fuel?>(null) }
    var deletingService by remember { mutableStateOf<Maintenance?>(null) }
    var inputKind by rememberSaveable { mutableStateOf("fuel") }
    var outputKind by rememberSaveable { mutableStateOf("json") }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.open(it, inputKind, pickerVehicleId) } }
    val jsonSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { model.export(it, "json") } }
    val csvSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { model.export(it, outputKind, pickerVehicleId) } }
    val snackbar = remember { SnackbarHostState() }
    val saveableTabs = rememberSaveableStateHolder()
    val view = LocalView.current
    val focus = LocalFocusManager.current
    LaunchedEffect(message) { if (message.isNotEmpty()) { snackbar.showSnackbar(message); model.message.value = "" } }
    BackHandler(enabled = tab != 0 && editor == null && !chooseEntry && !garage) { tab = 0 }
    fun startFuel(f: Fuel? = null) { editorVehicleId = f?.vehicleId ?: data.activeVehicle.id; fuelJson = f?.let { Json.encodeToString(it) }; serviceJson = null; editor = "fuel"; chooseEntry = false }
    fun startService(m: Maintenance? = null) { editorVehicleId = m?.vehicleId ?: data.activeVehicle.id; serviceJson = m?.let { Json.encodeToString(it) }; fuelJson = null; editor = "maintenance"; chooseEntry = false }
    MileageTheme(data.preferences.darkTheme, motion = !data.preferences.reduceMotion) {
        val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > .45f
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightSystemBars
                isAppearanceLightNavigationBars = lightSystemBars
            }
        }
    }
        ReferenceBackground {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Scaffold(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    topBar = {
                        TopAppBar(
                            title = { Text(listOf("Dashboard", "History", "Insights & Analytics", "Settings")[tab], style = if (tab == 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            navigationIcon = { if (tab == 2) IconButton(onClick = { tab = 0 }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Home") } },
                            actions = {
                                if (tab == 0) {
                                    IconButton(onClick = { model.theme(!data.preferences.darkTheme) }, enabled = !busy) { Icon(Icons.Outlined.Contrast, "Toggle theme") }
                                    Button(onClick = { chooseEntry = true }, enabled = !busy && data.loaded, contentPadding = PaddingValues(horizontal = 14.dp)) {
                                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add Entry")
                                    }
                                    Spacer(Modifier.width(16.dp))
                                }
                            }
                        )
                    },
                    bottomBar = {
                        Surface(color = MaterialTheme.colorScheme.background.copy(alpha = .96f), tonalElevation = 0.dp) {
                            Column {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                                    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.History, Icons.AutoMirrored.Outlined.ShowChart, Icons.Outlined.Settings)
                                    listOf("Home", "History", "Analytics", "Settings").forEachIndexed { i, name ->
                                        NavigationBarItem(modifier = Modifier.semantics { contentDescription = "Navigate $name" }, selected = tab == i, onClick = { focus.clearFocus(); tab = i }, icon = { Icon(icons[i], null) }, label = { Text(name) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = FuelBlue, selectedTextColor = MaterialTheme.colorScheme.onSurface, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant, indicatorColor = Color.Transparent))
                                    }
                                }
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbar) }
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(4.dp)) { if (showProgress) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        VehicleSwitcher(data, busy, model::selectVehicle, { garage = true })
                        val motion = LocalMotionEnabled.current
                        AnimatedContent(targetState = tab, modifier = Modifier.weight(1f).clipToBounds(), label = "screen transition", transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(if (motion) 180 else 0)) + slideInHorizontally(tween(if (motion) 180 else 0)) { direction * it / 18 }) togetherWith
                                (fadeOut(tween(if (motion) 100 else 0)) + slideOutHorizontally(tween(if (motion) 140 else 0)) { -direction * it / 24 })
                        }) { screen ->
                            saveableTabs.SaveableStateProvider(screen) {
                                val inactive = screen != tab
                                when (screen) {
                                    0 -> DashboardScreen(data, { if (!inactive) chooseEntry = true }, { if (!inactive) { model.clearFilters(); tab = 1 } })
                                    1 -> HistoryScreen(data, busy || inactive, { if (!inactive) startFuel() }, { if (!inactive) startService() }, { if (!inactive) startFuel(it) }, { if (!inactive) deletingFuel = it }, { if (!inactive) startService(it) }, { if (!inactive) deletingService = it }, filtered, filters, model::filter)
                                    2 -> AnalyticsScreen(data)
                                    else -> SettingsScreen(data, busy || inactive, { if (!inactive) model.currency(it) }, { if (!inactive) model.theme(it) },
                                        open = { if (!inactive) { inputKind = it; pickerVehicleId = data.activeVehicle.id; open.launch(arrayOf("*/*")) } },
                                        export = { kind -> if (!inactive) {
                                            pickerVehicleId = data.activeVehicle.id
                                            if (kind == "json") jsonSave.launch("mileage-tracker-backup.json") else { outputKind = kind; csvSave.launch(if (kind == "fuel") "fuel_entries_rows.csv" else "maintenance_logs_rows.csv") }
                                        } }, reduceMotion = { if (!inactive) model.reduceMotion(it) }, garage = { if (!inactive) garage = true })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (garage) VehicleManager(data, busy, { garage = false }, model::selectVehicle, model::saveVehicle, model::deleteVehicle)
        if (chooseEntry) ModalBottomSheet(onDismissRequest = { chooseEntry = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Add Entry", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(false, true).forEach { service ->
                        Surface(Modifier.weight(1f).clickable { if (service) startService() else startFuel() }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconTile(if (service) Icons.Outlined.Build else Icons.Outlined.LocalGasStation, if (service) ServiceAmber else FuelBlue, 56)
                                Text(if (service) "Maintenance" else "Fuel Fill-up", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        editor?.let { kind ->
            RecordEditor(kind, editingFuel, editingService, busy, data.preferences.currency,
                cancel = { editor = null }, saveFuel = { model.save(it, editingFuel != null) { editor = null } }, saveService = { model.save(it, editingService != null) { editor = null } },
                vehicleId = editorVehicleId, vehicleName = data.vehicles.firstOrNull { it.vehicle.id == editorVehicleId }?.vehicle?.name ?: "Vehicle",
                lastFuel = data.lastFuel?.takeIf { it.vehicleId == editorVehicleId })
        }
        if (deletingFuel != null || deletingService != null) AlertDialog(
            onDismissRequest = { if (!busy) { deletingFuel = null; deletingService = null } }, title = { Text("Delete record?") },
            text = { DialogMotion(); Text("This cannot be undone. Save a JSON backup first if you need to keep it.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { deletingFuel?.let(model::delete); deletingService?.let(model::delete); deletingFuel = null; deletingService = null }) { Text("Delete") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deletingFuel = null; deletingService = null }) { Text("Cancel") } }
        )
        preview?.let { p ->
            AlertDialog(onDismissRequest = { if (!busy) model.importPreview.value = null }, title = { Text("CSV import preview") }, text = {
                DialogMotion()
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item { Text("Vehicle: ${p.vehicleName}\n${p.ready} ready • ${p.duplicates} already imported (including repeated IDs in this file) • ${p.errors.size} invalid\nOnly valid, new rows will be saved. Local edits will be preserved.") }
                    items(p.errors, key = { it.line }) { Text("Row ${it.line}: ${it.reason}", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = { TextButton(enabled = p.ready > 0 && !busy, onClick = model::confirmImport) { Text("Import ${p.ready} records") } }, dismissButton = { TextButton(enabled = !busy, onClick = { model.importPreview.value = null }) { Text("Cancel") } })
        }
        restore?.let { b ->
            AlertDialog(onDismissRequest = { if (!busy) model.restorePreview.value = null }, title = { Text("Replace all local data?") }, text = {
                DialogMotion()
                Text("Validated backup v${b.version}: ${b.vehicles.size} vehicles, ${b.fuel.size} fill-ups, ${b.maintenance.size} services, ${b.preferences.currency}, metric.\n\nThis replaces ${data.totalFuelCount} fill-ups, ${data.totalMaintenanceCount} services across all ${data.vehicles.size} vehicles, and your preferences. Empty histories in the backup will clear those histories.")
            }, confirmButton = { TextButton(enabled = !busy, onClick = model::confirmRestore) { Text("Replace and restore") } }, dismissButton = { TextButton(enabled = !busy, onClick = { model.restorePreview.value = null }) { Text("Cancel") } })
        }
    }
}
