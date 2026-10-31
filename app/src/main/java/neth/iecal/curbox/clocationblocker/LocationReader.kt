package neth.iecal.curbox.clocationblocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationReader(private val context: Context) {
    fun permitted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    suspend fun read(): Location? = withTimeoutOrNull(25_000) {
        if (!permitted()) return@withTimeoutOrNull null
        suspendCancellableCoroutine { continuation ->
            val manager = context.getSystemService(LocationManager::class.java)
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) { manager.removeUpdates(this); continuation.resume(location) }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Legacy callback")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            try {
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .filter { manager.isProviderEnabled(it) }
                if (providers.isEmpty()) continuation.resume(null)
                else providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
            } catch (_: SecurityException) {
                manager.removeUpdates(listener)
                if (continuation.isActive) continuation.resume(null)
            } catch (_: Exception) {
                manager.removeUpdates(listener)
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}
