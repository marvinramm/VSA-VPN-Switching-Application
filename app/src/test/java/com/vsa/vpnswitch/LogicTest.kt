package com.vsa.vpnswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogicTest {

    @Test
    fun `SSID wird von Anführungszeichen befreit`() {
        assertEquals("FritzBox 7590", WifiWatcher.normalize("\"FritzBox 7590\""))
        assertEquals("Heim", WifiWatcher.normalize("Heim"))
    }

    @Test
    fun `unbekannte oder leere SSIDs ergeben null`() {
        assertNull(WifiWatcher.normalize(null))
        assertNull(WifiWatcher.normalize(""))
        assertNull(WifiWatcher.normalize("<unknown ssid>"))
        assertNull(WifiWatcher.normalize("\"\""))
    }

    @Test
    fun `Extras werden zeilenweise gelesen`() {
        val extras = VpnController.parseExtras(
            """
            profile = Zuhause
            # Kommentar
            ungültig
            =ohneSchlüssel
            url=https://a.b/c?x=1
            """.trimIndent(),
        )
        assertEquals(listOf("profile" to "Zuhause", "url" to "https://a.b/c?x=1"), extras)
    }

    @Test
    fun `unbekannte Anbieter-ID fällt auf Tailscale zurück`() {
        assertEquals(VpnProvider.TAILSCALE, VpnProvider.fromId(null))
        assertEquals(VpnProvider.TAILSCALE, VpnProvider.fromId("gibtsnicht"))
        assertEquals(VpnProvider.WIREGUARD, VpnProvider.fromId("wireguard"))
    }
}
