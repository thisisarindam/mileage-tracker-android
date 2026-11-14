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
class GarageStoreTest {
    private lateinit var store: Store
    @Before fun setup() { store = Store(Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MileageDatabase::class.java).addCallback(DEFAULT_VEHICLE_CALLBACK).build()) }
    @After fun close() { store.db.close() }
    private fun fuel(id: String = "local", vehicle: String = DEFAULT_VEHICLE_ID) = Fuel(id = id, sourceId = "source-$id", vehicleId = vehicle, date = "2025-01-01", odometer = "100", totalCost = "10.123400", pricePerLitre = "2", litres = "5", tankStatus = TankStatus.FULL)
    @Test fun separateVehiclesBackupAndRestoreAllMetadata() = runBlocking {
        val bike = Vehicle(id = "bike", name = "Test Bike", model = "Synthetic", registration = "EXAMPLE")
        store.saveVehicle(bike, false)
        store.save(fuel(), false); store.save(fuel("bike-record", bike.id).copy(missedEntry = true), false)
        store.save(Maintenance(vehicleId = bike.id, date = "2025-01-01", serviceType = "Oil", odometer = "100", totalCost = "50"), false)
        store.updatePreferences { it.copy(reduceMotion = true, currency = "GBP") }
        val expected = store.snapshot()
        assertEquals(1, store.dao.fuelForVehicle(DEFAULT_VEHICLE_ID).size)
        assertEquals(1, store.dao.fuelForVehicle(bike.id).size)
        store.restore(Backups.decode(Backups.encode(expected)))
        assertEquals(expected, store.snapshot())
        assertTrue(store.snapshot().fuel.single { it.vehicleId == bike.id }.missedEntry)
        assertTrue(store.snapshot().preferences.reduceMotion)
    }
    @Test fun vehicleDeletionProtectsDataAndLastVehicle() = runBlocking {
        val empty = Vehicle(id = "empty", name = "Empty")
        store.saveVehicle(empty, false)
        store.save(fuel(), false)
        val before = store.snapshot()
        try { store.deleteVehicle(Vehicle.default()); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(before, store.snapshot())
        store.deleteVehicle(empty)
        assertEquals(DEFAULT_VEHICLE_ID, store.snapshot().preferences.activeVehicleId)
        try { store.deleteVehicle(Vehicle.default()); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(1, store.dao.vehicles().size)
    }
    @Test fun globalImportIdsPreserveEditsAndNeverMoveRecordsToAnotherVehicle() = runBlocking {
        val csv = javaClass.classLoader!!.getResource("fuel_entries_rows.csv")!!.readText()
        val initial = Imports.preview(csv, true, emptySet())
        assertEquals(2, store.import(initial))
        val original = store.dao.fuel().first()
        store.save(original.copy(notes = "Keep local edit"), true)
        val bike = Vehicle(id = "bike", name = "Bike"); store.saveVehicle(bike, false)
        val stale = Imports.preview(csv, true, emptySet(), bike.id, bike.name)
        assertEquals(0, store.import(stale))
        assertTrue(store.dao.fuelForVehicle(bike.id).isEmpty())
        assertEquals("Keep local edit", store.dao.fuel().single { it.id == original.id }.notes)
        assertEquals(2, Imports.preview(csv, true, store.dao.fuelImportKeys().toSet(), bike.id, bike.name).duplicates)
    }
    @Test fun nativeCsvReimportAndStalePreviewCannotCreateDuplicates() = runBlocking {
        val original = fuel().copy(sourceId = null)
        val csv = Imports.exportFuel(listOf(original))
        val stale = Imports.preview(csv, true, emptySet())
        store.save(original, false)
        assertEquals(0, store.import(stale))
        assertEquals(1, Imports.preview(csv, true, store.dao.fuelImportKeys().toSet()).duplicates)
        assertEquals(1, store.dao.fuel().size)
    }
    @Test fun orphanBackupAndMidRestoreFailuresLeaveEntireGarageUntouched() = runBlocking {
        store.saveVehicle(Vehicle(id = "bike", name = "Bike"), false)
        store.save(fuel(), false)
        val before = store.snapshot()
        try { store.restore(before.copy(fuel = listOf(fuel().copy(vehicleId = "missing")))); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(before, store.snapshot())
        store.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_garage_restore BEFORE INSERT ON fuel BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try { store.restore(before.copy(preferences = before.preferences.copy(currency = "USD"))); fail() } catch (_: Exception) { }
        assertEquals(before, store.snapshot())
    }
}
