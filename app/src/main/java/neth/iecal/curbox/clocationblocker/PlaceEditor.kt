package neth.iecal.curbox.clocationblocker

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val SIZES = listOf("Small" to 75, "Medium" to 150, "Large" to 400)
private val NAME_IDEAS = listOf("Home", "Work", "School", "Library", "Gym")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaceEditor(onDismiss: () -> Unit, onSave: (Place) -> Unit) {
    val context = LocalContext.current
    val reader = remember { LocationReader(context) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var center by remember { mutableStateOf<Location?>(null) }
    var locating by remember { mutableStateOf<Job?>(null) }
    var live by remember { mutableStateOf<Location?>(null) }
    var stopEarly by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf("") }
    var radius by remember { mutableIntStateOf(150) }
    var precision by remember { mutableStateOf<Int?>(null) }
    var inverse by remember { mutableStateOf(false) }
    var rejectMock by remember { mutableStateOf(true) }
    var timed by remember { mutableStateOf(false) }
    var start by remember { mutableIntStateOf(540) }
    var end by remember { mutableIntStateOf(1080) }
    var days by remember { mutableStateOf(setOf(2, 3, 4, 5, 6)) }
    var more by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Boolean?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val accuracy = (precision ?: defaultAccuracy(radius)).coerceAtMost(radius - 10)

    fun locate() {
        error = null; live = null; center = null; stopEarly = false
        locating = scope.launch {
            try {
                val fix = reader.center(onBest = { live = it }, stop = { stopEarly })
                when {
                    fix == null -> error = if (!reader.enabled()) "Your phone's location is off. Turn it on and try again."
                        else "Couldn't find your location. Move near a window or step outside, or type the coordinates."
                    fix.accuracy > 100 -> error = "Your location is too rough (±${fix.accuracy.roundToInt()} m). Move near a window or step outside and try again."
                    else -> center = fix
                }
            } finally { locating = null }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (reader.permitted()) locate() else error = "Location Blocker needs precise location for this. You can also type the coordinates."
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("New place") }, navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cancel") }
                })
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                        Button(modifier = Modifier.fillMaxWidth().height(52.dp), enabled = locating == null, onClick = {
                            val manual = if (typed) parseCoordinates(coordinates) else null
                            val lat = manual?.first ?: center?.latitude
                            val lon = manual?.second ?: center?.longitude
                            error = when {
                                name.isBlank() -> "Give this place a name."
                                typed && manual == null -> "Coordinates should look like 12.97160, 77.59460"
                                lat == null || lon == null -> "Set where this place is first."
                                timed && days.isEmpty() -> "Pick at least one day."
                                else -> null
                            }
                            if (error == null) onSave(Place(name = name.trim(), latitude = lat!!, longitude = lon!!, radius = radius,
                                accuracy = accuracy, rejectMock = rejectMock, timed = timed, startMinute = start, endMinute = end,
                                days = if (timed) days else (1..7).toSet(), inverse = inverse))
                        }) { Text("Save place") }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)) {

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Name")
                    OutlinedTextField(name, { name = it.take(60); error = null }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. Library") },
                        singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done))
                    if (name.isBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NAME_IDEAS.forEach { idea -> SuggestionChip(onClick = { name = idea }, label = { Text(idea) }) }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Blocked apps can be unlocked")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Only here" to false, "Anywhere but here" to true).forEachIndexed { i, (label, value) ->
                            SegmentedButton(selected = inverse == value, onClick = { inverse = value },
                                shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                        }
                    }
                    Text(if (inverse) "Use this to keep apps locked at a place like work or the library."
                        else "Apps stay locked everywhere else.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Where")
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when {
                                typed -> {
                                    OutlinedTextField(coordinates, { coordinates = it; error = null }, Modifier.fillMaxWidth(),
                                        label = { Text("Latitude, longitude") }, placeholder = { Text("12.97160, 77.59460") }, singleLine = true,
                                        supportingText = { Text("Tip: long-press a spot in Google Maps to copy these.") },
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
                                }
                                locating != null -> {
                                    Text("Finding your spot…", style = MaterialTheme.typography.titleSmall)
                                    Text(live?.let { "Precision ±${it.accuracy.roundToInt()} m. Stay still for a few seconds." }
                                        ?: "Waiting for your phone's location.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    if (live != null) TextButton(onClick = { stopEarly = true }, contentPadding = PaddingValues(0.dp)) { Text("Use this now") }
                                }
                                center != null -> {
                                    val fix = center!!
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f)) {
                                            Text("Spot saved", style = MaterialTheme.typography.titleSmall)
                                            Text("Accurate to about ±${fix.accuracy.roundToInt()} m", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(String.format(Locale.US, "%.5f, %.5f", fix.latitude, fix.longitude), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        TextButton(onClick = { locate() }) { Text("Redo") }
                                    }
                                }
                                else -> {
                                    Text("Stand in the middle of the place and tap below.", style = MaterialTheme.typography.bodyMedium)
                                    Button(onClick = {
                                        if (reader.permitted()) locate()
                                        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    }, Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Use where I am now")
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { locating?.cancel(); typed = !typed; error = null }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(if (typed) "Use my current location instead" else "Not there right now? Type coordinates")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("How big is it?")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SIZES.forEachIndexed { i, (label, value) ->
                            SegmentedButton(selected = radius == value, onClick = { radius = value },
                                shape = SegmentedButtonDefaults.itemShape(i, SIZES.size)) { Text(label) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(areaHint(radius), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${radius} m around the spot", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(radius.toFloat(), { radius = (it / 25).roundToInt() * 25 }, valueRange = 50f..1000f)
                    if (radius < 100) Text("Small areas can fail indoors, where phone location is less precise.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("When")
                    SettingSwitch("Only at certain times", "Outside these times, apps stay locked wherever you are.", timed) { timed = it }
                    if (timed) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf(2, 3, 4, 5, 6, 7, 1).forEach { day ->
                                DayToggle(DAY_NAMES[day].take(1), DAY_NAMES[day], day in days) { days = if (day in days) days - day else days + day }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { picking = true }, Modifier.weight(1f)) { Text("From ${clock(start)}") }
                            OutlinedButton(onClick = { picking = false }, Modifier.weight(1f)) { Text("Until ${clock(end)}") }
                        }
                        if (end <= start) Text(if (end == start) "Same start and end means the whole day." else "Ends the next morning.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { more = !more }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("More options", Modifier.weight(1f))
                        Icon(if (more) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (more) {
                        SettingSwitch("Block fake location apps", "Refuse locations that Android marks as simulated.", rejectMock) { rejectMock = it }
                        Column {
                            Text("Required precision: ±$accuracy m${if (precision == null) " (automatic)" else ""}")
                            Text("Readings rougher than this are refused. Lower is stricter but may fail indoors.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(accuracy.toFloat(), { precision = it.roundToInt() }, valueRange = 10f..(radius - 10f).coerceAtMost(200f))
                            if (precision != null) TextButton(onClick = { precision = null }, contentPadding = PaddingValues(0.dp)) { Text("Back to automatic") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    picking?.let { isStart ->
        val initial = if (isStart) start else end
        val state = rememberTimePickerState(initial / 60, initial % 60, android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(onDismissRequest = { picking = null },
            title = { Text(if (isStart) "From" else "Until") },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = {
                val minute = state.hour * 60 + state.minute
                if (isStart) start = minute else end = minute
                picking = null
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } })
    }
}

@Composable
private fun DayToggle(letter: String, name: String, selected: Boolean, toggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = toggle, shape = CircleShape, modifier = Modifier.size(40.dp),
        color = if (selected) colors.primary else colors.surface,
        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant)) {
        Box(contentAlignment = Alignment.Center) { Text(letter, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
fun SettingSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = change)
    }
}

/** Accepts "lat, lon" the way map apps copy it, with or without a space. */
fun parseCoordinates(text: String): Pair<Double, Double>? {
    val parts = text.trim().removePrefix("(").removeSuffix(")").split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
    if (parts.size != 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    return if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
}
