package neth.iecal.curbox.clocationblocker

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import neth.iecal.curbox.clocationblocker.ui.theme.LocationBlockerTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var client: CurboxClient
    private lateinit var store: PlaceStore
    private var message by mutableStateOf<String?>(null)
    private var locationAllowed by mutableStateOf(false)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationAllowed = LocationReader(this).permitted()
        if (!locationAllowed) message = "Precise location is required. Enable it in Android app settings."
    }
    private val approval = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        lifecycleScope.launch { message = runCatching {
            if (client.ready()) "Curbox access approved. You can now publish places." else "Connect to Curbox first."
        }.getOrElse { it.message } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PlaceStore(this); client = CurboxClient(this)
        store.curboxPackage?.let(client::connect)
        enableEdgeToEdge()
        setContent { LocationBlockerTheme { Home() } }
    }
    override fun onResume() { super.onResume(); locationAllowed = LocationReader(this).permitted() }
    override fun onDestroy() { client.close(); super.onDestroy() }

    @Composable
    private fun Home() {
        var places by remember { mutableStateOf(store.places()) }
        var editor by remember { mutableStateOf(false) }
        var setup by remember { mutableStateOf(places.isEmpty()) }
        var busy by remember { mutableStateOf(false) }
        var delete by remember { mutableStateOf<Place?>(null) }
        var preview by remember { mutableStateOf<Place?>(null) }
        val scope = rememberCoroutineScope()
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("LOCATION BLOCKER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Stay in your place.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Text("Your blocked apps can unlock only when you’re inside an allowed place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${places.size} saved ${if (places.size == 1) "place" else "places"}", style = MaterialTheme.typography.titleMedium)
                        Text("Checked when you unlock. Existing access lasts until the interval you set in Curbox ends.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { setup = !setup }) { Text(if (setup) "Hide setup" else "Setup & connection") }
                    }
                }
                if (setup) {
                    Text("1. Connect Curbox", style = MaterialTheme.typography.titleLarge)
                    Text(client.status, style = MaterialTheme.typography.bodyMedium)
                    val installations = remember { client.installations() }
                    if (installations.isEmpty()) Text("Install Curbox with API version 3 or newer, then reopen this app.")
                    installations.forEach { (pkg, label) ->
                        OutlinedButton(onClick = { store.curboxPackage = pkg; client.connect(pkg) }, enabled = !busy) {
                            Column { Text(label); Text(pkg, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    Text("Curbox approval grants this app the full public API, including blocker controls and usage totals. This app uses it to register location checks.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        val pkg = store.curboxPackage
                        if (pkg == null) message = "Choose a Curbox installation first."
                        else runCatching { approval.launch(Intent("neth.iecal.curbox.api.REQUEST_PERMISSION").setPackage(pkg)) }
                            .onFailure { message = "Curbox’s approval screen is unavailable. Reconnect to try again." }
                    }) { Text("Approve in Curbox") }
                    Text("2. Allow precise location", style = MaterialTheme.typography.titleLarge)
                    Text("Location is requested only while saving or checking a place. No background tracking, account, or internet connection.", style = MaterialTheme.typography.bodyMedium)
                    if (locationAllowed) Text("Precise location allowed")
                    else OutlinedButton(onClick = { permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Allow location") }
                    TextButton(onClick = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Android location settings") }
                    Text("3. Attach your place", style = MaterialTheme.typography.titleLarge)
                    Text("Publish a place below. In Curbox, open your blocker group → Warning Screen Configuration → Require effort to unlock → Require another app → select “At [place]”. Enable the group and Curbox’s accessibility service.")
                    Text("Keep Location Blocker outside your blocked apps. For frequent checks, use Curbox’s “On each open” behavior or a short unlock interval.", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Allowed places", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { editor = true }, enabled = !busy && places.size < 50) { Text("+ Add place") }
                }
                if (places.isEmpty()) {
                    Text("Start with home, your library, or your workplace.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { editor = true }) { Text("Add your first place") }
                }
                places.forEach { p ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge)
                            Text("${p.radius} m radius · ±${p.accuracy} m accuracy required", style = MaterialTheme.typography.bodyMedium)
                            Text(String.format(Locale.US, "%.5f, %.5f", p.latitude, p.longitude), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (p.timed) Text("${daySummary(p.days)} · ${clock(p.startMinute)}–${clock(p.endMinute)}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !busy, onClick = { preview = p }) { Text("Check here") }
                                TextButton(enabled = !busy, onClick = {
                                    busy = true
                                    scope.launch {
                                        message = runCatching { registrationMessage(client.register(p)) }.getOrElse { it.message }
                                        busy = false
                                    }
                                }) { Text("Publish to Curbox") }
                            }
                            TextButton(enabled = !busy, onClick = { delete = p }) { Text("Remove place", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { text ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) { Text(text); TextButton(onClick = { message = null }) { Text("Dismiss") } }
                    }
                }
                Text("Places stay on this device. To change a place, add a replacement and select it again in Curbox. Removing a selected place keeps its apps blocked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (editor) PlaceEditor(onDismiss = { editor = false }, onSave = { place ->
            if (store.save(places + place)) { places = store.places(); editor = false; message = "Place saved. Tap Publish to Curbox, then select it in your group’s warning settings." }
            else message = "Could not save your place. Please try again."
        })
        delete?.let { p -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Remove ${p.name}?") },
            text = { Text("Any Curbox group using this place will stay blocked. Choose a replacement in Curbox. Your other places are unaffected.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    // Removing local data fails closed even if the remote registration is unavailable.
                    val remote = runCatching { client.remove(p) }.getOrNull()
                    if (store.save(places.filterNot { it.id == p.id })) {
                        places = store.places(); delete = null
                        message = if (remote == "OK") "Place removed." else "Place removed locally. Its Curbox registration could not be removed, but this app will deny checks for it."
                    } else message = "Could not remove local data. Try again."
                    busy = false
                }
            }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } }) }
        preview?.let { place -> CheckDialog(place, onDismiss = { preview = null }) }
    }
}

fun registrationMessage(result: String) = when (result) {
    "OK" -> "Published. In Curbox’s warning settings, select this place under Require another app. Publishing alone does not enable blocking."
    "DENIED" -> "Curbox access was denied. Approve this app again in Setup & connection."
    "UNKNOWN_COMMAND" -> "This Curbox installation does not support location requirements. Use API version 3 or newer."
    else -> "Curbox could not publish this place. Check API access and the 50-requirement limit."
}
fun clock(minute: Int) = "%02d:%02d".format(Locale.US, minute / 60, minute % 60)
fun daySummary(days: Set<Int>) = if (days.size == 7) "Every day" else listOf(2,3,4,5,6,7,1).filter { it in days }.joinToString(" ") { listOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[it] }
