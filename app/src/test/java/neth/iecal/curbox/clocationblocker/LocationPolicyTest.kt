package neth.iecal.curbox.clocationblocker

import org.junit.Assert.*
import org.junit.Test

class LocationPolicyTest {
    private val place = Place(name = "Library", latitude = 12.0, longitude = 77.0, radius = 200)
    @Test fun halfOfUncertaintyMustFitInsideBoundary() {
        assertNull(LocationPolicy.rejection(place, 175f, 50f, 1000, false))
        assertNotNull(LocationPolicy.rejection(place, 176f, 50f, 1000, false))
        assertNotNull(LocationPolicy.rejection(place, 250f, 5f, 1000, false))
    }
    @Test fun defaultPrecisionScalesWithArea() {
        assertEquals(25, defaultAccuracy(50))
        assertEquals(75, defaultAccuracy(150))
        assertEquals(100, defaultAccuracy(1000))
        assertEquals(75, Place(name = "Gym", latitude = 0.0, longitude = 0.0, radius = 150).accuracy)
    }
    @Test fun staleFutureMockAndUncertainFixesAreDenied() {
        assertNotNull(LocationPolicy.rejection(place, 0f, 10f, 30_001, false))
        assertNotNull(LocationPolicy.rejection(place, 0f, 10f, -1, false))
        assertNotNull(LocationPolicy.rejection(place, 0f, 10f, 0, true))
        assertNotNull(LocationPolicy.rejection(place, 0f, 101f, 0, false))
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
    @Test fun coordinatesParseLikeMapAppsCopyThem() {
        assertEquals(12.9716 to 77.5946, parseCoordinates("12.9716, 77.5946"))
        assertEquals(-33.8 to 151.2, parseCoordinates(" (-33.8,151.2) "))
        assertEquals(1.0 to 2.0, parseCoordinates("1 2"))
        assertNull(parseCoordinates("91, 0"))
        assertNull(parseCoordinates("12.97"))
        assertNull(parseCoordinates("north, south"))
    }
}
