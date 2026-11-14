package com.arindam.mileage

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MileageApp : Application() {
    val store by lazy { Store(Room.databaseBuilder(this, MileageDatabase::class.java, "mileage.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(DEFAULT_VEHICLE_CALLBACK).build()) }
}
data class VehicleSummary(val vehicle: Vehicle, val fuelCount: Int = 0, val maintenanceCount: Int = 0)
data class ScreenData(
    val fuel: List<Fuel> = emptyList(), val maintenance: List<Maintenance> = emptyList(),
    val preferences: Preferences = Preferences(), val perFill: Map<String, Double?> = emptyMap(),
    val insights: Insights = Insights(), val loaded: Boolean = false,
    val vehicles: List<VehicleSummary> = listOf(VehicleSummary(Vehicle.default())),
    val activeVehicle: Vehicle = Vehicle.default(), val tanks: TankSummary = TankSummary(),
    val index: HistoryIndex = HistoryIndex(), val stations: List<String> = emptyList(), val lastFuel: Fuel? = null,
    val totalFuelCount: Int = 0, val totalMaintenanceCount: Int = 0
)
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppModel(app: Application) : AndroidViewModel(app) {
    companion object {
        val Factory = viewModelFactory { initializer { AppModel(checkNotNull(this[APPLICATION_KEY])) } }
    }
    private val store = (app as MileageApp).store
    private val preferences = store.dao.watchPreferences().map { it ?: Preferences() }.distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
    // Room relations read all histories in one transaction. Theme/currency changes never rebuild analytics or search indexes.
    private val prepared = combine(store.dao.watchGarage(), preferences.map { it.activeVehicleId }.distinctUntilChanged()) { garage, id -> garage to id }
        .mapLatest { (garage, id) ->
            currentCoroutineContext().ensureActive()
            val selected = garage.firstOrNull { it.vehicle.id == id } ?: garage.firstOrNull() ?: return@mapLatest ScreenData()
            val index = HistoryIndex.prepare(selected.fuel, selected.maintenance)
            val insights = Insights.from(selected.fuel)
            currentCoroutineContext().ensureActive()
            ScreenData(
                fuel = Mileage.sorted(selected.fuel).reversed(), maintenance = selected.maintenance.sortedWith(compareByDescending<Maintenance> { it.date }.thenBy { it.id }),
                perFill = Mileage.perFill(selected.fuel), insights = insights, loaded = true,
                vehicles = garage.map { VehicleSummary(it.vehicle, it.fuel.size, it.maintenance.size) }, activeVehicle = selected.vehicle,
                tanks = TankMileage.calculate(selected.fuel), index = index, stations = selected.fuel.map { it.station }.distinct().sorted(),
                lastFuel = index.fuel.maxWithOrNull(compareBy<IndexedRecord<Fuel>> { it.date }.thenBy { it.odometer }.thenBy { it.id })?.record,
                totalFuelCount = garage.sumOf { it.fuel.size }, totalMaintenanceCount = garage.sumOf { it.maintenance.size }
            )
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenData())
    val data = combine(prepared, preferences) { content, prefs ->
        content.copy(preferences = prefs.copy(activeVehicleId = content.activeVehicle.id))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenData())
    val historyFilter = MutableStateFlow(HistoryFilter())
    val filteredHistory = combine(prepared, historyFilter.debounce(120).distinctUntilChanged()) { content, filter -> content to filter }
        .mapLatest { (content, filter) -> content.index.filter(filter, content.activeVehicle.id) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilteredHistory())
    fun filter(filter: HistoryFilter) { filter.validate(); historyFilter.value = filter }
    fun clearFilters() { historyFilter.value = HistoryFilter() }
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
    val importPreview = MutableStateFlow<ImportPreview?>(null)
    val restorePreview = MutableStateFlow<Backup?>(null)
    private fun task(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = e.message ?: "Operation failed" }
            finally { busy.value = false }
        }
    }
    fun save(f: Fuel, editing: Boolean, done: () -> Unit) = task {
        store.save(f, editing)
        message.value = "Fuel saved"; withContext(Dispatchers.Main) { done() }
    }
    fun save(m: Maintenance, editing: Boolean, done: () -> Unit) = task {
        store.save(m, editing)
        message.value = "Service saved"; withContext(Dispatchers.Main) { done() }
    }
    fun delete(f: Fuel) = task { store.dao.delete(f); message.value = "Fill-up deleted" }
    fun delete(m: Maintenance) = task { store.dao.delete(m); message.value = "Service deleted" }
    fun currency(currency: String) = task { store.updatePreferences { it.copy(currency = currency) }; message.value = "Settings saved" }
    fun theme(dark: Boolean) = task { store.updatePreferences { it.copy(darkTheme = dark) } }
    fun reduceMotion(reduce: Boolean) = task { store.updatePreferences { it.copy(reduceMotion = reduce) } }
    fun selectVehicle(id: String) = task { store.selectVehicle(id); clearFilters() }
    fun saveVehicle(vehicle: Vehicle, editing: Boolean, done: () -> Unit) = task {
        store.saveVehicle(vehicle, editing); clearFilters(); message.value = "Vehicle saved"; withContext(Dispatchers.Main) { done() }
    }
    fun deleteVehicle(vehicle: Vehicle) = task { store.deleteVehicle(vehicle); clearFilters(); message.value = "Vehicle removed" }
    private fun read(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return requireNotNull(resolver.openInputStream(uri)) { "Cannot open file" }.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = stream.read(buffer)
                if (n == -1) break
                require(output.size() + n <= 20 * 1024 * 1024) { "File exceeds 20 MiB limit" }
                output.write(buffer, 0, n)
            }
            val bytes = output.toByteArray()
            require(bytes.size <= 20 * 1024 * 1024) { "File exceeds 20 MiB limit" }
            java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }
    }
    fun open(uri: Uri, kind: String, vehicleId: String = data.value.activeVehicle.id) = task {
        val text = read(uri)
        if (kind == "json") restorePreview.value = Backups.decode(text)
        else {
            val vehicle = store.dao.vehicles().firstOrNull { it.id == vehicleId } ?: error("Vehicle no longer exists")
            val sources = if (kind == "fuel") store.dao.fuelImportKeys() else store.dao.maintenanceImportKeys()
            importPreview.value = Imports.preview(text, kind == "fuel", sources.toSet(), vehicleId, vehicle.name)
        }
    }
    fun confirmImport() = task {
        val p = importPreview.value ?: return@task
        val count = store.import(p); importPreview.value = null
        message.value = "$count records imported; existing records preserved"
    }
    fun confirmRestore() = task {
        val b = restorePreview.value ?: return@task
        store.restore(b); clearFilters(); restorePreview.value = null; message.value = "Full backup restored"
    }
    fun export(uri: Uri, kind: String, vehicleId: String = data.value.activeVehicle.id) = task {
        val text = if (kind == "json") Backups.encode(store.snapshot()) else {
            require(store.dao.vehicles().any { it.id == vehicleId }) { "Vehicle no longer exists" }
            if (kind == "fuel") Imports.exportFuel(store.dao.fuelForVehicle(vehicleId)) else Imports.exportMaintenance(store.dao.maintenanceForVehicle(vehicleId))
        }
        requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")) { "Cannot write file" }.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        message.value = "Export saved"
    }
}
fun formatMileage(n: Double?) = n?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "—"
