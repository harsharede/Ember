package com.harsharede.ember

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** What the status rail shows. Read with no runtime permissions: each is a public, permission-free API. */
data class DeviceStatus(
    val wifi: Boolean = false,
    val wifiLevel: Int = 0,
    val cellular: Boolean = false,
    val cellLevel: Int = 0,
    val vpn: Boolean = false,
    val bluetooth: Boolean = false,
    val airplane: Boolean = false,
    val dnd: Boolean = false,
    val silent: Boolean = false,
    val vibrate: Boolean = false,
    val location: Boolean = false,
    val battery: Int = 0,
    val charging: Boolean = false,
    val powerSave: Boolean = false,
)

/** The status icons the rail can show, in the order it shows them. Each one can be swiped away and restored in Settings. */
enum class StatusIcon(val id: String, val label: String) {
    WIFI("wifi", "Wi-Fi"),
    CELLULAR("cellular", "Mobile signal"),
    VPN("vpn", "VPN"),
    BLUETOOTH("bluetooth", "Bluetooth"),
    AIRPLANE("airplane", "Airplane mode"),
    DND("dnd", "Do Not Disturb"),
    RINGER("ringer", "Silent / vibrate"),
    LOCATION("location", "Location"),
    POWER_SAVE("power_save", "Power saving"),
    BATTERY("battery", "Battery"),
    NOTIFICATIONS("notifications", "Notification count"),
}

/** Listens while Home is on screen and stops when it leaves, so Ember costs nothing in the background. */
@Composable
fun rememberDeviceStatus(): State<DeviceStatus> {
    val context = LocalContext.current.applicationContext
    val state = remember { mutableStateOf(readStatus(context, null, null)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        var lastBattery: Intent? = null
        fun update() { state.value = readStatus(context, cm, lastBattery) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) lastBattery = intent
                update()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
        }
        lastBattery = context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
        }
        // A plain request (not just the default network) so a VPN beside Wi-Fi is seen too.
        runCatching { cm.registerNetworkCallback(android.net.NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback) }
        update()
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }
    return state
}

private fun readStatus(context: Context, cmOrNull: ConnectivityManager?, battery: Intent?): DeviceStatus {
    val cm = cmOrNull ?: context.getSystemService(ConnectivityManager::class.java)
    var wifi = false; var wifiLevel = 0; var cell = false; var vpn = false
    @Suppress("DEPRECATION")
    runCatching {
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn = true
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifi = true
                // Signal strength in dBm, put on the usual four bars.
                wifiLevel = wifiBars(caps.signalStrength)
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cell = true
        }
    }
    val tm = context.getSystemService(TelephonyManager::class.java)
    val cellLevel = runCatching { tm?.signalStrength?.level ?: 0 }.getOrDefault(0)
    val hasSim = runCatching { tm?.simState == TelephonyManager.SIM_STATE_READY }.getOrDefault(false)
    val bt = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
    val audio = context.getSystemService(AudioManager::class.java)
    val nm = context.getSystemService(NotificationManager::class.java)
    val lm = context.getSystemService(LocationManager::class.java)
    val pm = context.getSystemService(android.os.PowerManager::class.java)
    val b = battery ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = b?.let { val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100); if (l >= 0 && s > 0) l * 100 / s else 0 } ?: 0
    val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    return DeviceStatus(
        wifi = wifi, wifiLevel = wifiLevel,
        cellular = hasSim || cell, cellLevel = cellLevel,
        vpn = vpn, bluetooth = bt,
        airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
        dnd = nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL,
        silent = audio.ringerMode == AudioManager.RINGER_MODE_SILENT,
        vibrate = audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE,
        location = runCatching { lm.isLocationEnabled }.getOrDefault(false),
        battery = level, charging = plugged, powerSave = pm.isPowerSaveMode,
    )
}

/** -55 dBm or better is full, -85 or worse is one bar (Android's own Wi-Fi thresholds, roughly). */
internal fun wifiBars(dbm: Int): Int = when {
    dbm == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED -> 3
    dbm >= -55 -> 4
    dbm >= -66 -> 3
    dbm >= -77 -> 2
    else -> 1
}
