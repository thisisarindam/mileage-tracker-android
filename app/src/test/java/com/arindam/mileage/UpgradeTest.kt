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
    @Test fun upgradeFromOriginalApkRetainsRecordsAndAddsDefaultTheme() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "upgrade-test.db"
        context.deleteDatabase(name)
        val schema = Json.parseToJsonElement(javaClass.classLoader!!.getResource("schema-v1.json")!!.readText()).jsonObject.getValue("database").jsonObject
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                schema.getValue("entities").jsonArray.forEach { entity ->
                    val obj = entity.jsonObject
                    db.execSQL(obj.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", obj.getValue("tableName").jsonPrimitive.content))
                    obj.getValue("indices").jsonArray.forEach { index -> db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", obj.getValue("tableName").jsonPrimitive.content)) }
                }
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        helper.writableDatabase.execSQL("INSERT INTO preferences (id,currency,units) VALUES (1,'GBP','metric')")
        helper.writableDatabase.execSQL("INSERT INTO fuel (id,sourceId,date,odometer,totalCost,pricePerLitre,litres,station,notes,createdAt) VALUES ('synthetic','original-source','2025-01-01','100','100.123400','10','10','Example','Keep me','2025-01-02T00:00:00Z')")
        helper.close()
        val db = Room.databaseBuilder(context, MileageDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val store = Store(db)
            val snapshot = store.snapshot()
            assertEquals("Keep me", snapshot.fuel.single().notes)
            assertEquals("100.123400", snapshot.fuel.single().totalCost)
            assertEquals("original-source", snapshot.fuel.single().sourceId)
            assertEquals(Preferences(currency = "GBP"), snapshot.preferences)
            store.dao.setPreferences(snapshot.preferences.copy(darkTheme = false))
            assertFalse(store.snapshot().preferences.darkTheme)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun originalV1JsonStillRestoresAndV2IncludesTheme() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MileageDatabase::class.java).build()
        try {
            val original = Backup(fuel = listOf(Fuel(sourceId = "source", date = "2025-01-01", odometer = "1", totalCost = "1.2300", pricePerLitre = "1", litres = "1")), maintenance = emptyList(), preferences = Preferences(currency = "EUR"))
            val root = Json.parseToJsonElement(Backups.encode(original)).jsonObject.toMutableMap()
            root["version"] = JsonPrimitive(1)
            root["preferences"] = JsonObject(root.getValue("preferences").jsonObject.filterKeys { it != "darkTheme" })
            val old = Backups.decode(JsonObject(root).toString())
            val store = Store(db)
            store.restore(old)
            assertEquals(original, store.snapshot())
            val themed = original.copy(preferences = original.preferences.copy(darkTheme = false))
            store.restore(Backups.decode(Backups.encode(themed)))
            assertEquals(themed, store.snapshot())
        } finally { db.close() }
    }
}
