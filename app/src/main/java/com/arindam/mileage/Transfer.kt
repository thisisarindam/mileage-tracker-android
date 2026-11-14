package com.arindam.mileage

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** RFC 4180: quoted delimiters, doubled quotes, CRLF and embedded newlines. */
object Csv {
    data class Row(val line: Int, val cells: List<String>)
    fun parse(input: String): List<Row> {
        val text = input.removePrefix("\uFEFF")
        val rows = mutableListOf<Row>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false; var closed = false; var line = 1; var start = 1; var i = 0
        fun endField() { fields.add(field.toString()); field.clear(); closed = false }
        fun endRow() { endField(); if (fields.any { it.isNotEmpty() }) rows.add(Row(start, fields)); fields = mutableListOf() }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else { field.append(c); if (c == '\n') line++ }
            } else when (c) {
                '"' -> { require(field.isEmpty() && !closed) { "Row $line: unexpected quote" }; quoted = true }
                ',' -> endField()
                '\r', '\n' -> { endRow(); if (c == '\r' && i + 1 < text.length && text[i+1] == '\n') i++; line++; start = line }
                else -> { require(!closed) { "Row $line: text after closing quote" }; field.append(c) }
            }
            i++
        }
        require(!quoted) { "Row $start: unterminated quoted field" }
        if (field.isNotEmpty() || fields.isNotEmpty() || closed) endRow()
        return rows
    }
    fun write(rows: List<List<String>>) = rows.joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } }
}
data class RowError(val line: Int, val reason: String)
data class ImportPreview(val fuel: List<Fuel> = emptyList(), val maintenance: List<Maintenance> = emptyList(), val duplicates: Int, val errors: List<RowError>, val vehicleId: String = DEFAULT_VEHICLE_ID, val vehicleName: String = "My Vehicle") {
    val ready get() = fuel.size + maintenance.size
}
object Imports {
    val fuelHeaders = "id,user_id,entry_date,odometer_km,total_cost,price_per_litre,litres,station_name,notes,created_at".split(',')
    val maintenanceHeaders = "id,user_id,service_date,service_type,odometer_km,total_cost,notes,created_at".split(',')
    fun preview(text: String, fuel: Boolean, existing: Set<String>, vehicleId: String = DEFAULT_VEHICLE_ID, vehicleName: String = "My Vehicle"): ImportPreview {
        val rows = Csv.parse(text)
        require(rows.isNotEmpty()) { "Empty CSV" }
        val headers = rows.first().cells.map { it.trim() }
        require(headers.distinct().size == headers.size) { "Duplicate header names" }
        val required = if (fuel) fuelHeaders else maintenanceHeaders
        require(headers.containsAll(required)) { "Missing columns: ${(required - headers.toSet()).joinToString()}" }
        val seen = existing.toMutableSet(); val fs = mutableListOf<Fuel>(); val ms = mutableListOf<Maintenance>()
        val errors = mutableListOf<RowError>(); var duplicates = 0
        for (row in rows.drop(1)) {
            try {
                require(row.cells.size == headers.size) { "Expected ${headers.size} columns, found ${row.cells.size}" }
                val values = headers.zip(row.cells).toMap()
                fun v(name: String) = values.getValue(name)
                val source = v("id"); require(source.isNotBlank()) { "Missing source id" }
                if (fuel) {
                    val f = Fuel(vehicleId = vehicleId, sourceId = source, date = v("entry_date"), odometer = v("odometer_km"), totalCost = v("total_cost"), pricePerLitre = v("price_per_litre"), litres = v("litres"), station = v("station_name"), notes = v("notes"), createdAt = v("created_at"))
                    f.validate(); if (seen.add(source)) fs.add(f) else duplicates++
                } else {
                    val m = Maintenance(vehicleId = vehicleId, sourceId = source, date = v("service_date"), serviceType = v("service_type"), odometer = v("odometer_km"), totalCost = v("total_cost"), notes = v("notes"), createdAt = v("created_at"))
                    m.validate(); if (seen.add(source)) ms.add(m) else duplicates++
                }
            } catch (e: Exception) { errors.add(RowError(row.line, e.message ?: "Invalid row")) }
        }
        return ImportPreview(fs, ms, duplicates, errors, vehicleId, vehicleName)
    }
    fun exportFuel(rows: List<Fuel>) = Csv.write(listOf(fuelHeaders) + rows.map { listOf(it.sourceId ?: it.id, "", it.date, it.odometer, it.totalCost, it.pricePerLitre, it.litres, it.station, it.notes, it.createdAt) })
    fun exportMaintenance(rows: List<Maintenance>) = Csv.write(listOf(maintenanceHeaders) + rows.map { listOf(it.sourceId ?: it.id, "", it.date, it.serviceType, it.odometer, it.totalCost, it.notes, it.createdAt) })
}
@Serializable
data class Backup(
    val format: String = "mileage-tracker", val version: Int = 3,
    val fuel: List<Fuel>, val maintenance: List<Maintenance>, val preferences: Preferences,
    val vehicles: List<Vehicle> = listOf(Vehicle.default())
) {
    fun validate() {
        require(format == "mileage-tracker" && version in 1..3) { "Unsupported backup format/version" }
        require(vehicles.isNotEmpty()) { "A backup needs at least one vehicle" }
        vehicles.forEach { it.validate() }
        val ids = vehicles.map { it.id }.toSet()
        require(ids.size == vehicles.size) { "Duplicate vehicle IDs" }
        fuel.forEach { it.validate(); require(it.vehicleId in ids) { "Fuel record references a missing vehicle" } }
        maintenance.forEach { it.validate(); require(it.vehicleId in ids) { "Service record references a missing vehicle" } }
        preferences.validate()
        require(preferences.activeVehicleId in ids) { "Selected vehicle is missing" }
        fun unique(ids: List<String>, sources: List<String>) { require(ids.distinct().size == ids.size && sources.distinct().size == sources.size) { "Duplicate record or source IDs" } }
        unique(fuel.map { it.id }, fuel.mapNotNull { it.sourceId })
        unique(maintenance.map { it.id }, maintenance.mapNotNull { it.sourceId })
    }
}
object Backups {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; coerceInputValues = false }
    fun encode(backup: Backup): String { backup.validate(); return json.encodeToString(backup.copy(version = 3)) }
    fun decode(text: String): Backup {
        val root = json.parseToJsonElement(text).jsonObject
        val version = root.getValue("version").jsonPrimitive.int
        require(version in 1..3) { "Unsupported backup version" }
        fun fields(value: JsonObject, required: Set<String>) { require(value.keys == required) { "Backup has missing or unknown fields" } }
        fields(root, setOf("format", "version", "fuel", "maintenance", "preferences") + if (version == 3) setOf("vehicles") else emptySet())
        root.getValue("fuel").jsonArray.forEach {
            fields(it.jsonObject, setOf("id", "sourceId", "date", "odometer", "totalCost", "pricePerLitre", "litres", "station", "notes", "createdAt") + if (version == 3) setOf("vehicleId", "tankStatus", "missedEntry") else emptySet())
        }
        root.getValue("maintenance").jsonArray.forEach {
            fields(it.jsonObject, setOf("id", "sourceId", "date", "serviceType", "odometer", "totalCost", "notes", "createdAt") + if (version == 3) setOf("vehicleId") else emptySet())
        }
        val preferenceFields = setOf("id", "currency", "units") + if (version >= 2) setOf("darkTheme") else emptySet()
        fields(root.getValue("preferences").jsonObject, preferenceFields + if (version == 3) setOf("activeVehicleId", "reduceMotion") else emptySet())
        if (version == 3) root.getValue("vehicles").jsonArray.forEach { fields(it.jsonObject, setOf("id", "name", "model", "registration")) }
        return json.decodeFromString<Backup>(text).also { it.validate() }
    }
}
class Store(val db: MileageDatabase) {
    val dao = db.records()
    suspend fun snapshot(): Backup = db.withTransaction {
        val vehicles = dao.vehicles()
        Backup(fuel = dao.fuel(), maintenance = dao.maintenance(), preferences = dao.preferences() ?: Preferences(activeVehicleId = vehicles.firstOrNull()?.id ?: DEFAULT_VEHICLE_ID), vehicles = vehicles)
    }
    private suspend fun requireVehicle(id: String) { require(dao.vehicles().any { it.id == id }) { "Vehicle no longer exists" } }
    suspend fun save(f: Fuel, editing: Boolean) = db.withTransaction {
        f.validate(); requireVehicle(f.vehicleId)
        if (editing) require(dao.update(f) == 1) { "Fuel record no longer exists" } else dao.add(f)
    }
    suspend fun save(m: Maintenance, editing: Boolean) = db.withTransaction {
        m.validate(); requireVehicle(m.vehicleId)
        if (editing) require(dao.update(m) == 1) { "Service record no longer exists" } else dao.add(m)
    }
    suspend fun saveVehicle(vehicle: Vehicle, editing: Boolean) = db.withTransaction {
        vehicle.validate()
        if (editing) require(dao.update(vehicle) == 1) { "Vehicle no longer exists" } else {
            dao.add(vehicle)
            dao.setPreferences((dao.preferences() ?: Preferences()).copy(activeVehicleId = vehicle.id))
        }
    }
    suspend fun selectVehicle(id: String) = db.withTransaction { requireVehicle(id); dao.setPreferences((dao.preferences() ?: Preferences()).copy(activeVehicleId = id)) }
    suspend fun deleteVehicle(vehicle: Vehicle) = db.withTransaction {
        val remaining = dao.vehicles().filter { it.id != vehicle.id }
        require(remaining.isNotEmpty()) { "Keep at least one vehicle" }
        require(dao.fuelCount(vehicle.id) == 0 && dao.maintenanceCount(vehicle.id) == 0) { "Remove this vehicle's records before deleting it" }
        val p = dao.preferences() ?: Preferences()
        if (p.activeVehicleId == vehicle.id) dao.setPreferences(p.copy(activeVehicleId = remaining.first().id))
        require(dao.delete(vehicle) == 1) { "Vehicle no longer exists" }
    }
    suspend fun updatePreferences(change: (Preferences) -> Preferences) = db.withTransaction {
        val p = change(dao.preferences() ?: Preferences())
        p.validate(); requireVehicle(p.activeVehicleId); dao.setPreferences(p)
    }
    suspend fun import(preview: ImportPreview): Int = db.withTransaction {
        requireVehicle(preview.vehicleId)
        preview.fuel.forEach { it.validate(); require(it.vehicleId == preview.vehicleId) { "Import vehicle mismatch" } }
        preview.maintenance.forEach { it.validate(); require(it.vehicleId == preview.vehicleId) { "Import vehicle mismatch" } }
        // Recheck at commit time: stale previews and reimported native CSV exports cannot overwrite local edits.
        val fuelKeys = dao.fuelImportKeys().toMutableSet()
        val maintenanceKeys = dao.maintenanceImportKeys().toMutableSet()
        val fuel = preview.fuel.filter { fuelKeys.add(it.sourceId ?: it.id) }
        val maintenance = preview.maintenance.filter { maintenanceKeys.add(it.sourceId ?: it.id) }
        (dao.importFuel(fuel) + dao.importMaintenance(maintenance)).count { it != -1L }
    }
    suspend fun restore(backup: Backup) {
        backup.validate()
        db.withTransaction {
            dao.clearFuel(); dao.clearMaintenance(); dao.clearVehicles()
            backup.vehicles.forEach { dao.add(it) }
            backup.fuel.forEach { dao.add(it) }; backup.maintenance.forEach { dao.add(it) }
            dao.setPreferences(backup.preferences)
        }
    }
}
