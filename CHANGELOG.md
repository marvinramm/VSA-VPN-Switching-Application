# Changelog

All notable changes to VSA are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.1.0] - 2026-11-26

### Added
- **Multiple languages:** English, German, French, Spanish, Italian and Dutch. The app follows the phone's system language and falls back to English.
- Per-app language selection on Android 13+ (*Settings → Apps → VSA → Language*).
- License: [PolyForm Noncommercial 1.0.0](LICENSE.md). Commercial use requires a prior agreement, see [COMMERCIAL.md](COMMERCIAL.md).

### Changed
- English is now the default language of the app.
- Documentation is now in English.

## [1.0.0] - 2026-11-26

First release.

### Added
- Automatic VPN switching: home Wi-Fi connected → off, away → on.
- Multiple home networks; add the current Wi-Fi with one tap or type its name.
- Adjustable delay when leaving (0–120 s) to ride out short dropouts. Coming home disconnects immediately.
- Supported VPN apps:
  - **Tailscale**
  - **WireGuard** (official app, controlled by tunnel name)
  - **Other app** via a fully configurable broadcast
- Status shown in a persistent notification, including errors.
- Setup assistant for permissions, location, battery optimization and the VPN app.
- Starts automatically after a reboot.
- New app icon (shield with Wi-Fi symbol), including a themed icon for Android 13+.

[Unreleased]: ../../compare/v1.1.0...HEAD
[1.1.0]: ../../compare/v1.0.0...v1.1.0
[1.0.0]: ../../releases/tag/v1.0.0
