package com.vsa.vpnswitch

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("vsa_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(v) = sp.edit { putBoolean(KEY_ENABLED, v) }

    var homeSsids: Set<String>
        get() = sp.getStringSet(KEY_SSIDS, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit { putStringSet(KEY_SSIDS, v.toSet()) }

    /** Sekunden, die nach dem Verlassen des Heim-WLANs gewartet werden, bevor Tailscale startet. */
    var leaveDelaySec: Int
        get() = sp.getInt(KEY_DELAY, 20)
        set(v) = sp.edit { putInt(KEY_DELAY, v) }

    /** Zuletzt an Tailscale gesendeter Befehl: true = verbinden, false = trennen, null = noch keiner. */
    var lastVpnOn: Boolean?
        get() = if (sp.contains(KEY_LAST)) sp.getBoolean(KEY_LAST, false) else null
        set(v) = sp.edit { if (v == null) remove(KEY_LAST) else putBoolean(KEY_LAST, v) }

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_SSIDS = "home_ssids"
        const val KEY_DELAY = "leave_delay_sec"
        const val KEY_LAST = "last_vpn_on"
    }
}
