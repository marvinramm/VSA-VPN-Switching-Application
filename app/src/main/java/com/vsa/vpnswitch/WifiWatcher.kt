package com.vsa.vpnswitch

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi

/**
 * Beobachtet alle WLAN-Verbindungen und meldet deren SSIDs (null = verbunden, aber Name nicht lesbar).
 * Alle Callbacks laufen auf dem Main-Thread.
 */
class WifiWatcher(
    context: Context,
    private val onChange: (WifiWatcher) -> Unit,
) {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(ConnectivityManager::class.java)
    private val wm = appContext.getSystemService(WifiManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val networks = LinkedHashMap<Network, String?>()
    private var callback: Callback? = null

    /** true, wenn gerade irgendein WLAN verbunden ist. */
    val hasWifi: Boolean get() = networks.isNotEmpty()

    /** Lesbare SSIDs aller verbundenen WLANs. */
    val knownSsids: List<String> get() = networks.values.filterNotNull()

    fun start() {
        if (callback != null) return
        val cb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Callback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
        } else {
            Callback()
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(request, cb, handler)
        callback = cb
    }

    fun stop() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        networks.clear()
    }

    private fun readSsid(caps: NetworkCapabilities?): String? {
        var ssid: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ssid = normalize((caps?.transportInfo as? WifiInfo)?.ssid)
        }
        if (ssid == null) {
            @Suppress("DEPRECATION")
            ssid = normalize(runCatching { wm.connectionInfo?.ssid }.getOrNull())
        }
        return ssid
    }

    private inner class Callback : ConnectivityManager.NetworkCallback {
        constructor() : super()

        @RequiresApi(Build.VERSION_CODES.S)
        constructor(flags: Int) : super(flags)

        override fun onAvailable(network: Network) {
            if (network !in networks) {
                networks[network] = readSsid(cm.getNetworkCapabilities(network))
            }
            onChange(this@WifiWatcher)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val old = networks[network]
            val new = readSsid(caps) ?: old
            val changed = network !in networks || old != new
            networks[network] = new
            if (changed) onChange(this@WifiWatcher)
        }

        override fun onLost(network: Network) {
            networks.remove(network)
            onChange(this@WifiWatcher)
        }
    }

    companion object {
        fun normalize(raw: String?): String? {
            if (raw.isNullOrBlank() || raw == WifiManager.UNKNOWN_SSID) return null
            val s = if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
                raw.substring(1, raw.length - 1)
            } else {
                raw
            }
            return s.ifBlank { null }
        }
    }
}
