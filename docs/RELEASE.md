# Release erstellen

Releases baut GitHub Actions automatisch (`.github/workflows/release.yml`), sobald ein Tag `v*` gepusht wird.

## Ablauf

1. In `app/build.gradle.kts` die Version erhöhen:
   - `versionCode` muss **größer** als beim letzten Release sein, sonst lehnt Android das Update ab.
   - `versionName` ist die angezeigte Version, z. B. `1.1.0`.
2. In `CHANGELOG.md` einen Abschnitt `## [1.1.0] - JJJJ-MM-TT` anlegen. Er wird automatisch zum Release-Text.
3. Committen, Tag setzen, pushen:
   ```bash
   git commit -am "Version 1.1.0"
   git tag v1.1.0
   git push origin main v1.1.0
   ```
4. Nach 2–3 Minuten liegt unter **Releases** die Datei `VSA-1.1.0.apk` samt SHA-256-Prüfsumme.

## Signierung

Android installiert ein Update nur, wenn es mit **demselben Schlüssel** signiert ist wie die installierte Version.

**Standard:** Alle Builds (Debug, Release, lokal und in GitHub Actions) werden mit dem Projektschlüssel `signing/vsa-shared.keystore` signiert. Dadurch lassen sich alle Builds gegenseitig als Update installieren.

> ⚠️ Dieser Schlüssel liegt im Repository und hat das Standardpasswort `android`. Jeder mit Zugriff auf das Repo kann damit eine APK signieren, die Android als Update von VSA akzeptiert. Für ein privates Repo und den Eigengebrauch ist das in Ordnung. Wird das Repo öffentlich, solltest du auf einen eigenen Schlüssel umsteigen.

### Eigenen Schlüssel verwenden (optional)

```bash
keytool -genkeypair -v -keystore vsa-release.jks -alias vsa \
        -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 vsa-release.jks   # Ausgabe kopieren
```

Unter *GitHub → Settings → Secrets and variables → Actions* diese Repository-Secrets anlegen:

| Secret | Inhalt |
|---|---|
| `VSA_KEYSTORE_BASE64` | Base64-Ausgabe von oben |
| `VSA_KEYSTORE_PASSWORD` | Keystore-Passwort |
| `VSA_KEY_ALIAS` | `vsa` |
| `VSA_KEY_PASSWORD` | Schlüssel-Passwort |

Ab dann signiert der Release-Workflow mit deinem Schlüssel. **Wichtig:** Der Wechsel des Schlüssels erfordert einmalig eine Deinstallation der alten App. Die `.jks`-Datei und die Passwörter gut aufbewahren, ohne sie sind keine Updates mehr möglich.

Lokal signierst du mit demselben Schlüssel über die Umgebungsvariablen `VSA_KEYSTORE_FILE`, `VSA_KEYSTORE_PASSWORD`, `VSA_KEY_ALIAS` und `VSA_KEY_PASSWORD`.
