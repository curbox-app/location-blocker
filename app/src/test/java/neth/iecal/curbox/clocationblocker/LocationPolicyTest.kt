package neth.iecal.curbox.clocationblocker

import org.junit.Assert.*
import org.junit.Test

class LocationPolicyTest {
    private val place = Place(name = "Library", latitude = 12.0, longitude = 77.0, radius = 200)
    @Test fun uncertaintyMustFitEntirelyInsideBoundary() {
        assertNull(LocationPolicy.rejection(place, 150f, 50f, 1000, false))
        assertNotNull(LocationPolicy.rejection(place, 151f, 50f, 1000, false))
        assertNotNull(LocationPolicy.rejection(place, 250f, 5f, 1000, false))
    }
    @Test fun staleFutureMockAndUncertainFixesAreDenied() {
        assertNotNull(LocationPolicy.rejection(place, 0f, 10f, 30_001, false))
        assertNotNull(LocationPolicy.rejection(place, 0f, 10f, -1, false))
        assertNotNull(LocationPolicy.rejection(place, 0f, 10f, 0, true))
        assertNotNull(LocationPolicy.rejection(place, 0f, 51f, 0, false))
        assertNotNull(LocationPolicy.rejection(place, 0f, Float.NaN, 0, false))
        assertNotNull(LocationPolicy.rejection(place, Float.NaN, 10f, 0, false))
        assertNotNull(LocationPolicy.rejection(place, 0f, 0f, 0, false))
    }
    @Test fun overnightBelongsToDayItStarted() {
        val overnight = place.copy(timed = true, days = setOf(2), startMinute = 22 * 60, endMinute = 6 * 60)
        assertTrue(overnight.inHours(2, 23 * 60))
        assertTrue(overnight.inHours(3, 5 * 60))
        assertFalse(overnight.inHours(2, 5 * 60))
        assertFalse(overnight.inHours(3, 6 * 60))
        assertFalse(overnight.inHours(3, 23 * 60))
    }
    @Test fun overnightWrapsWeek() {
        val overnight = place.copy(timed = true, days = setOf(7), startMinute = 1320, endMinute = 360)
        assertTrue(overnight.inHours(1, 60))
        assertFalse(overnight.inHours(2, 60))
    }
    @Test fun normalHoursAreStartInclusiveEndExclusive() {
        val scheduled = place.copy(timed = true, days = setOf(2), startMinute = 540, endMinute = 1080)
        assertTrue(scheduled.inHours(2, 540))
        assertFalse(scheduled.inHours(2, 1080))
        assertFalse(scheduled.inHours(3, 600))
        assertTrue(scheduled.copy(startMinute = 0, endMinute = 0).inHours(2, 1439))
    }
    @Test fun timeInputRejectsInvalidTimes() {
        assertEquals(1439, parseClock("23:59"))
        assertNull(parseClock("24:00"))
        assertNull(parseClock("9:00"))
        assertNull(parseClock("12:60"))
    }
}
