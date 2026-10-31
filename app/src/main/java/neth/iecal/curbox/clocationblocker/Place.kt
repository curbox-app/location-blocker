package neth.iecal.curbox.clocationblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.Calendar

/** A published place is immutable: changing its meaning requires a new requirement ID. */
data class Place(
    val id: String = "place_" + UUID.randomUUID().toString().replace("-", ""),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int,
    val accuracy: Int = 50,
    val rejectMock: Boolean = true,
    val timed: Boolean = false,
    val startMinute: Int = 540,
    val endMinute: Int = 1080,
    val days: Set<Int> = (1..7).toSet()
) {
    fun inHours(day: Int, minute: Int): Boolean {
        if (!timed) return true
        if (startMinute == endMinute) return day in days
        return if (startMinute < endMinute) day in days && minute >= startMinute && minute < endMinute
        else (day in days && minute >= startMinute) ||
            ((if (day == 1) 7 else day - 1) in days && minute < endMinute)
    }
    fun inHoursNow(): Boolean = Calendar.getInstance().let {
        inHours(it.get(Calendar.DAY_OF_WEEK), it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE))
    }
}

class PlaceStore(context: Context) {
    private val prefs = context.getSharedPreferences("places", Context.MODE_PRIVATE)
    var curboxPackage: String?
        get() = prefs.getString("curbox", null)
        set(value) { prefs.edit().putString("curbox", value).commit() }
    fun places(): List<Place> = try {
        val array = JSONArray(prefs.getString("places", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { j ->
            Place(j.getString("id"), j.getString("name"), j.getDouble("lat"), j.getDouble("lon"),
                j.getInt("radius"), j.getInt("accuracy"), j.getBoolean("rejectMock"),
                j.getBoolean("timed"), j.getInt("start"), j.getInt("end"),
                j.getString("days").split(",").filter { it.isNotEmpty() }.map { it.toInt() }.toSet())
        } }
    } catch (_: Exception) { emptyList() }
    fun save(places: List<Place>): Boolean = prefs.edit().putString("places", JSONArray().apply {
        places.forEach { p -> put(JSONObject().apply {
            put("id", p.id); put("name", p.name); put("lat", p.latitude); put("lon", p.longitude)
            put("radius", p.radius); put("accuracy", p.accuracy); put("rejectMock", p.rejectMock)
            put("timed", p.timed); put("start", p.startMinute); put("end", p.endMinute)
            put("days", p.days.sorted().joinToString(","))
        }) }
    }.toString()).commit()
}

object LocationPolicy {
    fun rejection(place: Place, distance: Float, accuracy: Float, ageMs: Long, mock: Boolean): String? = when {
        !place.inHoursNow() -> "Outside allowed hours. Access stays blocked."
        place.rejectMock && mock -> "Mock location detected. Use a real device location."
        ageMs < 0 || ageMs > 30_000 -> "Location is too old. Try again outdoors."
        !accuracy.isFinite() || accuracy <= 0 || accuracy > place.accuracy -> "Location is not precise enough. Move near a window or outdoors."
        !distance.isFinite() || distance + accuracy > place.radius -> "You are outside the verified area, or too close to its edge."
        else -> null
    }
}
