package neth.iecal.curbox.clocationblocker

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import neth.iecal.curbox.clocationblocker.ui.theme.LocationBlockerTheme

/** Exported only for Curbox's authenticated, one-attempt result handoff. */
class CheckLocationActivity : ComponentActivity() {
    private lateinit var place: Place
    private lateinit var requestId: String
    private lateinit var expectedCaller: String
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (LocationReader(this).permitted()) checkLocation()
        else error = "Precise location is required. Enable it in Android app settings and try again."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val store = PlaceStore(this)
        val id = intent.getStringExtra("condition_id")
        val challenge = intent.getStringExtra("request_id")
        val caller = store.curboxPackage
        val saved = store.places().firstOrNull { it.id == id }
        if (intent.action != "neth.iecal.curbox.api.CHECK_UNLOCK_CONDITION" || caller == null ||
            callingPackage != caller || saved == null || challenge.isNullOrBlank() || challenge.length > 100) {
            finish(); return
        }
        place = saved; requestId = challenge; expectedCaller = caller
        setContent { LocationBlockerTheme {
            Scaffold { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Spacer(Modifier.height(36.dp))
                Text("LOCATION CHECK", style = MaterialTheme.typography.labelMedium)
                Text("Be at ${place.name}.", style = MaterialTheme.typography.headlineLarge)
                Text("Access requires a fresh, precise location inside the ${place.radius} m area.")
                if (place.timed) Text("${daySummary(place.days)} · ${clock(place.startMinute)}–${clock(place.endMinute)}")
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Verifying your location…") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !busy, onClick = { begin() }) { Text(if (error == null) "Verify location" else "Try again") }
                TextButton(onClick = { finish() }) { Text("Keep apps blocked") }
                Text("Curbox controls how long access lasts after a successful check.", style = MaterialTheme.typography.bodySmall)
            } }
        } }
        if (savedInstanceState == null) begin()
    }
    private fun begin() {
        if (busy) return
        if (LocationReader(this).permitted()) checkLocation()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    private fun checkLocation() {
        if (busy) return
        busy = true; error = null
        lifecycleScope.launch {
            try {
                val rejection = verifyLocation(LocationReader(this@CheckLocationActivity), place)
                // Re-read local authoritative state immediately before approving this attempt.
                val store = PlaceStore(this@CheckLocationActivity)
                if (rejection == null && store.curboxPackage == expectedCaller &&
                    store.places().any { it == place } && place.inHoursNow()) {
                    setResult(RESULT_OK, Intent().putExtra("request_id", requestId)); finish()
                } else error = rejection ?: "The place configuration changed. Start a new check from Curbox."
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { error = "Could not verify location. Access stays blocked." }
            finally { busy = false }
        }
    }
}
