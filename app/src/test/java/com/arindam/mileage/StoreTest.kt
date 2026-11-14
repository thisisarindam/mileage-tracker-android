package com.arindam.mileage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class StoreTest {
    private lateinit var store: Store
    @Before fun setup() { store = Store(Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MileageDatabase::class.java).addCallback(DEFAULT_VEHICLE_CALLBACK).build()) }
    @After fun close() { store.db.close() }
    private fun preview() = Imports.preview(javaClass.classLoader!!.getResource("fuel_entries_rows.csv")!!.readText(), true, emptySet())
    @Test fun duplicateConfirmationCannotOverwriteLocalEdits() = runBlocking {
        val preview = preview()
        assertEquals(2, store.import(preview))
        val entry = store.dao.fuel().first()
        store.dao.update(entry.copy(notes = "Locally edited", totalCost = "22.00001"))
        assertEquals(0, store.import(preview)) // stale preview: still protected by DB constraints
        assertEquals("Locally edited", store.dao.fuel().first { it.id == entry.id }.notes)
        assertEquals("22.00001", store.dao.fuel().first { it.id == entry.id }.totalCost)
        val again = Imports.preview(Imports.exportFuel(preview.fuel), true, store.dao.fuel().mapNotNull { it.sourceId }.toSet())
        assertEquals(2, again.duplicates); assertEquals(0, again.ready)
    }
    @Test fun maintenanceImportAlsoPreservesEdits() = runBlocking {
        val text = javaClass.classLoader!!.getResource("maintenance_logs_rows.csv")!!.readText()
        val preview = Imports.preview(text, false, emptySet())
        assertEquals(1, store.import(preview))
        val entry = store.dao.maintenance().single()
        store.dao.update(entry.copy(serviceType = "Edited locally", notes = "Keep me"))
        assertEquals(0, store.import(preview))
        assertEquals("Edited locally", store.dao.maintenance().single().serviceType)
        assertEquals("Keep me", store.dao.maintenance().single().notes)
    }
    @Test fun fullBackupRestoreIncludesPreferencesAndSourceIds() = runBlocking {
        store.import(preview())
        store.dao.add(Maintenance(date = "2025-01-01", serviceType = "Oil", odometer = "100", totalCost = "4.1234", sourceId = "service-source"))
        store.dao.setPreferences(Preferences(currency = "GBP"))
        val before = store.snapshot()
        val json = Backups.encode(before)
        store.dao.clearFuel(); store.dao.clearMaintenance(); store.dao.setPreferences(Preferences())
        store.restore(Backups.decode(json))
        assertEquals(before, store.snapshot())
    }
    @Test fun invalidBackupLeavesAllDataIntact() = runBlocking {
        store.import(preview()); store.dao.setPreferences(Preferences(currency = "EUR"))
        val before = store.snapshot()
        try { store.restore(before.copy(fuel = before.fuel + before.fuel.first().copy(date = "bad"))) ; fail() } catch (_: Exception) { }
        assertEquals(before, store.snapshot())
        try { store.restore(Backups.decode("{\"version\":999}")); fail() } catch (_: Exception) { }
        assertEquals(before, store.snapshot())
    }
    @Test fun databaseFailureRollsBackDeletionAndPreferences() = runBlocking {
        store.import(preview()); store.dao.setPreferences(Preferences(currency = "GBP"))
        val before = store.snapshot()
        // Inject an actual SQLite failure after deletes, independently of pre-validation.
        store.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON fuel BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try { store.restore(before.copy(preferences = Preferences())); fail() } catch (_: Exception) { }
        assertEquals(before, store.snapshot())
    }
}
