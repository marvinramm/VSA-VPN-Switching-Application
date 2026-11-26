package com.vsa.vpnswitch

import android.content.Context
import android.content.Intent

/**
 * Steuert die offizielle Tailscale-App über ihren exportierten Broadcast-Receiver.
 * (com.tailscale.ipn.IPNReceiver versteht CONNECT_VPN und DISCONNECT_VPN.)
 */
object Tailscale {
    const val PACKAGE = "com.tailscale.ipn"
    private const val RECEIVER = "com.tailscale.ipn.IPNReceiver"
    private const val ACTION_CONNECT = "com.tailscale.ipn.CONNECT_VPN"
    private const val ACTION_DISCONNECT = "com.tailscale.ipn.DISCONNECT_VPN"

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PACKAGE) != null

    fun setConnected(context: Context, on: Boolean) {
        val intent = Intent(if (on) ACTION_CONNECT else ACTION_DISCONNECT)
            .setClassName(PACKAGE, RECEIVER)
        context.sendBroadcast(intent)
    }
}
