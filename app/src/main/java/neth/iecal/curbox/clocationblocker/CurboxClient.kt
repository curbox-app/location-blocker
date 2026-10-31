package neth.iecal.curbox.clocationblocker

import android.content.*
import android.os.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neth.iecal.curbox.api.ICurboxApi
import org.json.JSONArray

class CurboxClient(private val context: Context, private val scope: CoroutineScope) {
    enum class State { Disconnected, Connecting, NeedsApproval, Outdated, Ready }

    var state by mutableStateOf(State.Disconnected)
        private set
    /** Requirement IDs this app currently has registered in the connected Curbox. */
    var registered by mutableStateOf(emptySet<String>())
        private set
    private var api: ICurboxApi? = null
    private var bound = false
    private var connection: ServiceConnection? = null

    fun installations(): List<Pair<String, String>> = context.packageManager
        .queryIntentServices(Intent("neth.iecal.curbox.api.BIND"), 0)
        .distinctBy { it.serviceInfo.packageName }.map { it.serviceInfo.packageName to it.loadLabel(context.packageManager).toString() }

    fun connect(pkg: String) {
        close()
        state = State.Connecting
        val next = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                api = ICurboxApi.Stub.asInterface(binder); scope.launch { refresh() }
            }
            override fun onServiceDisconnected(name: ComponentName) { api = null; state = State.Disconnected }
            override fun onBindingDied(name: ComponentName) { close() }
            override fun onNullBinding(name: ComponentName) { close() }
        }
        connection = next
        try {
            bound = context.bindService(Intent("neth.iecal.curbox.api.BIND").setPackage(pkg), next, Context.BIND_AUTO_CREATE)
            if (!bound) state = State.Disconnected
        } catch (_: Exception) { state = State.Disconnected }
    }

    /** Re-reads version, approval, and registrations. Safe to call whenever the app returns to the front. */
    suspend fun refresh() {
        val remote = api ?: return
        val (next, ids) = withContext(Dispatchers.IO) {
            try {
                when {
                    remote.apiVersion() < 3 -> State.Outdated to emptySet()
                    !remote.isGranted() -> State.NeedsApproval to emptySet()
                    else -> State.Ready to registeredIds(remote)
                }
            } catch (_: Exception) { State.Disconnected to emptySet() }
        }
        if (api === remote) { state = next; registered = ids }
    }

    private fun registeredIds(remote: ICurboxApi): Set<String> = runCatching {
        val array = JSONArray(remote.list("UNLOCK_CONDITIONS") ?: "[]")
        (0 until array.length()).map { array.getJSONObject(it) }
            .filter { it.optString("packageName") == context.packageName }
            .map { it.getString("id") }.toSet()
    }.getOrDefault(emptySet())

    suspend fun register(place: Place): String {
        val remote = api ?: return "DISCONNECTED"
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (remote.apiVersion() < 3) "UNKNOWN_COMMAND"
                else if (!remote.isGranted()) "DENIED"
                else remote.execute("REGISTER_UNLOCK_CONDITION", Bundle().apply {
                    putString("id", place.id); putString("label", "${if (place.inverse) "Away from" else "At"} ${place.name}".take(100))
                    putString("activity", "${context.packageName}.CheckLocationActivity")
                }) ?: "FAILED"
            }.getOrDefault("FAILED")
        }
        refresh()
        return result
    }

    suspend fun remove(place: Place): String {
        val remote = api ?: return "DISCONNECTED"
        val result = withContext(Dispatchers.IO) {
            runCatching { remote.execute("REMOVE_UNLOCK_CONDITION", Bundle().apply { putString("id", place.id) }) ?: "FAILED" }
                .getOrDefault("FAILED")
        }
        refresh()
        return result
    }

    fun close() {
        if (bound) connection?.let { runCatching { context.unbindService(it) } }
        bound = false; api = null; connection = null
        state = State.Disconnected; registered = emptySet()
    }
}
