# Building KordX

This guide covers building KordX from the command line (headless / CI) as well
as the usual Android Studio flow.

## Toolchain

| Tool            | Version                       | Notes                                   |
|-----------------|-------------------------------|-----------------------------------------|
| JDK             | 17                            | Set `JAVA_HOME` accordingly             |
| Android SDK     | Platform 35 + build-tools 35  | `compileSdk` / `targetSdk` = 35         |
| Android NDK     | r27 (`27.0.12077973`)         | Required by the native `metaphony` module |
| CMake           | 3.22.1                        | Native build backend for `metaphony`    |
| Gradle          | Wrapper-provided              | Use `./gradlew`; no global install needed |
| Node.js         | 22                            | Optional — only to regenerate i18n assets locally |
| Kotlin          | 2.1                           | Enforced by the version catalog         |

`minSdk` is 31 (Android 12).

## 1. Clone

```bash
git clone https://github.com/Sreecharannuthi/kordx.git
cd kordx
```

## 2. Internationalization (i18n)

The translation strings live as TOML sources under `i18n/`. They are compiled
into the JSON assets the app loads at runtime (`app/src/main/assets/i18n/`),
which are **committed to the repository**, so a normal build needs no extra
generation step.

To update translations, edit the TOML sources and regenerate the bundled
assets locally, then commit the results.

## 3. Build

```bash
# Debug APK (per-ABI splits: arm64-v8a, armeabi-v7a, x86, x86_64)
./gradlew :app:assembleDebug

# Or install straight to a connected device/emulator
./gradlew :app:installDebug
```

Release builds enable minification and resource shrinking:

```bash
./gradlew :app:assembleRelease
```

## 4. Tests & static analysis

```bash
# JVM unit tests
./gradlew :app:testDebugUnitTest :metaphony:testDebugUnitTest

# Lint + detekt
./gradlew :app:lintDebug detekt
```

Instrumentation tests (`androidTest`) need a running emulator/device and are not
part of the default CI job.

## 5. Android Studio

Open the repository root in Android Studio (Hedgehog or newer), let it sync the
Gradle project, then run the `:app` configuration. Make sure the NDK and CMake
are installed via **SDK Manager → SDK Tools**.

## Media folder management (SAF)

KordX indexes audio via the Storage Access Framework. On first run (and from
Settings) the app launches a system SAF picker so the user can grant access to
the media folders they want indexed. No files leave the device and no special
storage permissions are required beyond what SAF provides.

## Troubleshooting

- **`CMake` / NDK not found** — install NDK r27 and CMake 3.22.1 from the SDK
  Manager, or set `ANDROID_NDK_HOME`.
- **i18n assets** — the bundled translation JSON in `app/src/main/assets/i18n/` is
  committed to the repo; a normal build does not require generating them. To change
  strings, edit `i18n/*.toml` and regenerate the assets, then commit.
- **`detekt` baseline churn** — the project ships a detekt baseline
  (`config/detekt/baseline.xml` and per-module `detekt-baseline.xml`). New
  violations are reported; run `./gradlew detekt` to see them.
- **Coverage shows 0%** — kover reports 0% for classes under `com.android.*`
  due to a known upstream kover issue (#810); this is expected and not a build
  failure.
- **R8 / dalvik verifier crash on launch** — `android.enableR8.fullMode` is
  intentionally `false` to work around a dalvik split-verifier bug with the
  large generated i18n serialization class. Do not flip it on without testing.
