package com.arindam.mileage

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h852dp-xhdpi", application = MileageApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmoothCoreUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[AppModel::class.java]
    private val store get() = (compose.activity.application as MileageApp).store
    @Before fun seed(): Unit = runBlocking {
        store.saveVehicle(Vehicle(id = "bike", name = "Test Bike"), false)
        store.selectVehicle(DEFAULT_VEHICLE_ID)
        val today = LocalDate.now().toString()
        store.dao.importFuel(listOf(
            Fuel(id = "baseline", date = today, odometer = "1000", totalCost = "1000", pricePerLitre = "100", litres = "10", station = "Starting", tankStatus = TankStatus.FULL),
            Fuel(id = "partial", date = today, odometer = "1050", totalCost = "400", pricePerLitre = "100", litres = "4", station = "Middle", notes = "Search needle", tankStatus = TankStatus.PARTIAL),
            Fuel(id = "closing", date = today, odometer = "1200", totalCost = "600", pricePerLitre = "100", litres = "6", station = "Closing", notes = "Do not autofill this", tankStatus = TankStatus.FULL),
            Fuel(id = "bike-fill", vehicleId = "bike", date = today, odometer = "9000", totalCost = "200", pricePerLitre = "100", litres = "2", station = "Bike station", tankStatus = TankStatus.FULL)
        ))
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/ui/$name.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun ready() { compose.waitUntil(10_000) { model.data.value.loaded && model.data.value.fuel.size == 3 } }
    private fun nav(label: String) { compose.onNodeWithContentDescription("Navigate $label").performClick() }
    @Test fun filtersAndRapidNavigationKeepVehicleAndMileageContextCorrect() {
        ready(); nav("History")
        compose.onNodeWithText("Search history").performTextInput("needle")
        compose.waitUntil(10_000) { model.filteredHistory.value.fuel.map { it.id } == listOf("partial") }
        assertEquals(12.5, model.data.value.tanks.readings.getValue("partial").value!!, 1e-9)
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Filter & Sort").performClick()
        compose.onNodeWithText("Highest cost").performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.waitUntil(10_000) { model.filteredHistory.value.applied.sort == HistorySort.HIGHEST_COST && model.filteredHistory.value.fuel.size == 3 }
        assertEquals("baseline", model.filteredHistory.value.fuel.first().id)
        screenshot("core-history")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6)
        screenshot("core-history-records")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.mainClock.autoAdvance = false
        repeat(3) { listOf("Home", "Analytics", "Settings", "History").forEach { nav(it); compose.mainClock.advanceTimeBy(16) } }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Search history").assertExists()
        compose.onNodeWithContentDescription("Select vehicle").performClick()
        compose.onNodeWithText("Test Bike").performClick()
        compose.waitUntil(10_000) { model.data.value.activeVehicle.id == "bike" && model.filteredHistory.value.vehicleId == "bike" }
        assertEquals(listOf("bike-fill"), model.data.value.fuel.map { it.id })
        assertEquals(HistoryFilter(), model.historyFilter.value)
        assertNull(model.data.value.tanks.confirmedMileage)
        assertEquals("bike-fill", model.data.value.lastFuel!!.id)
    }
    @Test fun autofillNeverCopiesIdentityOdometerQuantityNotesOrTankFlags() {
        ready()
        compose.onNodeWithText("Add Entry").performClick()
        compose.onNodeWithText("Fuel Fill-up").performClick()
        compose.onNodeWithText("Fill from last entry").performClick()
        fun text(label: String) = compose.onNodeWithText(label).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("100", text("Price per litre (INR)"))
        assertEquals("Closing", text("Station (optional)"))
        assertEquals("", text("Odometer (km)")); assertEquals("", text("Litres")); assertEquals("", text("Notes (optional)"))
        assertEquals("Unspecified · original calculation", text("Tank status"))
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText("Odometer (km)").performTextReplacement("1300")
        compose.onNodeWithText("Litres").performTextReplacement("5")
        compose.onNodeWithText("Tank status").performScrollTo().performClick()
        compose.onNodeWithText("Full tank").performClick()
        compose.onNodeWithText("Save Entry").performClick()
        compose.waitUntil(10_000) { runBlocking { store.dao.fuelForVehicle(DEFAULT_VEHICLE_ID).size == 4 } }
        val added = runBlocking { store.dao.fuelForVehicle(DEFAULT_VEHICLE_ID).single { it.id !in setOf("baseline", "partial", "closing") } }
        assertEquals("1300", added.odometer); assertEquals("5", added.litres); assertEquals("500.00", added.totalCost)
        assertEquals(TankStatus.FULL, added.tankStatus); assertFalse(added.missedEntry); assertNull(added.sourceId)
        assertEquals(DEFAULT_VEHICLE_ID, added.vehicleId)
    }
    @Test fun thousandRecordHistoryCanJumpScrollAndSearchUsingTheSameIndex() {
        ready()
        val today = LocalDate.now().toString()
        runBlocking { store.dao.importFuel((0 until 1000).map { i -> Fuel(id = "load-$i", date = today, odometer = (2000 + i * 10).toString(), totalCost = "100", pricePerLitre = "100", litres = "1", station = "Load Test", notes = "Synthetic large-history $i") }) }
        compose.waitUntil(15_000) { model.data.value.fuel.size == 1003 && model.filteredHistory.value.fuel.size == 1003 }
        val index = model.data.value.index
        nav("History")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1000)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Search history").performTextInput("Synthetic large-history 777")
        compose.waitUntil(10_000) { model.filteredHistory.value.fuel.map { it.id } == listOf("load-777") }
        assertSame(index, model.data.value.index)
        val resultNote = hasText("Synthetic large-history 777") and !hasSetTextAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(resultNote)
        compose.onNode(resultNote).assertIsDisplayed()
        nav("Home"); nav("Analytics"); nav("History")
        assertEquals("Synthetic large-history 777", model.historyFilter.value.query)
    }
    @Test fun appearanceChangesReusePreparedHistoryAndReducedMotionPersists() {
        ready()
        val index = model.data.value.index; val insights = model.data.value.insights
        compose.onNodeWithContentDescription("Toggle theme").performClick()
        compose.waitUntil(10_000) { !model.data.value.preferences.darkTheme }
        assertSame(index, model.data.value.index); assertSame(insights, model.data.value.insights)
        nav("Settings")
        compose.onNodeWithContentDescription("Reduced motion").performScrollTo().performClick()
        compose.waitUntil(10_000) { model.data.value.preferences.reduceMotion }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithContentDescription("Reduced motion").assertIsOn()
        nav("History"); compose.onNodeWithText("Search history").assertExists()
        assertEquals(3, model.data.value.fuel.size)
    }
    @Test fun garageCreatesRenamesAndRemovesOnlyAnEmptyVehicle() {
        ready()
        compose.onNodeWithText("Garage").performClick()
        compose.onNodeWithContentDescription("Delete My Vehicle").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Delete Test Bike").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Add vehicle").performClick()
        compose.onNodeWithText("Vehicle name").performTextInput("Weekend Car")
        compose.onNodeWithText("Model (optional)").performTextInput("Synthetic EV")
        compose.onNodeWithText("Save Vehicle").performClick()
        compose.waitUntil(10_000) { model.data.value.vehicles.size == 3 && model.data.value.activeVehicle.name == "Weekend Car" }
        val vehicleId = model.data.value.activeVehicle.id
        assertTrue(model.data.value.fuel.isEmpty())
        compose.onNodeWithContentDescription("Edit Weekend Car").performClick()
        compose.onNodeWithText("Vehicle name").performTextReplacement("City Car")
        compose.onNodeWithText("Save Vehicle").performClick()
        compose.waitUntil(10_000) { model.data.value.activeVehicle.name == "City Car" }
        assertEquals(vehicleId, model.data.value.activeVehicle.id)
        compose.onNodeWithContentDescription("Delete City Car").performScrollTo().performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(10_000) { model.data.value.vehicles.size == 2 && model.data.value.activeVehicle.id != vehicleId }
        compose.onNodeWithContentDescription("Close garage").performClick()
        assertEquals(4, runBlocking { store.dao.fuel().size })
        assertEquals(2, runBlocking { store.dao.vehicles().size })
    }
}
