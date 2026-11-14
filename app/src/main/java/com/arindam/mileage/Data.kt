package com.arindam.mileage

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.util.UUID

// Decimal strings avoid SQLite REAL and JSON floating-point precision loss.
@Serializable
@Entity(tableName = "fuel", indices = [Index(value = ["sourceId"], unique = true)])
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
    val createdAt: String = OffsetDateTime.now().toString()
)
@Serializable
@Entity(tableName = "maintenance", indices = [Index(value = ["sourceId"], unique = true)])
data class Maintenance(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceId: String? = null,
    val date: String,
    val serviceType: String,
    val odometer: String,
    val totalCost: String,
    val notes: String = "",
    val createdAt: String = OffsetDateTime.now().toString()
)
// Preferences live in the same local SQLite transaction as histories so full restore is atomic.
@Serializable
@Entity(tableName = "preferences")
data class Preferences(@PrimaryKey val id: Int = 1, val currency: String = "INR", val units: String = "metric")

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
    decimal(pricePerLitre, "Price"); decimal(litres, "Litres")
}
fun Maintenance.validate() {
    validateIdentity(id, sourceId, date, createdAt)
    require(serviceType.isNotBlank()) { "Service type is required" }
    decimal(odometer, "Odometer"); decimal(totalCost, "Cost")
}
fun Preferences.validate() {
    require(id == 1 && units == "metric" && currency in listOf("INR", "USD", "EUR", "GBP")) { "Unsupported preferences" }
}

@Dao
interface RecordsDao {
    @Query("SELECT * FROM fuel ORDER BY date DESC, id") fun watchFuel(): Flow<List<Fuel>>
    @Query("SELECT * FROM maintenance ORDER BY date DESC, id") fun watchMaintenance(): Flow<List<Maintenance>>
    @Query("SELECT * FROM preferences WHERE id=1") fun watchPreferences(): Flow<Preferences?>
    @Query("SELECT * FROM fuel") suspend fun fuel(): List<Fuel>
    @Query("SELECT * FROM maintenance") suspend fun maintenance(): List<Maintenance>
    @Query("SELECT * FROM preferences WHERE id=1") suspend fun preferences(): Preferences?
    @Insert suspend fun add(fuel: Fuel)
    @Insert suspend fun add(maintenance: Maintenance)
    @Update suspend fun update(fuel: Fuel)
    @Update suspend fun update(maintenance: Maintenance)
    @Delete suspend fun delete(fuel: Fuel)
    @Delete suspend fun delete(maintenance: Maintenance)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun importFuel(rows: List<Fuel>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun importMaintenance(rows: List<Maintenance>): List<Long>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setPreferences(p: Preferences)
    @Query("DELETE FROM fuel") suspend fun clearFuel()
    @Query("DELETE FROM maintenance") suspend fun clearMaintenance()
}
@Database(entities = [Fuel::class, Maintenance::class, Preferences::class], version = 1, exportSchema = true)
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
