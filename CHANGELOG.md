# Changelog

Alle nennenswerten Änderungen an VSA. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach [SemVer](https://semver.org/lang/de/).

## [1.0.0] - 2026-11-26

Erste Veröffentlichung.

### Neu
- Schaltet das VPN automatisch: Heim-WLAN verbunden → aus, unterwegs → an.
- Mehrere Heim-WLANs, wahlweise das aktuelle WLAN übernehmen oder den Namen eintippen.
- Einstellbare Verzögerung beim Verlassen (0–120 s) gegen Funklöcher. Beim Heimkommen wird sofort getrennt.
- Unterstützte VPN-Apps:
  - **Tailscale**
  - **WireGuard** (offizielle App, Steuerung über den Tunnelnamen)
  - **Andere App** über einen frei konfigurierbaren Broadcast
- Statusanzeige in einer dauerhaften Benachrichtigung, Fehler werden dort ebenfalls angezeigt.
- Einrichtungsassistent für Berechtigungen, Standort, Akku-Optimierung und VPN-App.
- Startet nach einem Neustart des Handys automatisch.
- Neues App-Icon (Schild mit WLAN-Symbol), inklusive Themed Icon für Android 13+.
