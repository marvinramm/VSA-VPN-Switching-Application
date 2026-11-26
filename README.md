<p align="center">
  <img src="docs/icon.png" width="128" alt="VSA icon">
</p>

<h1 align="center">VSA – VPN Switching Application</h1>

<p align="center">
  Android app that <b>automatically turns your VPN on when you leave your home Wi-Fi</b><br>
  and <b>turns it off again when you get back home</b>.
</p>

<p align="center">
  <a href="../../releases/latest"><b>⬇ Download the latest release</b></a>
</p>

---

## Contents

- [Features](#features)
- [Supported VPN apps](#supported-vpn-apps)
- [Installation](#installation)
- [Setup](#setup)
- [How the automation works](#how-the-automation-works)
- [Permissions & privacy](#permissions--privacy)
- [Troubleshooting](#troubleshooting)
- [Development](#development)
- [License](#license)

> The app's user interface is currently in **German**. UI labels below are quoted in German with an English translation.

## Features

- 🏠 **Define your home Wi-Fi**: add the network you are connected to with one tap, or type its name (SSID). Multiple home networks are supported, e.g. separate 2.4 GHz / 5 GHz names or repeaters.
- 🔒 **Automatic switching**: VPN off at home, VPN on everywhere else.
- ⏱️ **Delay against dropouts**: after leaving home, VSA waits for an adjustable time (0–120 s, default 20 s) before connecting. Coming home disconnects immediately.
- 🔌 **Multiple VPN services**: Tailscale, WireGuard, or any app that can be controlled via broadcast intents.
- 🔁 **Runs in the background** and restarts automatically after a reboot.
- ✋ **Respects manual control**: VSA only switches when you arrive or leave. If you toggle the VPN yourself in between, VSA leaves it alone.
- 🧭 **Setup assistant**: shows what is still missing and takes you to the right setting with „Jetzt beheben“ (*Fix now*).

## Supported VPN apps

| VPN app | How VSA controls it | What you need to do |
|---|---|---|
| **[Tailscale](https://play.google.com/store/apps/details?id=com.tailscale.ipn)** | Broadcast `com.tailscale.ipn.CONNECT_VPN` / `DISCONNECT_VPN` | Log in once and connect once |
| **[WireGuard](https://play.google.com/store/apps/details?id=com.wireguard.android)** (official app) | Broadcast `SET_TUNNEL_UP` / `SET_TUNNEL_DOWN` with the tunnel name | In WireGuard, enable *Settings → "Allow remote control apps"*. In VSA, enter the tunnel name and grant the permission |
| **Other app** | Fully configurable broadcast (package, actions, extras) | Enter the app's broadcast interface (see that app's documentation) |

> **Why not ProtonVPN, NordVPN, Mullvad & co.?**
> Android does not allow an app to switch *another* app's VPN directly. This only works if the VPN app itself offers an interface for it, as Tailscale and WireGuard do. If an app offers such an interface, you can connect it via **"Other app"**.

<details>
<summary><b>Example "Other app"</b>: Tailscale configured manually</summary>

This is what Tailscale would look like if it weren't built in:

| Field | Value |
|---|---|
| Package name | `com.tailscale.ipn` |
| Receiver class | `com.tailscale.ipn.IPNReceiver` |
| Connect action | `com.tailscale.ipn.CONNECT_VPN` |
| Disconnect action | `com.tailscale.ipn.DISCONNECT_VPN` |
| Extras | *(empty)* |

Extras are written as `key=value`, one per line, and passed as strings. Lines starting with `#` are ignored.
</details>

## Installation

1. Download `VSA-x.y.z.apk` from [**Releases**](../../releases/latest) to your phone.
2. Open it and allow installing from this source when Android asks.
3. To update, install the new version over the old one. Your settings are kept.

Requires **Android 8.0 or newer**.

## Setup

1. **Prepare your VPN app**
   - *Tailscale:* open it, log in, connect once and confirm Android's VPN request.
   - *WireGuard:* create a tunnel, connect once, then enable "Allow remote control apps" in its settings.
2. **Open VSA**, choose your app under „VPN-Dienst“ (*VPN service*). For WireGuard or "Other app", fill in the fields and tap „Speichern“ (*Save*).
3. While connected to your home Wi-Fi, tap „Aktuelles WLAN hinzufügen“ (*Add current Wi-Fi*).
4. Turn on „Automatik aktiv“ (*Automation enabled*) and work through the items in the „Einrichtung“ (*Setup*) box:
   - Set location access to **"Allow all the time"** (see [Privacy](#permissions--privacy)).
   - Keep location (GPS) turned on.
   - Allow notifications.
   - Recommended: disable battery optimization for VSA.
5. Use „VPN manuell schalten“ (*Switch VPN manually*) to check that VSA can reach your VPN app.

Done. The notification always shows what VSA is doing, e.g. *„Zuhause („FRITZ!Box 7590“) – Tailscale aus“* (*At home – Tailscale off*).

## How the automation works

```
        Home Wi-Fi connected                    No Wi-Fi / other Wi-Fi
  ┌─────────────────────────────────┐   ┌──────────────────────────────────┐
  │  Disconnect VPN immediately     │   │  Wait for the delay (default 20 s)│
  │  (cancel any running countdown) │   │  → connect VPN                    │
  └─────────────────────────────────┘   └──────────────────────────────────┘
```

- A foreground service watches all Wi-Fi connections using Android's network callback API. It doesn't poll, so battery usage is minimal.
- If you **come back before the delay expires** (e.g. a short dropout), nothing happens.
- VSA only sends a command **on a transition** between "home" and "away". The current state is sent once when the service starts and after the VPN settings change.
- If Wi-Fi is connected but its name can't be read (location off or permission missing), VSA **does not switch at all** and shows a warning instead.
- If a command fails (e.g. the VPN app was uninstalled), the reason is shown in the notification. VSA tries again on the next network change.

## Permissions & privacy

VSA has **no internet permission**. It collects and sends nothing, and all settings stay on the device.

| Permission | Why |
|---|---|
| Location (precise + "Allow all the time") | Android only reveals the **Wi-Fi name** to apps with location permission, and in the background only with "Allow all the time". VSA **does not request or store your location**. |
| Wi-Fi / network state | Detect which Wi-Fi the phone is connected to |
| Foreground service + notifications | Run in the background and show the status |
| Run at startup | Resume monitoring after a reboot |
| Ignore battery optimizations (optional) | Prevents aggressive power-saving modes from killing VSA |
| Control WireGuard tunnels | WireGuard only: turn tunnels on and off |
| Query all packages | "Other app" only: Android 11+ requires this so VSA can find and address arbitrary VPN apps |

## Troubleshooting

**„WLAN-Name nicht lesbar“ (*Wi-Fi name not readable*)**
Turn on location and set *Settings → Apps → VSA → Permissions → Location* to **"Allow all the time"** with **"Use precise location"** enabled.

**The VPN doesn't switch although the status is correct**
- Test with „VPN manuell schalten“ (*Switch VPN manually*).
- *Tailscale:* are you logged in? Has Android stored the VPN permission for Tailscale?
- *WireGuard:* does the tunnel name match exactly, including upper/lower case? Is "Allow remote control apps" enabled?
- Is **Always-on VPN** enabled for a *different* app in Android's settings? That blocks other VPNs.

**VSA stops working after a while**
Disable battery optimization for VSA. On Samsung, also check *Battery → Background usage limits* and make sure VSA isn't listed as sleeping. On Xiaomi, allow *Autostart*. See [dontkillmyapp.com](https://dontkillmyapp.com) for details.

**My home network has several names**
Add all of them as home Wi-Fi.

## Development

```bash
./gradlew assembleDebug          # debug APK   → app/build/outputs/apk/debug/
./gradlew assembleRelease        # release APK → app/build/outputs/apk/release/
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android Lint
```

Requirements: JDK 17+, Android SDK with platform 35.

| File | Purpose |
|---|---|
| `WifiWatcher.kt` | Watches Wi-Fi connections and reads their SSIDs |
| `WifiMonitorService.kt` | Foreground service with the switching logic (home/away, delay) |
| `VpnProvider.kt` | Integration with Tailscale, WireGuard and custom broadcasts |
| `MainActivity.kt` | User interface and setup assistant |
| `Prefs.kt` | Settings |
| `BootReceiver.kt` | Start after boot |

GitHub Actions builds and tests every push (artifact **VSA-debug-apk**). Releases are created by the release workflow, see [docs/RELEASE.md](docs/RELEASE.md).

Changes are listed in the [CHANGELOG](CHANGELOG.md).

## License

VSA is **source-available** under the [PolyForm Noncommercial License 1.0.0](LICENSE.md).

| | |
|---|---|
| ✅ Private use | free |
| ✅ Modifying and further developing it for noncommercial purposes | free, as long as the license and copyright notice are kept |
| ✅ Use by noncommercial organizations (schools, charities, public institutions) | free |
| 💼 **Commercial use** (selling, monetizing, use in or by companies, paid services) | **only with a prior agreement**, e.g. a license fee or revenue share. See [COMMERCIAL.md](COMMERCIAL.md) |

Copyright © 2026 marvinramm

---

*VSA is an independent project and is not affiliated with or endorsed by Tailscale Inc. or the WireGuard project. "Tailscale" and "WireGuard" are trademarks of their respective owners.*
