package com.arindam.mileage

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Synthetic histories only. These assertions exercise actual Compose navigation and form actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h852dp-xhdpi", application = MileageApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReferenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun seed() = runBlocking {
        val dao = (compose.activity.application as MileageApp).store.dao
        dao.setPreferences(Preferences())
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        dao.add(Fuel(id = "example-first", date = month.minusMonths(1).atDay(4).toString(), odometer = "1000", totalCost = "1000", pricePerLitre = "100", litres = "10", station = "IndianOil", notes = "Synthetic screenshot fixture"))
        dao.add(Fuel(id = "example-second", date = month.atDay(1).toString(), odometer = "1240", totalCost = "1018", pricePerLitre = "101.80", litres = "10", station = "Bharat Petroleum"))
        dao.add(Fuel(id = "example-third", date = today.toString(), odometer = "1500", totalCost = "1227", pricePerLitre = "102.25", litres = "12", station = "IndianOil"))
        dao.add(Maintenance(id = "example-service", date = today.toString(), serviceType = "Oil & filter change", odometer = "1500", totalCost = "850", notes = "Synthetic service record"))
    }
    private fun loaded() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("₹2,245").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun referenceNavigationHistoryAndBackupsRemainReachable() {
        loaded()
        compose.onNodeWithText("Dashboard").assertIsDisplayed()
        screenshot("dashboard-dark")
        compose.onNodeWithText("History", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Fuel Fillups").assertIsDisplayed()
        screenshot("history-dark")
        compose.onNodeWithText("Maintenance", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Oil & filter change").assertExists()
        compose.onNodeWithText("Analytics", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Insights & Analytics").assertIsDisplayed()
        compose.onNodeWithText("This Month").assertIsDisplayed()
        screenshot("analytics-dark")
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        screenshot("settings-dark")
        listOf("Import Fuel CSV", "Import Maintenance CSV", "Restore full JSON backup", "Export maintenance history CSV").forEach { label ->
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }
    @Test fun addEntryChooserOpensNativeFuelAndServiceForms() {
        loaded()
        compose.onNodeWithText("Add Entry").performClick()
        compose.onNodeWithText("Fuel Fill-up").performClick()
        compose.onNodeWithText("Add Fuel Entry").assertIsDisplayed()
        compose.onNodeWithText("Odometer (km)").performTextReplacement("1700")
        compose.onNodeWithText("Litres").performTextReplacement("10")
        compose.onNodeWithText("Price per litre (INR)").performTextReplacement("100")
        compose.onNodeWithText("Save Entry").performClick()
        compose.waitForIdle()
        val store = (compose.activity.application as MileageApp).store
        compose.waitUntil(10_000) { runBlocking { store.dao.fuel().size == 4 } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Dashboard").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add Entry").performClick()
        compose.onNodeWithText("Maintenance", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Add Service Log").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Dashboard").assertIsDisplayed()
    }
    @Test fun themeSurvivesSettingsSaveAndActivityRecreation() {
        loaded()
        compose.onNodeWithContentDescription("Toggle theme").assertIsEnabled().performClick()
        compose.waitForIdle()
        val store = (compose.activity.application as MileageApp).store
        compose.waitUntil(10_000) { runBlocking { store.dao.preferences()?.darkTheme == false } }
        screenshot("dashboard-light")
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithText("₹ (INR)").performClick()
        compose.onNodeWithText("$ (USD)").performClick()
        compose.onNodeWithText("Save Settings").performScrollTo().performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) { runBlocking { store.dao.preferences()?.currency == "USD" } }
        org.junit.Assert.assertFalse(runBlocking { store.dao.preferences()!!.darkTheme })
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onAllNodesWithText("Settings", useUnmergedTree = true).assertCountEquals(2)
        compose.onNode(isToggleable()).assertIsOff()
        org.junit.Assert.assertFalse(runBlocking { store.dao.preferences()!!.darkTheme })
        org.junit.Assert.assertEquals(3, runBlocking { store.dao.fuel().size })
    }
    private fun screenshot(name: String) {
        val file = File("build/reports/ui/$name.png")
        file.parentFile!!.mkdirs()
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
