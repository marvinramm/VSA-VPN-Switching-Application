# Creating a release

Releases are built by GitHub Actions (`.github/workflows/release.yml`).

## Steps

1. Bump the version in `app/build.gradle.kts`:
   - `versionCode` must be **higher** than in the previous release, otherwise Android rejects the update.
   - `versionName` is the displayed version, e.g. `1.1.0`.
2. In `CHANGELOG.md`, rename `## [Unreleased]` to `## [1.1.0] - YYYY-MM-DD` (or add that section). It becomes the release notes automatically.
3. Commit and push to `main`.
4. Start the release, either way works:
   - **On GitHub:** *Actions → Release → "Run workflow"* on `main`. The tag `v<versionName>` is created automatically.
   - **Via tag:**
     ```bash
     git tag v1.1.0
     git push origin v1.1.0
     ```
5. After 2–3 minutes, `VSA-1.1.0.apk` and its SHA-256 checksum appear under **Releases**.

## Signing

Android only installs an update if it is signed with the **same key** as the installed version.

**Default:** All builds (debug, release, local and on GitHub Actions) are signed with the project key `signing/vsa-shared.keystore`. This way every build can be installed as an update of every other build.

> ⚠️ This key is stored in the repository with the default password `android`. Anyone with access to the repository can sign an APK that Android accepts as an update of VSA. That's fine for a private repository and personal use. Before making the repository public, switch to your own key.

### Using your own key (optional)

```bash
keytool -genkeypair -v -keystore vsa-release.jks -alias vsa \
        -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 vsa-release.jks   # copy the output
```

Create these repository secrets under *GitHub → Settings → Secrets and variables → Actions*:

| Secret | Value |
|---|---|
| `VSA_KEYSTORE_BASE64` | Base64 output from above |
| `VSA_KEYSTORE_PASSWORD` | Keystore password |
| `VSA_KEY_ALIAS` | `vsa` |
| `VSA_KEY_PASSWORD` | Key password |

From then on the release workflow signs with your key. **Important:** switching keys requires uninstalling the old app once. Keep the `.jks` file and passwords safe. Without them, no further updates are possible.

To sign locally with the same key, set the environment variables `VSA_KEYSTORE_FILE`, `VSA_KEYSTORE_PASSWORD`, `VSA_KEY_ALIAS` and `VSA_KEY_PASSWORD`.
