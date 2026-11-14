package com.arindam.mileage

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.util.UUID

const val DEFAULT_VEHICLE_ID = "default-vehicle"
object TankStatus {
    const val UNSPECIFIED = "UNSPECIFIED"
    const val FULL = "FULL"
    const val PARTIAL = "PARTIAL"
    val all = setOf(UNSPECIFIED, FULL, PARTIAL)
}
@Serializable
@Entity(tableName = "vehicles")
data class Vehicle(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String, val model: String = "", val registration: String = "") {
    fun validate() {
        require(id.isNotBlank()) { "Missing vehicle ID" }
        require(name.isNotBlank() && name.length <= 80) { "Enter a vehicle name (up to 80 characters)" }
        require(model.length <= 80) { "Vehicle model must be at most 80 characters" }
        require(registration.length <= 32) { "Registration must be at most 32 characters" }
    }
    companion object { fun default() = Vehicle(DEFAULT_VEHICLE_ID, "My Vehicle") }
}

// Decimal strings avoid SQLite REAL and JSON floating-point precision loss.
@Serializable
@Entity(tableName = "fuel", indices = [Index(value = ["sourceId"], unique = true), Index(value = ["vehicleId"])],
    foreignKeys = [ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT)])
data class Fuel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceId: String? = null,
    val date: String,
    val odometer: String,
    val totalCost: String,
    val pricePerLitre: String,
    val litres: String,
    val station: String = "",
    val notes: String = "",
    val createdAt: String = OffsetDateTime.now().toString(),
    @ColumnInfo(defaultValue = "'default-vehicle'") val vehicleId: String = DEFAULT_VEHICLE_ID,
    @ColumnInfo(defaultValue = "'UNSPECIFIED'") val tankStatus: String = TankStatus.UNSPECIFIED,
    @ColumnInfo(defaultValue = "0") val missedEntry: Boolean = false
)
@Serializable
@Entity(tableName = "maintenance", indices = [Index(value = ["sourceId"], unique = true), Index(value = ["vehicleId"])],
    foreignKeys = [ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT)])
data class Maintenance(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceId: String? = null,
    val date: String,
    val serviceType: String,
    val odometer: String,
    val totalCost: String,
    val notes: String = "",
    val createdAt: String = OffsetDateTime.now().toString(),
    @ColumnInfo(defaultValue = "'default-vehicle'") val vehicleId: String = DEFAULT_VEHICLE_ID
)
// Preferences live in the same local SQLite transaction as histories so full restore is atomic.
@Serializable
@Entity(tableName = "preferences")
data class Preferences(
    @PrimaryKey val id: Int = 1, val currency: String = "INR", val units: String = "metric",
    @ColumnInfo(defaultValue = "1") val darkTheme: Boolean = true,
    @ColumnInfo(defaultValue = "'default-vehicle'") val activeVehicleId: String = DEFAULT_VEHICLE_ID,
    @ColumnInfo(defaultValue = "0") val reduceMotion: Boolean = false
)

fun decimal(value: String, name: String, positive: Boolean = false): BigDecimal {
    require(value.length in 1..100 && Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)").matches(value)) { "$name must be a finite decimal" }
    val n = value.toBigDecimal()
    require(if (positive) n > BigDecimal.ZERO else n >= BigDecimal.ZERO) { "$name must be ${if (positive) "positive" else "nonnegative"}" }
    return n
}
private fun validateIdentity(id: String, source: String?, date: String, created: String) {
    require(id.isNotBlank()) { "Missing local ID" }
    require(source == null || source.isNotBlank()) { "Blank source ID" }
    LocalDate.parse(date)
    // PostgreSQL exports timestamps with a space separator and sometimes an hour-only offset.
    OffsetDateTime.parse(created.replace(' ', 'T'))
}
fun Fuel.validate() {
    validateIdentity(id, sourceId, date, createdAt)
    decimal(odometer, "Odometer"); decimal(totalCost, "Cost")
    decimal(pricePerLitre, "Price"); decimal(litres, "Litres", tankStatus != TankStatus.UNSPECIFIED)
    require(vehicleId.isNotBlank() && tankStatus in TankStatus.all) { "Invalid vehicle or tank status" }
}
fun Maintenance.validate() {
    validateIdentity(id, sourceId, date, createdAt)
    require(vehicleId.isNotBlank()) { "Missing vehicle" }
    require(serviceType.isNotBlank()) { "Service type is required" }
    decimal(odometer, "Odometer"); decimal(totalCost, "Cost")
}
fun Preferences.validate() {
    require(activeVehicleId.isNotBlank()) { "Missing active vehicle" }
    require(id == 1 && units == "metric" && currency in listOf("INR", "USD", "EUR", "GBP")) { "Unsupported preferences" }
}

