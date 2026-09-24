package cc.opencar.assistant.feature.shortcuts

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Polls the connected Wi‑Fi SSID and invokes [onChanged] on transitions.
 */
class WifiSsidMonitor(
    context: Context,
    private val onChanged: (ssid: String?) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var job: Job? = null
    @Volatile private var lastSsid: String? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (coroutineContext.isActive) {
                val ssid = currentSsid()
                if (ssid != lastSsid) {
                    lastSsid = ssid
                    Log.i(TAG, "wifi ssid -> ${ssid ?: "(none)"}")
                    onChanged(ssid)
                }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun currentSsid(): String? {
        val info = runCatching { wifi?.connectionInfo }.getOrNull() ?: return null
        val raw = info.ssid ?: return null
        val cleaned = raw.trim().removePrefix("\"").removeSuffix("\"")
        if (cleaned.isEmpty() || cleaned.equals("<unknown ssid>", true) || cleaned == "0x") {
            return null
        }
        return cleaned
    }

    companion object {
        private const val TAG = "WifiSsidMonitor"
        private const val POLL_MS = 3_000L
    }
}
