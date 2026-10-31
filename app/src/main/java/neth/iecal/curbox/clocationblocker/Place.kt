package neth.iecal.curbox.clocationblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.Calendar
import kotlin.math.roundToInt

/** A published place is immutable: changing its meaning requires a new requirement ID. */
data class Place(
    val id: String = "place_" + UUID.randomUUID().toString().replace("-", ""),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int,
    val accuracy: Int = defaultAccuracy(radius),
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

/** The loosest location precision accepted for an area: rough enough to work indoors, tight enough to mean something. */
fun defaultAccuracy(radius: Int) = (radius / 2).coerceIn(20, 100).coerceAtMost(radius - 10)

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
    const val MAX_AGE_MS = 30_000L

    /**
     * Half of the reported uncertainty must still fit inside the area. Requiring all of it made
     * honest indoor readings fail near the middle; ignoring it lets a rough fix near the edge pass.
     */
    fun rejection(place: Place, distance: Float, accuracy: Float, ageMs: Long, mock: Boolean): String? = when {
        !place.inHoursNow() -> "${place.name} only counts during the times you set. Apps stay blocked for now."
        place.rejectMock && mock -> "A fake location app is running. Turn it off and try again."
        ageMs < 0 || ageMs > MAX_AGE_MS -> "Your phone gave an old location. Try again."
        !accuracy.isFinite() || accuracy <= 0 -> "Couldn't get a clear location. Move near a window or step outside."
        accuracy > place.accuracy -> "Your location is too rough right now (±${accuracy.roundToInt()} m). Move near a window or step outside."
        !distance.isFinite() -> "Couldn't work out where you are. Try again."
        distance + accuracy / 2 <= place.radius -> null
        distance <= place.radius -> "You're right at the edge of ${place.name}. Move a little closer to the middle."
        else -> "You're about ${meters(distance)} from ${place.name}. You need to be within ${meters(place.radius.toFloat())}."
    }
}

fun meters(value: Float): String = when {
    value >= 10_000 -> "${(value / 1000).roundToInt()} km"
    value >= 1000 -> "%.1f km".format(java.util.Locale.US, value / 1000)
    value >= 100 -> "${(value / 10).roundToInt() * 10} m"
    else -> "${value.roundToInt()} m"
}
