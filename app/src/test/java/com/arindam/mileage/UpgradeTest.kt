package com.arindam.mileage

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class UpgradeTest {
    private suspend fun checkUpgrade(version: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "upgrade-$version.db"
        context.deleteDatabase(name)
        val schema = Json.parseToJsonElement(javaClass.classLoader!!.getResource("schema-v$version.json")!!.readText()).jsonObject.getValue("database").jsonObject
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                schema.getValue("entities").jsonArray.forEach { entity ->
                    val obj = entity.jsonObject
                    fun sql(value: String) = value.replace("\${TABLE_NAME}", obj.getValue("tableName").jsonPrimitive.content)
                    db.execSQL(sql(obj.getValue("createSql").jsonPrimitive.content))
                    obj.getValue("indices").jsonArray.forEach { index -> db.execSQL(sql(index.jsonObject.getValue("createSql").jsonPrimitive.content)) }
                }
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        helper.writableDatabase.execSQL(if (version == 1) "INSERT INTO preferences (id,currency,units) VALUES (1,'GBP','metric')" else "INSERT INTO preferences (id,currency,units,darkTheme) VALUES (1,'GBP','metric',0)")
        helper.writableDatabase.execSQL("INSERT INTO fuel (id,sourceId,date,odometer,totalCost,pricePerLitre,litres,station,notes,createdAt) VALUES ('synthetic','original-source','2025-01-01','100','100.123400','10.000','10','Example','Keep me','2025-01-02T00:00:00Z')")
        helper.writableDatabase.execSQL("INSERT INTO maintenance (id,sourceId,date,serviceType,odometer,totalCost,notes,createdAt) VALUES ('service','original-service','2025-01-02','Oil','100','50.987600','Keep service','2025-01-03T00:00:00Z')")
        helper.close()
        val db = Room.databaseBuilder(context, MileageDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(DEFAULT_VEHICLE_CALLBACK).build()
        try {
            val store = Store(db)
            val snapshot = store.snapshot()
            assertEquals("Keep me", snapshot.fuel.single().notes)
            assertEquals("100.123400", snapshot.fuel.single().totalCost)
            assertEquals("10.000", snapshot.fuel.single().pricePerLitre)
            assertEquals("original-source", snapshot.fuel.single().sourceId)
            assertEquals(TankStatus.UNSPECIFIED, snapshot.fuel.single().tankStatus)
            assertEquals(DEFAULT_VEHICLE_ID, snapshot.fuel.single().vehicleId)
            assertFalse(snapshot.fuel.single().missedEntry)
            assertEquals("original-service", snapshot.maintenance.single().sourceId)
            assertEquals("50.987600", snapshot.maintenance.single().totalCost)
            assertEquals(DEFAULT_VEHICLE_ID, snapshot.maintenance.single().vehicleId)
            assertEquals(listOf(Vehicle.default()), snapshot.vehicles)
            assertEquals(Preferences(currency = "GBP", darkTheme = version == 1), snapshot.preferences)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun upgradeFromOriginalApkRetainsBothHistories() = runBlocking { checkUpgrade(1) }
    @Test fun upgradeFromReferenceUiApkRetainsBothHistoriesAndAppearance() = runBlocking { checkUpgrade(2) }
    @Test fun originalV1AndV2JsonRestoreAsUnspecifiedWithoutLosingValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MileageDatabase::class.java).addCallback(DEFAULT_VEHICLE_CALLBACK).build()
        try {
            val original = Backup(fuel = listOf(Fuel(sourceId = "source", date = "2025-01-01", odometer = "1", totalCost = "1.2300", pricePerLitre = "1", litres = "1")), maintenance = emptyList(), preferences = Preferences(currency = "EUR", darkTheme = false))
            for (version in 1..2) {
                val root = Json.parseToJsonElement(Backups.encode(original)).jsonObject.toMutableMap()
                root.remove("vehicles"); root["version"] = JsonPrimitive(version)
                root["fuel"] = JsonArray(root.getValue("fuel").jsonArray.map { JsonObject(it.jsonObject.filterKeys { k -> k !in setOf("vehicleId", "tankStatus", "missedEntry") }) })
                root["preferences"] = JsonObject(root.getValue("preferences").jsonObject.filterKeys { it !in setOf("activeVehicleId", "reduceMotion") && (version != 1 || it != "darkTheme") })
                val old = Backups.decode(JsonObject(root).toString())
                val store = Store(db)
                store.restore(old)
                assertEquals(original.copy(preferences = original.preferences.copy(darkTheme = version == 1)), store.snapshot())
                assertEquals(TankStatus.UNSPECIFIED, store.snapshot().fuel.single().tankStatus)
                assertEquals(store.snapshot(), Backups.decode(Backups.encode(old)))
            }
        } finally { db.close() }
    }
}
