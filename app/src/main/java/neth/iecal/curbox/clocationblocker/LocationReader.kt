package neth.iecal.curbox.clocationblocker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

class LocationReader(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    fun permitted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun enabled() = runCatching { LocationManagerCompat.isLocationEnabled(manager) }.getOrDefault(false)

    private fun providers(): List<String> = buildList {
        add(LocationManager.GPS_PROVIDER); add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
    }.filter { runCatching { it in manager.allProviders && manager.isProviderEnabled(it) }.getOrDefault(false) }

    /**
     * Live fixes from every enabled provider at once, so a quick network fix shows up while GPS is
     * still warming up. Cached fixes are never used: they can predate walking out of the area.
     */
    @SuppressLint("MissingPermission")
    private fun fixes(): Flow<Location> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { trySend(location) }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Legacy callback")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        val providers = providers()
        try {
            providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
            if (providers.isEmpty()) close()
        } catch (_: Exception) { close() }
        awaitClose { manager.removeUpdates(listener) }
    }.filter { it.fresh() }

    /**
     * Listens until [done] accepts a fix, then returns it. If time runs out first, returns the most
     * precise fix seen, so a slow GPS lock still gets judged on its best reading rather than its first.
     */
    suspend fun sample(timeoutMs: Long, onBest: (Location) -> Unit = {}, done: (Location) -> Boolean): Location? {
        if (!permitted()) return null
        var best: Location? = null
        val accepted = withTimeoutOrNull(timeoutMs) {
            fixes().firstOrNull { fix ->
                if (best.let { it == null || fix.accuracy <= it.accuracy }) { best = fix; onBest(fix) }
                done(fix)
            }
        }
        return accepted ?: best?.takeIf { it.fresh() }
    }

    /**
     * Averages several precise fixes into one center, weighted by precision, so a saved place
     * isn't thrown off by a single noisy reading. [stop] ends sampling early with what's collected.
     */
    suspend fun center(onBest: (Location) -> Unit = {}, stop: () -> Boolean = { false }): Location? {
        if (!permitted()) return null
        val samples = mutableListOf<Location>()
        withTimeoutOrNull(30_000) {
            fixes().firstOrNull { fix ->
                if (!fix.isMockFix()) { samples += fix; onBest(samples.minBy { it.accuracy }) }
                stop() || samples.count { it.accuracy <= 12f } >= 5
            }
        }
        val best = samples.minByOrNull { it.accuracy } ?: return null
        val good = samples.filter { it.accuracy <= max(best.accuracy * 1.5f, 10f) }
        val weights = good.map { 1.0 / (it.accuracy.toDouble() * it.accuracy) }
        val total = weights.sum()
        return Location(best).apply {
            latitude = good.indices.sumOf { good[it].latitude * weights[it] } / total
            longitude = good.indices.sumOf { good[it].longitude * weights[it] } / total
        }
    }
}

fun Location.isMockFix(): Boolean = if (Build.VERSION.SDK_INT >= 31) isMock else @Suppress("DEPRECATION") isFromMockProvider
fun Location.ageMs(): Long = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000
private fun Location.fresh() = hasAccuracy() && accuracy.isFinite() && accuracy > 0 && ageMs() in 0..LocationPolicy.MAX_AGE_MS

/** Where a fix sits relative to a place: [bearing] is degrees clockwise from north, seen from the center. */
data class Reading(val distance: Float, val bearing: Float, val accuracy: Float)
data class Verdict(val reading: Reading?, val problem: String?)

fun Location.readingFor(place: Place): Reading {
    val result = FloatArray(2)
    Location.distanceBetween(place.latitude, place.longitude, latitude, longitude, result)
    return Reading(result[0], result[1], if (hasAccuracy()) accuracy else Float.NaN)
}

/** Keeps listening while the answer is "no", so a fix that improves over a few seconds can still pass. */
suspend fun verifyLocation(reader: LocationReader, place: Place, onReading: (Reading) -> Unit = {}): Verdict {
    if (!place.inHoursNow()) return Verdict(null, "${place.name} only counts during the times you set. Apps stay blocked for now.")
    if (!reader.permitted()) return Verdict(null, "Location Blocker needs precise location. Turn it on in the app's settings.")
    if (!reader.enabled()) return Verdict(null, "Your phone's location is off. Turn it on and try again.")
    fun judge(fix: Location) = fix.readingFor(place).let { LocationPolicy.rejection(place, it.distance, it.accuracy, fix.ageMs(), fix.isMockFix()) }
    val fix = reader.sample(20_000, onBest = { onReading(it.readingFor(place)) }) { judge(it) == null }
        ?: return Verdict(null, "Couldn't find your location. Move near a window or step outside and try again.")
    return Verdict(fix.readingFor(place), judge(fix))
}
