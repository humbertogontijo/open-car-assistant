package cc.opencar.assistant.feature.web

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import cc.opencar.assistant.api.EntityContract
import cc.opencar.assistant.api.EntityType
import cc.opencar.assistant.feature.memory.ExternalSettingsApplier
import cc.opencar.assistant.support.I18nBundle

/**
 * First-class Android radios (Wi‑Fi / Bluetooth) for the Conexão section.
 * Uses framework APIs — may need CHANGE_WIFI_STATE / BLUETOOTH_CONNECT on the HU.
 */
class AndroidSettingsController(
    private val context: Context,
) : ExternalSettingsApplier {
    private val wifi: WifiManager?
        get() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val bluetooth: BluetoothAdapter?
        get() {
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return mgr?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
        }

    fun status(): Map<String, Any?> {
        val w = wifi
        val b = bluetooth
        return mapOf(
            "wifiEnabled" to (w?.isWifiEnabled == true),
            "wifiAvailable" to (w != null),
            "bluetoothEnabled" to (b?.isEnabled == true),
            "bluetoothAvailable" to (b != null),
        )
    }

    fun setWifi(enable: Boolean): Map<String, Any?> {
        val w = wifi ?: return mapOf("ok" to false, "error" to "wifi unavailable", "status" to status())
        return try {
            @Suppress("DEPRECATION")
            val ok = w.setWifiEnabled(enable)
            mapOf("ok" to ok, "status" to status())
        } catch (t: Throwable) {
            Log.w(TAG, "setWifi failed: ${t.message}")
            mapOf("ok" to false, "error" to (t.message ?: "failed"), "status" to status())
        }
    }

    fun setBluetooth(enable: Boolean): Map<String, Any?> {
        val b = bluetooth ?: return mapOf("ok" to false, "error" to "bluetooth unavailable", "status" to status())
        return try {
            @Suppress("DEPRECATION")
            val ok = if (enable) b.enable() else b.disable()
            mapOf("ok" to ok, "status" to status())
        } catch (t: Throwable) {
            Log.w(TAG, "setBluetooth failed: ${t.message}")
            mapOf("ok" to false, "error" to (t.message ?: "failed"), "status" to status())
        }
    }

    override suspend fun apply(id: String, value: String): Boolean {
        val on = value == "1" || value.equals("true", true) || value.equals("on", true)
        return when (id) {
            ID_WIFI -> setWifi(on)["ok"] == true
            ID_BT -> setBluetooth(on)["ok"] == true
            else -> false
        }
    }

    override suspend fun read(id: String): String? {
        val st = status()
        return when (id) {
            ID_WIFI -> if (st["wifiEnabled"] == true) "1" else "0"
            ID_BT -> if (st["bluetoothEnabled"] == true) "1" else "0"
            else -> null
        }
    }

        fun entityMaps(
        i18n: I18nBundle?,
        persist: Map<String, Map<String, Any?>>,
    ): List<Map<String, Any?>> {
        val st = status()
        fun label(key: String, fb: String) = i18n?.t(key, fb) ?: fb
        return listOf(
            androidEntity(
                id = ID_WIFI,
                label = label("control.android_wifi", "Wi‑Fi"),
                hint = label("control.android_wifi.hint", "Pin to reapply on boot / gear"),
                enabled = st["wifiEnabled"] == true,
                available = st["wifiAvailable"] == true,
                pin = persist[ID_WIFI],
                onLabel = label("common.on", "On"),
                offLabel = label("common.off", "Off"),
            ),
            androidEntity(
                id = ID_BT,
                label = label("control.android_bluetooth", "Bluetooth"),
                hint = label("control.android_bluetooth.hint", "Pin to reapply on boot / gear"),
                enabled = st["bluetoothEnabled"] == true,
                available = st["bluetoothAvailable"] == true,
                pin = persist[ID_BT],
                onLabel = label("common.on", "On"),
                offLabel = label("common.off", "Off"),
            ),
        )
    }

    private fun androidEntity(
        id: String,
        label: String,
        hint: String,
        enabled: Boolean,
        available: Boolean,
        pin: Map<String, Any?>?,
        onLabel: String,
        offLabel: String,
    ): Map<String, Any?> {
        val value = if (enabled) "1" else "0"
        val pinEnabled = pin?.get("enabled") == true
        val pVal = pin?.get("value") as? String
        return EntityContract.enrich(
            mapOf(
                "id" to id,
                "group" to "connect",
                "entity" to EntityType.ANDROID.id,
                "label" to label,
                "hint" to hint,
                "description" to hint,
                "input" to "bool",
                "icon" to "system",
                "writable" to available,
                "value" to value,
                "valueLabel" to if (enabled) onLabel else offLabel,
                "status" to if (available) "ok" else "unavailable",
                "needsPrivilege" to false,
                "stale" to false,
                "history" to false,
                "persistEnabled" to pinEnabled,
                "persistValue" to pVal,
                "persistLabel" to pVal,
            ),
        )
    }

    companion object {
        private const val TAG = "AndroidSettings"
        const val ID_WIFI = "android_wifi"
        const val ID_BT = "android_bluetooth"
    }
}
