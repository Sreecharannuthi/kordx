# Releasing KordX

This is the maintainer runbook for cutting a public release of KordX.

## How a release ships

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit + push to `main`.
3. Tag the commit: `git tag vX.Y.Z && git push origin vX.Y.Z`.
4. The `.github/workflows/release.yml` workflow fires on the tag push:
   - Decodes the `KEYSTORE_BASE64` secret to `app/keystore/kordx-release.jks`.
   - Writes `app/keystore.properties` from the other 3 secrets.
   - Runs `./gradlew :app:assembleRelease` (produces **5 signed APKs**:
     1 universal + 4 ABI splits).
   - Verifies each APK is signed with the release key.
   - Creates the GitHub Release and attaches the 5 APKs.
5. Users download from the GitHub Releases page.

For a manual run from the Actions tab (e.g., to re-build a release
without re-tagging), use **Run workflow → release**. The resulting
release is created as a **DRAFT** so you can review the assets
before publishing.

## Required GitHub Secrets

The release workflow reads 4 secrets. Set them in
**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | What it is | How to create it |
|---|---|---|
| `KEYSTORE_BASE64` | The `.jks` file, base64-encoded with **no newlines** | `base64 -w0 kordx-release.jks \| pbcopy` (Linux) or `base64 kordx-release.jks \| tr -d '\n' \| pbcopy` (macOS) |
| `KEYSTORE_PASSWORD` | The `.jks` password | The password you set when generating the keystore |
| `KEY_ALIAS` | The key alias inside the `.jks` | The alias you specified at generation |
| `KEY_PASSWORD` | The key password (often same as `KEYSTORE_PASSWORD`) | The password you set for the key |

**Only the repo owner (or admins) can add these.** Contributors
submitting a PR cannot add secrets to a repo they don't own.

The `KEYSTORE_BASE64` secret is a binary in disguise — the workflow
decodes it back to a `.jks` file at build time. The `.jks` is never
committed to the repo or written outside the ephemeral runner.

## Generating the keystore (one-time)

If you don't have a release keystore yet:

```bash
keytool -genkey -v \
  -keystore kordx-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias kordx \
  -storepass "$YOUR_KEYSTORE_PASSWORD" \
  -keypass "$YOUR_KEY_PASSWORD" \
  -dname "CN=KordX, OU=Apps, O=Sreecharannuthi, L=City, ST=State, C=IN"
```

Then:

```bash
# macOS:
base64 kordx-release.jks | tr -d '\n' | pbcopy
# Linux:
base64 -w0 kordx-release.jks
```

Paste the resulting one-line string into the `KEYSTORE_BASE64`
GitHub Secret. Store `kordx-release.jks` and the passwords somewhere
safe (a password manager). **If you lose them you can't update the
app on existing installs** — Android requires the same signature
for updates.

## The 5 APKs explained

| File | Size (approx, R8-minified) | When to use |
|---|---|---|
| `app-universal-release.apk` | ~15 MB | **Default.** Works on every device. Use for F-Droid, manual sideloads, quick testing. |
| `app-arm64-v8a-release.apk` | ~10 MB | Modern phones (2017+, ~95% of active devices). |
| `app-armeabi-v7a-release.apk` | ~9 MB | Older 32-bit ARM phones. |
| `app-x86_64-release.apk` | ~10 MB | Chromebooks, emulators. |
| `app-x86-release.apk` | ~10 MB | Very old 32-bit emulators. |

Sizes depend on the current R8 + resource-shrink output. The
universal APK is the largest because it ships all 4 native
`metaphony` ABIs.

## Manual upload fallback

If the workflow fails (e.g., a transient CI issue, a secret
rotation gone wrong, or you're on a private network), you can
build locally and upload manually:

```bash
# 1. Place a real keystore.properties at the repo root.
#    (Use app/keystore.properties.example as the template.)

# 2. Build the 5 release APKs.
./gradlew :app:assembleRelease

# 3. Create the GitHub Release and upload.
gh release create vX.Y.Z \
  app/build/outputs/apk/release/*.apk \
  --title "KordX vX.Y.Z" \
  --generate-notes
```

The local build produces the same 5 APKs (signed with whatever
keystore is in `keystore.properties`) and `gh` uploads them to a
newly-created release with the tag.

## Versioning

KordX follows [SemVer](https://semver.org/spec/v2.0.0.html):

- **Major** (`X.0.0`) — breaking UI/UX changes
- **Minor** (`0.Y.0`) — new features, backward-compatible
- **Patch** (`0.0.Z`) — bug fixes, backward-compatible

Bump `versionCode` and `versionName` in `app/build.gradle.kts`
before tagging:

- `versionCode` is a monotonically increasing integer (Play Store
  requires it; downgrades will refuse to install).
- `versionName` is the human-readable string shown in the app's
  About screen.

`AppMeta.version` is derived from `BuildConfig.VERSION_NAME`, so
it stays in sync automatically — no extra edit needed.

## Test the workflow before relying on it

The first time you set up the 4 secrets, do a **workflow_dispatch**
run (Actions tab → release → Run workflow) and verify the resulting
DRAFT release has 5 APKs that install correctly on a real device.
Once you've confirmed that, the tag-push path is trusted.
