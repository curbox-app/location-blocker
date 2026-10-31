package neth.iecal.curbox.clocationblocker

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import neth.iecal.curbox.clocationblocker.ui.theme.LocationBlockerTheme

/** Exported only for Curbox's authenticated, one-attempt result handoff. */
class CheckLocationActivity : ComponentActivity() {
    private lateinit var place: Place
    private lateinit var requestId: String
    private lateinit var expectedCaller: String
    private var state by mutableStateOf<CheckState?>(null)
    private val busy get() = state is CheckState.Checking
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (LocationReader(this).permitted()) checkLocation()
        else state = CheckState.Done(Verdict(null, "Location Blocker needs precise location. Turn it on in the app's settings."))
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
        enableEdgeToEdge()
        setContent { LocationBlockerTheme {
            Scaffold { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    Text(if (place.inverse) "Checking you're away from" else "Checking you're at", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(place.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(32.dp))
                    CheckView(place, state ?: CheckState.Checking(null))
                    Spacer(Modifier.weight(1f))
                    val passed = (state as? CheckState.Done)?.verdict?.problem == null
                    if (state is CheckState.Done && !passed)
                        Button(onClick = { begin() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Try again") }
                    if (state !is CheckState.Done || !passed)
                        TextButton(onClick = { finish() }, Modifier.fillMaxWidth()) { Text("Keep apps blocked") }
                }
            }
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
        state = CheckState.Checking(null)
        lifecycleScope.launch {
            try {
                val verdict = verifyLocation(LocationReader(this@CheckLocationActivity), place) { state = CheckState.Checking(it) }
                // Re-read local authoritative state immediately before approving this attempt.
                val store = PlaceStore(this@CheckLocationActivity)
                val stillValid = store.curboxPackage == expectedCaller && store.places().any { it == place } && place.inHoursNow()
                if (verdict.problem == null && stillValid) {
                    setResult(RESULT_OK, Intent().putExtra("request_id", requestId))
                    state = CheckState.Done(verdict)
                    delay(700); finish()
                } else state = CheckState.Done(if (verdict.problem != null) verdict
                    else Verdict(verdict.reading, "This place was changed. Start again from Curbox."))
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { state = CheckState.Done(Verdict(null, "Couldn't check your location. Apps stay blocked.")) }
        }
    }
}
