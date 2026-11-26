package com.vsa.vpnswitch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Vordergrund-Dienst: beobachtet das WLAN und schaltet die gewählte VPN-App (Tailscale, WireGuard, …)
 *  - AUS, sobald ein Heim-WLAN verbunden ist,
 *  - AN, sobald kein Heim-WLAN mehr verbunden ist (nach einer kurzen Verzögerung gegen Funklöcher).
 *
 * Es wird nur bei einem Wechsel geschaltet. Schaltest du das VPN zwischendurch von Hand,
 * bleibt das so, bis du das nächste Mal nach Hause kommst bzw. gehst.
 */
class WifiMonitorService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var watcher: WifiWatcher
    private val handler = Handler(Looper.getMainLooper())

    private var ready = false
    private var firstDecision = true
    private var pendingConnect = false
    private var lastNotificationText: String? = null
    private var lastError: String? = null

    private val connectRunnable = Runnable {
        pendingConnect = false
        applyVpn(true)
        evaluate()
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in VPN_CONFIG_KEYS) {
            // Anderer Dienst/andere Einstellungen: beim nächsten Durchlauf neu schalten.
            firstDecision = true
            cancelPendingConnect()
            evaluate()
        } else if (key == Prefs.KEY_SSIDS || key == Prefs.KEY_DELAY) {
            if (key == Prefs.KEY_DELAY && pendingConnect) {
                handler.removeCallbacks(connectRunnable)
                pendingConnect = false
            }
            evaluate()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createChannel()
        startInForeground(getString(R.string.notif_starting))

        watcher = WifiWatcher(this) { evaluate() }
        watcher.start()
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)

        // Kurz warten, bis Android die bereits bestehenden Netzwerke gemeldet hat.
        handler.postDelayed({
            ready = true
            evaluate()
        }, 1500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        evaluate()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        watcher.stop()
        MonitorState.status.value = MonitorStatus(running = false, text = getString(R.string.status_stopped))
        super.onDestroy()
    }

    private fun evaluate() {
        if (!ready) return
        val home = prefs.homeSsids
        val known = watcher.knownSsids
        val homeSsid = known.firstOrNull { it in home }

        val vpn = prefs.provider.label
        val text: String = when {
            home.isEmpty() -> "Kein Heim-WLAN festgelegt – öffne die App."

            homeSsid != null -> {
                cancelPendingConnect()
                applyVpn(false)
                "Zuhause („$homeSsid“) – $vpn aus"
            }

            watcher.hasWifi && known.isEmpty() ->
                // WLAN verbunden, aber Android verrät den Namen nicht -> lieber nichts tun.
                "WLAN-Name nicht lesbar – Standort einschalten und Standortzugriff „Immer erlauben“."

            else -> {
                val where = known.firstOrNull()?.let { "Fremdes WLAN („$it“)" } ?: "Kein WLAN"
                if (firstDecision) {
                    applyVpn(true)
                } else if (prefs.lastVpnOn != true && !pendingConnect) {
                    val delay = prefs.leaveDelaySec
                    if (delay <= 0) {
                        applyVpn(true)
                    } else {
                        pendingConnect = true
                        handler.postDelayed(connectRunnable, delay * 1000L)
                    }
                }
                if (pendingConnect) {
                    "$where – $vpn startet in ${prefs.leaveDelaySec} s…"
                } else {
                    "$where – $vpn an"
                }
            }
        }

        val fullText = lastError?.let { "$text\n⚠ $it" } ?: text
        MonitorState.status.value = MonitorStatus(running = true, text = fullText)
        updateNotification(fullText)
    }

    private fun cancelPendingConnect() {
        if (pendingConnect) {
            handler.removeCallbacks(connectRunnable)
            pendingConnect = false
        }
    }

    /** Schickt den Befehl nur, wenn er sich vom zuletzt gesendeten unterscheidet (oder beim Start). */
    private fun applyVpn(on: Boolean) {
        val force = firstDecision
        firstDecision = false
        if (!force && prefs.lastVpnOn == on) return
        Log.i(TAG, "${prefs.provider.label} -> ${if (on) "connect" else "disconnect"}")
        when (val result = VpnController.setConnected(this, prefs, on)) {
            is SendResult.Sent -> {
                lastError = null
                prefs.lastVpnOn = on
            }
            is SendResult.Failed -> {
                // lastVpnOn bleibt unverändert, damit beim nächsten Netzwechsel erneut versucht wird.
                Log.w(TAG, "Schalten fehlgeschlagen: ${result.reason}")
                lastError = result.reason
            }
        }
    }

    // ---------- Benachrichtigung ----------

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun startInForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(text), type)
        lastNotificationText = text
    }

    private fun updateNotification(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    companion object {
        private const val TAG = "VSA"
        private const val CHANNEL_ID = "monitor"
        private const val NOTIFICATION_ID = 1
        private val VPN_CONFIG_KEYS = setOf(
            Prefs.KEY_PROVIDER, Prefs.KEY_WG_TUNNEL, Prefs.KEY_CUSTOM_PACKAGE, Prefs.KEY_CUSTOM_CLASS,
            Prefs.KEY_CUSTOM_CONNECT, Prefs.KEY_CUSTOM_DISCONNECT, Prefs.KEY_CUSTOM_EXTRAS,
        )

        fun startIfEnabled(context: Context) {
            val prefs = Prefs(context)
            if (prefs.enabled && prefs.homeSsids.isNotEmpty()) {
                runCatching {
                    ContextCompat.startForegroundService(
                        context, Intent(context, WifiMonitorService::class.java),
                    )
                }.onFailure { Log.w(TAG, "Dienst konnte nicht gestartet werden", it) }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WifiMonitorService::class.java))
        }
    }
}
