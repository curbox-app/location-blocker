package neth.iecal.curbox.clocationblocker

import android.content.*
import android.os.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import neth.iecal.curbox.api.ICurboxApi

class CurboxClient(private val context: Context) {
    var status by mutableStateOf("Choose your Curbox installation")
        private set
    private var api: ICurboxApi? = null
    private var bound = false
    private var connection: ServiceConnection? = null
    fun installations(): List<Pair<String, String>> = context.packageManager
        .queryIntentServices(Intent("neth.iecal.curbox.api.BIND"), 0)
        .distinctBy { it.serviceInfo.packageName }.map { it.serviceInfo.packageName to it.loadLabel(context.packageManager).toString() }
    fun connect(pkg: String) {
        close()
        status = "Connecting…"
        val next = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                api = ICurboxApi.Stub.asInterface(binder); status = "Connected · check access to continue"
            }
            override fun onServiceDisconnected(name: ComponentName) { api = null; status = "Disconnected from Curbox" }
            override fun onBindingDied(name: ComponentName) { close(); status = "Curbox changed. Reconnect to continue." }
            override fun onNullBinding(name: ComponentName) { close(); status = "Curbox connection unavailable" }
        }
        connection = next
        try {
            bound = context.bindService(Intent("neth.iecal.curbox.api.BIND").setPackage(pkg), next, Context.BIND_AUTO_CREATE)
            if (!bound) status = "Could not connect to Curbox"
        } catch (_: Exception) { status = "Could not connect to Curbox" }
    }
    suspend fun ready(): Boolean = withContext(Dispatchers.IO) {
        val remote = api ?: return@withContext false
        try {
            check(remote.apiVersion() >= 3) { "Curbox API 3 or newer is required" }
            check(remote.isGranted()) { "Approve Location Blocker in Curbox first" }
            true
        } catch (e: Exception) { throw IllegalStateException(e.message ?: "Curbox is unavailable") }
    }
    suspend fun register(place: Place): String = withContext(Dispatchers.IO) {
        val remote = api ?: error("Connect to Curbox first")
        check(ready()) { "Connect to Curbox first" }
        remote.execute("REGISTER_UNLOCK_CONDITION", Bundle().apply {
            putString("id", place.id); putString("label", "At ${place.name}")
            putString("activity", "${context.packageName}.CheckLocationActivity")
        }) ?: "FAILED"
    }
    suspend fun remove(place: Place): String = withContext(Dispatchers.IO) {
        check(ready()) { "Connect to Curbox first" }
        api?.execute("REMOVE_UNLOCK_CONDITION", Bundle().apply { putString("id", place.id) }) ?: "FAILED"
    }
    fun close() {
        if (bound) connection?.let { runCatching { context.unbindService(it) } }
        bound = false; api = null; connection = null
    }
}
