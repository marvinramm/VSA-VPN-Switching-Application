package com.vsa.vpnswitch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Beobachtet alle WLAN-Verbindungen und meldet deren SSIDs (null = verbunden, aber Name nicht lesbar).
 * Alle Callbacks laufen auf dem Main-Thread.
 *
 * Android wertet jedes Auslesen der SSID als Standortzugriff. Deshalb wird der Name nur einmal pro
 * Verbindung gelesen (beim Verbinden, bei Bedarf mit wenigen Wiederholungen) und nicht bei jeder
 * Änderung der Signalstärke. Die SSID einer bestehenden Verbindung kann sich nicht ändern –
 * ein anderes WLAN ist für Android immer ein neues [Network].
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
    private val pendingReads = HashMap<Network, Runnable>()
    private val validated = HashSet<Network>()
    private var callback: Callback? = null
    private var locationReceiverRegistered = false

    /** Wird der Standort eingeschaltet, noch unlesbare WLAN-Namen erneut versuchen. */
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            networks.filterValues { it == null }.keys.forEach { scheduleRead(it, attempt = 0, delayMs = 1_000) }
        }
    }

    /** true, wenn gerade irgendein WLAN verbunden ist. */
    val hasWifi: Boolean get() = networks.isNotEmpty()

    /** Lesbare SSIDs aller verbundenen WLANs. */
    val knownSsids: List<String> get() = networks.values.filterNotNull()

    fun start() {
        if (callback != null) return
        // Bewusst ohne FLAG_INCLUDE_LOCATION_INFO: sonst zählt jede Callback-Auslieferung
        // (also auch jede Signalstärke-Änderung) als Standortzugriff.
        val cb = Callback()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(request, cb, handler)
        callback = cb

        // PROVIDERS_CHANGED ist ein geschützter System-Broadcast; EXPORTED, damit er auch
        // unter Android < 13 ohne die von ContextCompat ergänzte Sender-Berechtigung ankommt.
        ContextCompat.registerReceiver(
            appContext, locationReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        locationReceiverRegistered = true
    }

    fun stop() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        if (locationReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(locationReceiver) }
            locationReceiverRegistered = false
        }
        pendingReads.values.forEach { handler.removeCallbacks(it) }
        pendingReads.clear()
        validated.clear()
        networks.clear()
    }

    /** Ein einzelner Lesevorgang = ein Standortzugriff aus Sicht von Android. */
    private fun readSsid(): String? {
        @Suppress("DEPRECATION")
        return normalize(runCatching { wm.connectionInfo?.ssid }.getOrNull())
    }

    /**
     * Liest die SSID für [network], sofern sie noch fehlt. Klappt es nicht (z. B. direkt nach dem
     * Verbinden), wird es nach den Wartezeiten aus [RETRY_DELAYS_MS] erneut versucht, dann aufgegeben.
     */
    private fun scheduleRead(network: Network, attempt: Int, delayMs: Long) {
        cancelRead(network)
        val task = object : Runnable {
            override fun run() {
                pendingReads.remove(network)
                if (network !in networks || networks[network] != null) return
                val ssid = readSsid()
                if (ssid != null) {
                    networks[network] = ssid
                    onChange(this@WifiWatcher)
                } else if (attempt < RETRY_DELAYS_MS.size) {
                    scheduleRead(network, attempt + 1, RETRY_DELAYS_MS[attempt])
                }
            }
        }
        if (delayMs <= 0) {
            task.run()
        } else {
            pendingReads[network] = task
            handler.postDelayed(task, delayMs)
        }
    }

    private fun cancelRead(network: Network) {
        pendingReads.remove(network)?.let { handler.removeCallbacks(it) }
    }

    private inner class Callback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (network !in networks) {
                networks[network] = null
                scheduleRead(network, attempt = 0, delayMs = 0)
            }
            onChange(this@WifiWatcher)
        }

        // onCapabilitiesChanged feuert bei jeder Signalstärke-Änderung, die SSID bleibt dabei gleich.
        // Ausgewertet wird nur der einmalige Moment, in dem die Verbindung als funktionierend
        // erkannt wird (VALIDATED) – und auch nur, wenn der Name bis dahin noch nicht lesbar war.
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (network !in networks || network in validated) return
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
            validated += network
            if (networks[network] == null) scheduleRead(network, attempt = 0, delayMs = 0)
        }

        override fun onLost(network: Network) {
            cancelRead(network)
            validated -= network
            networks.remove(network)
            onChange(this@WifiWatcher)
        }
    }

    companion object {
        /** Wartezeiten vor den Wiederholungen, falls der Name direkt nach dem Verbinden noch fehlt. */
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 3_000, 10_000)

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
