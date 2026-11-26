package com.vsa.vpnswitch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Die VPN-Apps, die VSA steuern kann. */
enum class VpnProvider(val id: String, val packageName: String?) {
    TAILSCALE("tailscale", "com.tailscale.ipn"),
    WIREGUARD("wireguard", "com.wireguard.android"),
    CUSTOM("custom", null);

    /** Name für Status und Meldungen. */
    fun displayName(context: Context): String = when (this) {
        TAILSCALE -> "Tailscale"
        WIREGUARD -> "WireGuard"
        CUSTOM -> context.getString(R.string.vpn_generic)
    }

    companion object {
        fun fromId(id: String?): VpnProvider = entries.firstOrNull { it.id == id } ?: TAILSCALE
    }
}

/** Ergebnis eines Schaltbefehls. */
sealed class SendResult {
    data object Sent : SendResult()
    data class Failed(val reason: String) : SendResult()
}

/**
 * Schickt den Verbinden/Trennen-Befehl an die gewählte VPN-App.
 *
 * - Tailscale:  Broadcast com.tailscale.ipn.CONNECT_VPN / DISCONNECT_VPN an com.tailscale.ipn.IPNReceiver
 * - WireGuard:  Broadcast com.wireguard.android.action.SET_TUNNEL_UP / SET_TUNNEL_DOWN mit Extra "tunnel"
 *               (benötigt in WireGuard „Fernsteuerung durch Apps erlauben“ und die Berechtigung CONTROL_TUNNELS)
 * - Eigener:    frei konfigurierbarer Broadcast (Paket, optional Empfänger-Klasse, Aktionen, Extras)
 */
object VpnController {

    const val WIREGUARD_PERMISSION = "com.wireguard.android.permission.CONTROL_TUNNELS"

    private const val TS_RECEIVER = "com.tailscale.ipn.IPNReceiver"
    private const val TS_CONNECT = "com.tailscale.ipn.CONNECT_VPN"
    private const val TS_DISCONNECT = "com.tailscale.ipn.DISCONNECT_VPN"

    private const val WG_UP = "com.wireguard.android.action.SET_TUNNEL_UP"
    private const val WG_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"
    private const val WG_EXTRA_TUNNEL = "tunnel"

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)

    fun hasWireGuardPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, WIREGUARD_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Beschreibung des Problems, falls der aktuell gewählte Dienst noch nicht schaltbar ist – sonst null. */
    fun configProblem(context: Context, prefs: Prefs): String? = when (prefs.provider) {
        VpnProvider.TAILSCALE ->
            if (!isInstalled(context, VpnProvider.TAILSCALE.packageName!!)) {
                context.getString(R.string.err_not_installed, "Tailscale")
            } else {
                null
            }

        VpnProvider.WIREGUARD -> when {
            !isInstalled(context, VpnProvider.WIREGUARD.packageName!!) ->
                context.getString(R.string.err_not_installed, "WireGuard")
            prefs.wireguardTunnel.isBlank() -> context.getString(R.string.err_wg_no_tunnel)
            !hasWireGuardPermission(context) -> context.getString(R.string.err_wg_no_permission)
            else -> null
        }

        VpnProvider.CUSTOM -> when {
            prefs.customConnectAction.isBlank() || prefs.customDisconnectAction.isBlank() ->
                context.getString(R.string.err_custom_no_actions)
            prefs.customPackage.isBlank() ->
                context.getString(R.string.err_custom_no_package)
            !isInstalled(context, prefs.customPackage.trim()) ->
                context.getString(R.string.err_not_installed, prefs.customPackage.trim())
            else -> null
        }
    }

    fun setConnected(context: Context, prefs: Prefs, on: Boolean): SendResult {
        configProblem(context, prefs)?.let { return SendResult.Failed(it) }
        val intent = when (prefs.provider) {
            VpnProvider.TAILSCALE ->
                Intent(if (on) TS_CONNECT else TS_DISCONNECT)
                    .setClassName(VpnProvider.TAILSCALE.packageName!!, TS_RECEIVER)

            VpnProvider.WIREGUARD ->
                Intent(if (on) WG_UP else WG_DOWN)
                    .setPackage(VpnProvider.WIREGUARD.packageName)
                    .putExtra(WG_EXTRA_TUNNEL, prefs.wireguardTunnel.trim())

            VpnProvider.CUSTOM -> {
                val pkg = prefs.customPackage.trim()
                val cls = prefs.customReceiverClass.trim()
                Intent(if (on) prefs.customConnectAction.trim() else prefs.customDisconnectAction.trim()).apply {
                    if (cls.isNotEmpty()) setClassName(pkg, cls) else setPackage(pkg)
                    parseExtras(prefs.customExtras).forEach { (k, v) -> putExtra(k, v) }
                }
            }
        }
        return runCatching {
            context.sendBroadcast(intent)
            SendResult.Sent
        }.getOrElse { SendResult.Failed(it.message ?: it.javaClass.simpleName) }
    }

    /** Extras im Format `schlüssel=wert`, eine Zeile pro Extra. Werte werden als Text übergeben. */
    fun parseExtras(text: String): List<Pair<String, String>> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .map { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
            .filter { it.first.isNotEmpty() }
}
