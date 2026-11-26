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

    /** Sekunden, die nach dem Verlassen des Heim-WLANs gewartet werden, bevor das VPN startet. */
    var leaveDelaySec: Int
        get() = sp.getInt(KEY_DELAY, 20)
        set(v) = sp.edit { putInt(KEY_DELAY, v) }

    /** Zuletzt an die VPN-App gesendeter Befehl: true = verbinden, false = trennen, null = noch keiner. */
    var lastVpnOn: Boolean?
        get() = if (sp.contains(KEY_LAST)) sp.getBoolean(KEY_LAST, false) else null
        set(v) = sp.edit { if (v == null) remove(KEY_LAST) else putBoolean(KEY_LAST, v) }

    var provider: VpnProvider
        get() = VpnProvider.fromId(sp.getString(KEY_PROVIDER, null))
        set(v) = sp.edit { putString(KEY_PROVIDER, v.id) }

    var wireguardTunnel: String
        get() = sp.getString(KEY_WG_TUNNEL, "") ?: ""
        set(v) = sp.edit { putString(KEY_WG_TUNNEL, v) }

    var customPackage: String
        get() = sp.getString(KEY_CUSTOM_PACKAGE, "") ?: ""
        set(v) = sp.edit { putString(KEY_CUSTOM_PACKAGE, v) }

    var customReceiverClass: String
        get() = sp.getString(KEY_CUSTOM_CLASS, "") ?: ""
        set(v) = sp.edit { putString(KEY_CUSTOM_CLASS, v) }

    var customConnectAction: String
        get() = sp.getString(KEY_CUSTOM_CONNECT, "") ?: ""
        set(v) = sp.edit { putString(KEY_CUSTOM_CONNECT, v) }

    var customDisconnectAction: String
        get() = sp.getString(KEY_CUSTOM_DISCONNECT, "") ?: ""
        set(v) = sp.edit { putString(KEY_CUSTOM_DISCONNECT, v) }

    var customExtras: String
        get() = sp.getString(KEY_CUSTOM_EXTRAS, "") ?: ""
        set(v) = sp.edit { putString(KEY_CUSTOM_EXTRAS, v) }

    companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_WG_TUNNEL = "wg_tunnel"
        const val KEY_CUSTOM_PACKAGE = "custom_package"
        const val KEY_CUSTOM_CLASS = "custom_class"
        const val KEY_CUSTOM_CONNECT = "custom_connect"
        const val KEY_CUSTOM_DISCONNECT = "custom_disconnect"
        const val KEY_CUSTOM_EXTRAS = "custom_extras"
        const val KEY_ENABLED = "enabled"
        const val KEY_SSIDS = "home_ssids"
        const val KEY_DELAY = "leave_delay_sec"
        const val KEY_LAST = "last_vpn_on"
    }
}
