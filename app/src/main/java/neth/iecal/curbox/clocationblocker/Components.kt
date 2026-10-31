package neth.iecal.curbox.clocationblocker

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Live state of a location check, shared by the test dialog and the real Curbox check. */
sealed interface CheckState {
    data class Checking(val reading: Reading?) : CheckState
    data class Done(val verdict: Verdict) : CheckState
}

@Composable
fun rememberCheck(place: Place, attempt: Int): CheckState {
    val context = LocalContext.current
    var state by remember { mutableStateOf<CheckState>(CheckState.Checking(null)) }
    LaunchedEffect(attempt) {
        state = CheckState.Checking(null)
        state = CheckState.Done(verifyLocation(LocationReader(context), place) { state = CheckState.Checking(it) })
    }
    return state
}

/** The area as a circle with you as a dot, north up. The halo is how unsure the phone is. */
@Composable
fun PlaceDiagram(radius: Int, reading: Reading?, passed: Boolean?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tone = when (passed) { true -> colors.primary; false -> colors.error; null -> colors.onSurfaceVariant }
    val distance by animateFloatAsState(reading?.distance ?: 0f, label = "distance")
    val accuracy by animateFloatAsState(reading?.accuracy?.takeIf { it.isFinite() } ?: 0f, label = "accuracy")
    val bearing = reading?.bearing ?: 0f
    Canvas(modifier.size(200.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val span = (radius * 1.35f).coerceAtLeast(min(distance + accuracy, radius * 4f) * 1.1f)
        val scale = (size.minDimension / 2) / span
        drawCircle(colors.primary.copy(alpha = 0.10f), radius * scale, center)
        drawCircle(colors.primary.copy(alpha = 0.55f), radius * scale, center, style = Stroke(1.5.dp.toPx()))
        drawCircle(colors.primary.copy(alpha = 0.7f), 3.dp.toPx(), center)
        if (reading != null) {
            val angle = Math.toRadians(bearing.toDouble())
            val reach = min(distance * scale, size.minDimension / 2 - 8.dp.toPx())
            val dot = Offset(center.x + (sin(angle) * reach).toFloat(), center.y - (cos(angle) * reach).toFloat())
            drawCircle(tone.copy(alpha = 0.18f), (accuracy * scale).coerceAtLeast(10.dp.toPx()), dot)
            drawCircle(colors.surface, 8.dp.toPx(), dot)
            drawCircle(tone, 6.dp.toPx(), dot)
        }
    }
}

@Composable
fun CheckView(place: Place, state: CheckState) {
    val verdict = (state as? CheckState.Done)?.verdict
    val reading = when (state) { is CheckState.Checking -> state.reading; is CheckState.Done -> state.verdict.reading }
    val passed = verdict?.let { it.problem == null }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PlaceDiagram(place.radius, reading, passed)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                verdict == null -> {
                    Text("Finding you…", style = MaterialTheme.typography.titleMedium)
                    Text(reading?.let { "Precision so far ±${meters(it.accuracy)}" } ?: "This can take a few seconds indoors",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.width(120.dp).padding(top = 6.dp))
                }
                passed == true -> {
                    Text("You're at ${place.name}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    reading?.let { Text(if (it.distance < 10) "Right in the middle" else "About ${meters(it.distance)} from the middle", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                else -> Text(verdict.problem.orEmpty(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun clock(minute: Int): String {
    val h24 = DateFormat.is24HourFormat(LocalContext.current)
    val h = minute / 60; val m = minute % 60
    return if (h24) "%02d:%02d".format(h, m) else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
}

fun daySummary(days: Set<Int>): String = when (days) {
    (1..7).toSet() -> "Every day"
    setOf(2, 3, 4, 5, 6) -> "Weekdays"
    setOf(1, 7) -> "Weekends"
    else -> listOf(2, 3, 4, 5, 6, 7, 1).filter { it in days }.joinToString(", ") { DAY_NAMES[it] }
}
val DAY_NAMES = listOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun schedule(place: Place): String =
    if (!place.timed) "Any time" else "${daySummary(place.days)}, ${clock(place.startMinute)}–${clock(place.endMinute)}"

fun areaHint(radius: Int) = when {
    radius <= 75 -> "About one house or small shop"
    radius <= 150 -> "About one building"
    radius <= 300 -> "A building and its grounds"
    radius <= 600 -> "A campus or a few blocks"
    else -> "A whole neighbourhood"
}
