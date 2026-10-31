package neth.iecal.curbox.clocationblocker

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationBlockerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun setupAndEditorExplainTheirActions() {
        compose.onNodeWithText("Location Blocker").assertIsDisplayed()
        compose.onAllNodes(hasText("Add a place") or hasText("Add place")).onFirst().performClick()
        compose.onNodeWithText("New place").assertIsDisplayed()
        compose.onNodeWithText("Save place").performClick()
        compose.onNodeWithText("Give this place a name.").assertExists()
        compose.onNodeWithContentDescription("Cancel").performClick()
    }

    @Test fun placesRoundTripWithoutChangingRequirementMeaning() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        // Isolate persistence tests from real provider configuration.
        val isolated = object : android.content.ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("test_$name", mode)
        }
        val store = PlaceStore(isolated)
        val place = Place(name = "Library", latitude = -12.345, longitude = 77.123, radius = 300,
            timed = true, startMinute = 1320, endMinute = 360, days = setOf(2, 7))
        try {
            assertTrue(store.save(listOf(place)))
            assertEquals(listOf(place), PlaceStore(isolated).places())
            assertTrue(store.save(emptyList()))
            assertTrue(store.places().isEmpty())
        } finally { isolated.getSharedPreferences("places", Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun exportedCheckRejectsUnverifiedCaller() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, CheckLocationActivity::class.java)
            .setAction("neth.iecal.curbox.api.CHECK_UNLOCK_CONDITION")
            .putExtra("condition_id", "not_a_saved_place").putExtra("request_id", "forged")
        ActivityScenario.launch<CheckLocationActivity>(intent).use {
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, it.state)
        }
    }
}
