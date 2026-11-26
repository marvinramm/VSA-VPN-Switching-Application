# VSA – VPN Switching Application

Android-App, die **Tailscale automatisch einschaltet, sobald du dein Heim-WLAN verlässt**, und **wieder ausschaltet, wenn du zuhause bist**.

## So funktioniert's

- Ein kleiner Hintergrunddienst (mit dauerhafter Benachrichtigung) beobachtet die WLAN-Verbindung.
- **Heim-WLAN verbunden** → Tailscale wird sofort getrennt.
- **Kein / fremdes WLAN** → nach einer einstellbaren Verzögerung (Standard 20 s, gegen kurze Funklöcher) wird Tailscale verbunden.
- Geschaltet wird nur beim *Wechsel*. Schaltest du Tailscale zwischendurch von Hand um, bleibt das so bis zum nächsten Kommen/Gehen.
- Tailscale wird über seine offiziellen Intents gesteuert (`com.tailscale.ipn.CONNECT_VPN` / `DISCONNECT_VPN` an `com.tailscale.ipn.IPNReceiver`).
- Mehrere Heim-WLANs möglich (z. B. 2,4 GHz und 5 GHz mit unterschiedlichem Namen).
- Startet nach einem Neustart des Handys automatisch.

## Einrichtung

1. Tailscale installieren, einmal öffnen, einloggen und einmal verbinden (VPN-Erlaubnis bestätigen).
2. APK installieren (siehe unten) und VSA öffnen.
3. Im Heim-WLAN auf **„Aktuelles WLAN hinzufügen“** tippen (oder den Namen von Hand eintragen).
4. **„Automatik aktiv“** einschalten und die Einrichtungsschritte abarbeiten:
   - Standort: **„Immer erlauben“**. Android gibt den WLAN-Namen nur mit Standortzugriff heraus; VSA speichert oder verschickt keinen Standort.
   - Standort (GPS) muss systemweit eingeschaltet sein.
   - Benachrichtigungen erlauben.
   - Empfohlen: Akku-Optimierung für VSA abschalten (v. a. bei Samsung/Xiaomi/Huawei).

## Bauen

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Bei jedem Push baut GitHub Actions die APK automatisch. Du findest sie im Tab **Actions** → letzter Lauf → Artefakt **VSA-debug-apk**.

Voraussetzungen: JDK 17+, Android SDK (Platform 35). Mindestens Android 8.0.
