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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MileageApp : Application() {
    val store by lazy { Store(Room.databaseBuilder(this, MileageDatabase::class.java, "mileage.db").addMigrations(MIGRATION_1_2).build()) }
}
data class ScreenData(
    val fuel: List<Fuel> = emptyList(),
    val maintenance: List<Maintenance> = emptyList(),
    val preferences: Preferences = Preferences(),
    val perFill: Map<String, Double?> = emptyMap(),
    val insights: Insights = Insights(),
    val loaded: Boolean = false
)
class AppModel(app: Application) : AndroidViewModel(app) {
    companion object {
        val Factory = viewModelFactory {
            initializer { AppModel(checkNotNull(this[APPLICATION_KEY])) }
        }
    }
    private val store = (app as MileageApp).store
    val data = combine(store.dao.watchFuel(), store.dao.watchMaintenance(), store.dao.watchPreferences()) { f, m, p ->
        ScreenData(Mileage.sorted(f).reversed(), m, p ?: Preferences(), Mileage.perFill(f), Insights.from(f), true)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenData())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
    val importPreview = MutableStateFlow<ImportPreview?>(null)
    val restorePreview = MutableStateFlow<Backup?>(null)
    private fun task(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: Exception) { message.value = e.message ?: "Operation failed" }
            finally { busy.value = false }
        }
    }
    fun save(f: Fuel, editing: Boolean, done: () -> Unit) = task {
        f.validate(); if (editing) store.dao.update(f) else store.dao.add(f)
        message.value = "Fuel saved"; withContext(Dispatchers.Main) { done() }
    }
    fun save(m: Maintenance, editing: Boolean, done: () -> Unit) = task {
        m.validate(); if (editing) store.dao.update(m) else store.dao.add(m)
        message.value = "Service saved"; withContext(Dispatchers.Main) { done() }
    }
    fun delete(f: Fuel) = task { store.dao.delete(f); message.value = "Fill-up deleted" }
    fun delete(m: Maintenance) = task { store.dao.delete(m); message.value = "Service deleted" }
    fun currency(currency: String) = task { val p = (store.dao.preferences() ?: Preferences()).copy(currency = currency); p.validate(); store.dao.setPreferences(p); message.value = "Settings saved" }
    fun theme(dark: Boolean) = task { store.dao.setPreferences((store.dao.preferences() ?: Preferences()).copy(darkTheme = dark)) }
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
    fun open(uri: Uri, kind: String) = task {
        val text = read(uri)
        if (kind == "json") restorePreview.value = Backups.decode(text)
        else {
            val sources = if (kind == "fuel") store.dao.fuel().mapNotNull { it.sourceId } else store.dao.maintenance().mapNotNull { it.sourceId }
            importPreview.value = Imports.preview(text, kind == "fuel", sources.toSet())
        }
    }
    fun confirmImport() = task {
        val p = importPreview.value ?: return@task
        val count = store.import(p); importPreview.value = null
        message.value = "$count records imported; existing records preserved"
    }
    fun confirmRestore() = task {
        val b = restorePreview.value ?: return@task
        store.restore(b); restorePreview.value = null; message.value = "Full backup restored"
    }
    fun export(uri: Uri, kind: String) = task {
        val snapshot = store.snapshot()
        val text = when (kind) { "fuel" -> Imports.exportFuel(snapshot.fuel); "maintenance" -> Imports.exportMaintenance(snapshot.maintenance); else -> Backups.encode(snapshot) }
        requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")) { "Cannot write file" }.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        message.value = "Export saved"
    }
}
fun formatMileage(n: Double?) = n?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "—"
