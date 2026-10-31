package neth.iecal.curbox.clocationblocker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import neth.iecal.curbox.clocationblocker.CurboxClient.State
import neth.iecal.curbox.clocationblocker.ui.theme.LocationBlockerTheme

class MainActivity : ComponentActivity() {
    private lateinit var client: CurboxClient
    private lateinit var store: PlaceStore
    private lateinit var reader: LocationReader
    private var message by mutableStateOf<String?>(null)
    private var locationAllowed by mutableStateOf(false)
    private var locationOn by mutableStateOf(false)
    private var askedLocation by mutableStateOf(false)
    private var places by mutableStateOf(emptyList<Place>())
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedLocation = true
        locationAllowed = reader.permitted()
        if (!locationAllowed) message = if (it[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
            "Turn on \"Use precise location\" for Location Blocker." else "Location is needed to check where you are."
    }
    private val approval = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        lifecycleScope.launch {
            client.refresh()
            if (client.state == State.Ready) { message = "Connected to Curbox."; syncPlaces() }
            else if (client.state == State.NeedsApproval) message = "Curbox wasn't approved. Tap Connect to try again."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PlaceStore(this); client = CurboxClient(this, lifecycleScope); reader = LocationReader(this)
        places = store.places()
        store.curboxPackage?.let(client::connect)
        enableEdgeToEdge()
        setContent { LocationBlockerTheme { Home() } }
    }
    override fun onResume() {
        super.onResume()
        locationAllowed = reader.permitted(); locationOn = reader.enabled()
        lifecycleScope.launch { client.refresh() }
    }
    override fun onDestroy() { client.close(); super.onDestroy() }

    private fun connectCurbox(pkg: String) {
        store.curboxPackage = pkg
        client.connect(pkg)
        runCatching { approval.launch(Intent("neth.iecal.curbox.api.REQUEST_PERMISSION").setPackage(pkg)) }
            .onFailure { message = "Couldn't open Curbox. Make sure it's up to date." }
    }

    private fun askLocation() {
        if (askedLocation && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION))
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    /** Places saved before Curbox was connected get added as soon as it is. */
    private suspend fun syncPlaces() {
        val missing = places.filter { it.id !in client.registered }
        val failed = missing.count { client.register(it) != "OK" }
        if (failed > 0) message = "$failed ${if (failed == 1) "place" else "places"} couldn't be added to Curbox. Tap Add to Curbox to retry."
    }

    @Composable
    private fun Home() {
        var editing by remember { mutableStateOf(false) }
        var testing by remember { mutableStateOf<Place?>(null) }
        var removing by remember { mutableStateOf<Place?>(null) }
        var busy by remember { mutableStateOf(false) }
        var choosing by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val installations = remember { client.installations() }
        val ready = client.state == State.Ready
        val setupDone = ready && locationAllowed && locationOn

        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it, withDismissAction = true); message = null } }

        fun publish(place: Place) {
            busy = true
            scope.launch { client.register(place).let { if (it != "OK") message = registrationMessage(it) }; busy = false }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (places.isNotEmpty() && places.size < 50) ExtendedFloatingActionButton(onClick = { editing = true },
                    icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add place") })
            }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 28.dp, bottom = padding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Location Blocker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text("Apps you block in Curbox unlock only when you're at one of your places.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!setupDone) item {
                    SetupCard(installations, onConnect = { if (installations.size == 1) connectCurbox(installations[0].first) else choosing = true })
                }
                if (places.isEmpty()) item {
                    EmptyState(onAdd = { editing = true })
                } else item { SectionLabel("Your places", Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(places, key = { it.id }) { place ->
                    PlaceCard(place, ready, place.id in client.registered, busy,
                        onTest = { testing = place }, onPublish = { publish(place) }, onRemove = { removing = place })
                }
                if (places.isNotEmpty()) item { CurboxGuide() }
                if (installations.size > 1 && store.curboxPackage != null) item {
                    TextButton(onClick = { choosing = true }) {
                        Text("Using ${installations.firstOrNull { it.first == store.curboxPackage }?.second ?: "Curbox"} · Change")
                    }
                }
            }
        }

        if (editing) PlaceEditor(onDismiss = { editing = false }, onSave = { place ->
            if (!store.save(places + place)) { message = "Couldn't save the place. Try again."; return@PlaceEditor }
            places = store.places(); editing = false
            if (ready) scope.launch {
                val result = client.register(place)
                message = if (result == "OK") "${place.name} saved and added to Curbox." else registrationMessage(result)
            } else message = "${place.name} saved. Connect Curbox to start using it."
        })
        testing?.let { place -> TestDialog(place, onDismiss = { testing = null }) }
        removing?.let { p -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove ${p.name}?") },
            text = { Text("Curbox groups that use this place will stay locked until you pick another place for them.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    // Removing local data fails closed even if the remote registration is unavailable.
                    runCatching { client.remove(p) }
                    if (store.save(places.filterNot { it.id == p.id })) { places = store.places(); removing = null }
                    else message = "Couldn't remove the place. Try again."
                    busy = false
                }
            }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }) }
        if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text("Which Curbox?") },
            text = { Column { installations.forEach { (pkg, label) ->
                ListItem(headlineContent = { Text(label) }, supportingContent = { Text(pkg) },
                    modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { choosing = false; connectCurbox(pkg) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh))
            } } },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } })
    }

    @Composable
    private fun SetupCard(installations: List<Pair<String, String>>, onConnect: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text("Finish setting up", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
                val curbox = when {
                    installations.isEmpty() -> "Install Curbox first, then come back."
                    client.state == State.Outdated -> "Your Curbox is too old. Update it to use places."
                    client.state == State.Connecting -> "Connecting…"
                    else -> "Lets Curbox ask this app where you are."
                }
                SetupStep("Connect Curbox", curbox, client.state == State.Ready,
                    action = if (installations.isNotEmpty() && client.state != State.Connecting) "Connect" else null, onClick = onConnect)
                SetupStep("Allow location", "Only used when you check in. Nothing leaves your phone.", locationAllowed,
                    action = if (askedLocation && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) "Settings" else "Allow",
                    onClick = ::askLocation)
                SetupStep("Turn on location", "Your phone's location switch needs to be on.", locationOn, action = "Turn on",
                    onClick = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) })
            }
        }
    }

    @Composable
    private fun SetupStep(title: String, detail: String, done: Boolean, action: String?, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center) {
                if (done) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (!done) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!done && action != null) FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp)) { Text(action) }
        }
    }

    @Composable
    private fun EmptyState(onAdd: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Place, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("No places yet", style = MaterialTheme.typography.titleMedium)
            Text("Add the places where you're allowed to use your apps, like home or the library.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Button(onClick = onAdd, Modifier.padding(top = 8.dp)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add a place") }
        }
    }

    @Composable
    private fun PlaceCard(place: Place, ready: Boolean, registered: Boolean, busy: Boolean,
                          onTest: () -> Unit, onPublish: () -> Unit, onRemove: () -> Unit) {
        var menu by remember { mutableStateOf(false) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        Text("Within ${meters(place.radius.toFloat())} · ${schedule(place)}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Remove") }, enabled = !busy, onClick = { menu = false; onRemove() })
                        }
                    }
                }
                Row(Modifier.padding(top = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onTest) { Text("Test here") }
                    Spacer(Modifier.weight(1f))
                    when {
                        registered -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("In Curbox", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        ready -> TextButton(enabled = !busy, onClick = onPublish) { Text("Add to Curbox") }
                        else -> Text("Not in Curbox yet", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    @Composable
    private fun CurboxGuide() {
        var open by remember { mutableStateOf(places.none { it.id in client.registered }) }
        Card(Modifier.padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column {
                Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Using a place in Curbox", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (open) Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "Open Curbox and pick the app blocker group you want.",
                        "Go to Warning Screen Configuration → Require effort to unlock → Require another app.",
                        "Choose \"At\" followed by your place's name, then save and turn the group on.",
                        "Make sure Curbox's accessibility service is on, and don't block Location Blocker itself."
                    ).forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(12.dp))
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text("The check happens when you unlock. Leaving afterwards doesn't re-lock apps until the unlock time you set in Curbox runs out.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TestDialog(place: Place, onDismiss: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val state = rememberCheck(place, attempt)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Testing ${place.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CheckView(place, state)
                Text("This is only a test. It won't unlock anything.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { if (state is CheckState.Done) TextButton(onClick = { attempt++ }) { Text("Test again") } })
}

fun registrationMessage(result: String) = when (result) {
    "OK" -> "Added to Curbox."
    "DENIED" -> "Curbox hasn't approved this app. Tap Connect to approve it."
    "UNKNOWN_COMMAND" -> "Your Curbox is too old for places. Update it and try again."
    "DISCONNECTED" -> "Not connected to Curbox yet."
    else -> "Curbox couldn't add this place. You can have up to 50 places."
}