data class VehicleHistory(
    @Embedded val vehicle: Vehicle,
    @Relation(parentColumn = "id", entityColumn = "vehicleId") val fuel: List<Fuel>,
    @Relation(parentColumn = "id", entityColumn = "vehicleId") val maintenance: List<Maintenance>
)
@Dao
interface RecordsDao {
    @Transaction @Query("SELECT * FROM vehicles ORDER BY name COLLATE NOCASE, id") fun watchGarage(): Flow<List<VehicleHistory>>
    @Query("SELECT * FROM vehicles ORDER BY name COLLATE NOCASE, id") suspend fun vehicles(): List<Vehicle>
    @Insert suspend fun add(vehicle: Vehicle)
    @Update suspend fun update(vehicle: Vehicle): Int
    @Delete suspend fun delete(vehicle: Vehicle): Int
    @Query("DELETE FROM vehicles") suspend fun clearVehicles()
    @Query("SELECT COUNT(*) FROM fuel WHERE vehicleId=:id") suspend fun fuelCount(id: String): Int
    @Query("SELECT COUNT(*) FROM maintenance WHERE vehicleId=:id") suspend fun maintenanceCount(id: String): Int
    @Query("SELECT sourceId FROM fuel WHERE sourceId IS NOT NULL UNION SELECT id FROM fuel") suspend fun fuelImportKeys(): List<String>
    @Query("SELECT sourceId FROM maintenance WHERE sourceId IS NOT NULL UNION SELECT id FROM maintenance") suspend fun maintenanceImportKeys(): List<String>
    @Query("SELECT * FROM fuel ORDER BY date DESC, id") fun watchFuel(): Flow<List<Fuel>>
    @Query("SELECT * FROM maintenance ORDER BY date DESC, id") fun watchMaintenance(): Flow<List<Maintenance>>
    @Query("SELECT * FROM preferences WHERE id=1") fun watchPreferences(): Flow<Preferences?>
    @Query("SELECT * FROM fuel WHERE vehicleId=:id") suspend fun fuelForVehicle(id: String): List<Fuel>
    @Query("SELECT * FROM maintenance WHERE vehicleId=:id") suspend fun maintenanceForVehicle(id: String): List<Maintenance>
    @Query("SELECT * FROM fuel") suspend fun fuel(): List<Fuel>
    @Query("SELECT * FROM maintenance") suspend fun maintenance(): List<Maintenance>
    @Query("SELECT * FROM preferences WHERE id=1") suspend fun preferences(): Preferences?
    @Insert suspend fun add(fuel: Fuel)
    @Insert suspend fun add(maintenance: Maintenance)
    @Update suspend fun update(fuel: Fuel): Int
    @Update suspend fun update(maintenance: Maintenance): Int
    @Delete suspend fun delete(fuel: Fuel)
    @Delete suspend fun delete(maintenance: Maintenance)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun importFuel(rows: List<Fuel>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun importMaintenance(rows: List<Maintenance>): List<Long>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setPreferences(p: Preferences)
    @Query("DELETE FROM fuel") suspend fun clearFuel()
    @Query("DELETE FROM maintenance") suspend fun clearMaintenance()
}
@Database(entities = [Fuel::class, Maintenance::class, Preferences::class, Vehicle::class], version = 3, exportSchema = true)
abstract class MileageDatabase : RoomDatabase() { abstract fun records(): RecordsDao }

object Mileage {
    private fun ratio(a: BigDecimal, b: BigDecimal): Double? = if (a.signum() > 0 && b.signum() > 0) a.divide(b, 12, RoundingMode.HALF_UP).toDouble() else null
    fun sorted(rows: List<Fuel>) = rows.sortedBy { it.odometer.toBigDecimal() }
    fun perFill(rows: List<Fuel>): Map<String, Double?> {
        val sorted = sorted(rows)
        return sorted.mapIndexed { i, row -> row.id to if (i == 0) null else ratio(row.odometer.toBigDecimal() - sorted[i-1].odometer.toBigDecimal(), row.litres.toBigDecimal()) }.toMap()
    }
    fun distance(rows: List<Fuel>): BigDecimal = if (rows.size < 2) BigDecimal.ZERO else sorted(rows).let { it.last().odometer.toBigDecimal() - it.first().odometer.toBigDecimal() }
    fun dashboardMileage(rows: List<Fuel>): Double? = ratio(distance(rows), sorted(rows).drop(1).sumOf { it.litres.toBigDecimal() })
    fun analyticsMileage(rows: List<Fuel>): Double? = ratio(distance(rows), rows.sumOf { it.litres.toBigDecimal() })
    fun spent(rows: List<Fuel>) = rows.sumOf { it.totalCost.toBigDecimal() }
    fun monthSpent(rows: List<Fuel>, month: YearMonth) = spent(rows.filter { YearMonth.from(LocalDate.parse(it.date)) == month })
    fun newTotal(litres: String, price: String): String {
        // Match parseFloat multiplication followed by JS toFixed(2), including binary ties.
        val product = decimal(litres, "Litres").toDouble() * decimal(price, "Price").toDouble()
        require(product.isFinite()) { "Total is too large" }
        return BigDecimal(product).setScale(2, RoundingMode.HALF_UP).toPlainString()
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE preferences ADD COLUMN darkTheme INTEGER NOT NULL DEFAULT 1")
    }
}

val DEFAULT_VEHICLE_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO vehicles (id,name,model,registration) VALUES ('default-vehicle','My Vehicle','','')")
    }
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS vehicles (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, model TEXT NOT NULL, registration TEXT NOT NULL)")
        db.execSQL("INSERT INTO vehicles VALUES ('default-vehicle','My Vehicle','','')")
        db.execSQL("CREATE TABLE fuel_new (id TEXT NOT NULL PRIMARY KEY, sourceId TEXT, date TEXT NOT NULL, odometer TEXT NOT NULL, totalCost TEXT NOT NULL, pricePerLitre TEXT NOT NULL, litres TEXT NOT NULL, station TEXT NOT NULL, notes TEXT NOT NULL, createdAt TEXT NOT NULL, vehicleId TEXT NOT NULL DEFAULT 'default-vehicle', tankStatus TEXT NOT NULL DEFAULT 'UNSPECIFIED', missedEntry INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(vehicleId) REFERENCES vehicles(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("INSERT INTO fuel_new (id,sourceId,date,odometer,totalCost,pricePerLitre,litres,station,notes,createdAt) SELECT id,sourceId,date,odometer,totalCost,pricePerLitre,litres,station,notes,createdAt FROM fuel")
        db.execSQL("DROP TABLE fuel")
        db.execSQL("ALTER TABLE fuel_new RENAME TO fuel")
        db.execSQL("CREATE UNIQUE INDEX index_fuel_sourceId ON fuel(sourceId)")
        db.execSQL("CREATE INDEX index_fuel_vehicleId ON fuel(vehicleId)")
        db.execSQL("CREATE TABLE maintenance_new (id TEXT NOT NULL PRIMARY KEY, sourceId TEXT, date TEXT NOT NULL, serviceType TEXT NOT NULL, odometer TEXT NOT NULL, totalCost TEXT NOT NULL, notes TEXT NOT NULL, createdAt TEXT NOT NULL, vehicleId TEXT NOT NULL DEFAULT 'default-vehicle', FOREIGN KEY(vehicleId) REFERENCES vehicles(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("INSERT INTO maintenance_new (id,sourceId,date,serviceType,odometer,totalCost,notes,createdAt) SELECT id,sourceId,date,serviceType,odometer,totalCost,notes,createdAt FROM maintenance")
        db.execSQL("DROP TABLE maintenance")
        db.execSQL("ALTER TABLE maintenance_new RENAME TO maintenance")
        db.execSQL("CREATE UNIQUE INDEX index_maintenance_sourceId ON maintenance(sourceId)")
        db.execSQL("CREATE INDEX index_maintenance_vehicleId ON maintenance(vehicleId)")
        db.execSQL("ALTER TABLE preferences ADD COLUMN activeVehicleId TEXT NOT NULL DEFAULT 'default-vehicle'")
        db.execSQL("ALTER TABLE preferences ADD COLUMN reduceMotion INTEGER NOT NULL DEFAULT 0")
    }
}
