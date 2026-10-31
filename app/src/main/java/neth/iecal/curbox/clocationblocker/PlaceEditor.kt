package neth.iecal.curbox.clocationblocker

import android.Manifest
import android.os.SystemClock
import android.os.Build
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PlaceEditor(onDismiss: () -> Unit, onSave: (Place) -> Unit) {
    val context = LocalContext.current
    val reader = remember { LocationReader(context) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("200") }
    var accuracy by remember { mutableStateOf("50") }
    var rejectMock by remember { mutableStateOf(true) }
    var timed by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("18:00") }
    var days by remember { mutableStateOf((1..7).toSet()) }
    var advanced by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun locate() {
        busy = true; error = "Finding a fresh location…"
        scope.launch {
            try {
                val fix = reader.read()
                if (fix == null) error = "No fresh location. Turn on Android location and try outdoors."
                else if (!fix.hasAccuracy() || fix.accuracy > 100 || fix.isMockFix() || fix.ageMs() !in 0..30_000) error = "Could not get a precise, real location. Try again outdoors or enter coordinates."
                else { lat = "%.7f".format(Locale.US, fix.latitude); lon = "%.7f".format(Locale.US, fix.longitude); error = "Location captured · accuracy ±${fix.accuracy.toInt()} m" }
            } finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (reader.permitted()) locate() else error = "Allow precise location in Android app settings, or enter coordinates manually."
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Add an allowed place") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Give it a name, choose its center, and set how far you can be from it.")
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Place name") }, placeholder = { Text("Library") }, singleLine = true)
                OutlinedButton(enabled = !busy, onClick = {
                    if (reader.permitted()) locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text(if (busy) "Finding location…" else "Use my current location") }
                OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(radius, { radius = it }, label = { Text("Allowed radius (meters)") }, supportingText = { Text("50–10,000 m. Start with 200 m for a building.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced options" else "Advanced options") }
                if (advanced) {
                    OutlinedTextField(accuracy, { accuracy = it }, label = { Text("Maximum uncertainty (meters)") }, supportingText = { Text("10–200 m, smaller than the radius. Smaller values require a better signal.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    SettingSwitch("Reject mock locations", "Deny fixes marked as simulated by Android.", rejectMock) { rejectMock = it }
                    SettingSwitch("Limit access hours", "Outside these hours, access stays blocked even at this place.", timed) { timed = it }
                    if (timed) {
                        Text("Days", style = MaterialTheme.typography.labelLarge)
                        listOf(listOf(2,3,4,5), listOf(6,7,1)).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { day -> FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day }, label = { Text(daySummary(setOf(day))) }) }
                            }
                        }
                        OutlinedTextField(start, { start = it }, label = { Text("From (24-hour HH:mm)") }, singleLine = true)
                        OutlinedTextField(end, { end = it }, label = { Text("Until (24-hour HH:mm)") }, singleLine = true)
                        Text("Uses your device’s local time. Overnight hours end the next day. Equal times mean a full day.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = {
            val latitude = lat.toDoubleOrNull(); val longitude = lon.toDoubleOrNull()
            val meters = radius.toIntOrNull(); val precision = accuracy.toIntOrNull()
            val from = parseClock(start); val until = parseClock(end)
            error = when {
                name.isBlank() -> "Name this place first."
                latitude == null || !latitude.isFinite() || latitude !in -90.0..90.0 -> "Latitude must be between −90 and 90."
                longitude == null || !longitude.isFinite() || longitude !in -180.0..180.0 -> "Longitude must be between −180 and 180."
                meters == null || meters !in 50..10000 -> "Choose a radius from 50 to 10,000 meters."
                precision == null || precision !in 10..200 || precision >= meters -> "Maximum uncertainty must be 10–200 meters and smaller than the radius."
                timed && (from == null || until == null || days.isEmpty()) -> "Choose at least one day and valid HH:mm times."
                else -> null
            }
            if (error == null) onSave(Place(name = name.trim(), latitude = latitude!!, longitude = longitude!!, radius = meters!!,
                accuracy = precision!!, rejectMock = rejectMock, timed = timed, startMinute = from ?: 540, endMinute = until ?: 1080, days = days))
        }) { Text("Save place") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun SettingSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = change)
    }
}
fun parseClock(value: String): Int? {
    if (!value.matches(Regex("[0-2][0-9]:[0-5][0-9]"))) return null
    val h = value.substring(0, 2).toInt(); val m = value.substring(3).toInt()
    return if (h < 24) h * 60 + m else null
}
fun Location.isMockFix(): Boolean = if (Build.VERSION.SDK_INT >= 31) isMock else @Suppress("DEPRECATION") isFromMockProvider
fun Location.ageMs(): Long = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000

suspend fun verifyLocation(reader: LocationReader, place: Place): String? {
    if (!place.inHoursNow()) return "Outside allowed hours. Access stays blocked."
    if (!reader.permitted()) return "Precise location permission is required. Allow it in Location Blocker’s app settings."
    val fix = reader.read() ?: return "Location unavailable. Turn on Android location and try near a window or outdoors."
    val distance = FloatArray(1)
    Location.distanceBetween(fix.latitude, fix.longitude, place.latitude, place.longitude, distance)
    return LocationPolicy.rejection(place, distance[0], if (fix.hasAccuracy()) fix.accuracy else Float.NaN, fix.ageMs(), fix.isMockFix())
}

@Composable
fun CheckDialog(place: Place, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { result = null; result = verifyLocation(LocationReader(context), place) ?: "Inside ${place.name}. A Curbox check would approve access now." }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Check ${place.name}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(result ?: "Verifying your current location…")
            if (result == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("This test does not unlock any apps.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }, dismissButton = {
        TextButton(enabled = result != null, onClick = { attempt++ }) { Text("Try again") }
    })
}
