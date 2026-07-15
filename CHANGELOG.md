# Changelog

All notable changes to KordX are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.2] - 2026-07-15

### Fixed
- **App crash on Android 13+ (API 33+) at startup, release builds only** —
  the v1.1.0/v1.1.1 release APK had **two R8/ProGuard bugs** in the i18n
  loader path that only manifested on signed release builds. The AVD
  validation gate missed both because it runs a DEBUG build (no R8). The
  v1.1.1 `RadioNativeReceiver` fix unmasked the deeper bug: once the
  `SecurityException` stopped throwing, the i18n `NoSuchElementException`
  was exposed on every API 33+ device.
  - **Bug 1 (silently stripped `_Keys` constructor):** the ProGuard rules
    used the wrong JVM class names. `_Keys` is nested in `_Translation`
    (NOT in `Translation`), so the JVM FQN is `_Translation$_Keys`; the
    previous `-keep class ...Translation$_Keys { *; }` rule never matched
    anything. R8 stripped the primary 251-parameter constructor of
    `_Keys`; the `keysConstructor` lazy delegate threw
    `NoSuchElementException: Array contains no element matching the
    predicate` on `.first { parameterCount > 0 }`; the exception
    propagated out of `Translation.<init>` → the `KordX` ViewModel
    constructor → the framework's `ViewModelProvider` factory, surfacing
    as the red "Cannot create an instance of class
    com.android.rockages.kordx.KordX" crash screen.
  - **Bug 2 (silently produced wrong labels):** even with the correct
    `-keep` rule, the loader would still produce "nonempty but WRONG
    labels" (Songs field showing "Add media folders", etc.) because the
    constructor parameter names are read via `Parameter.getName()`,
    which requires the JVM `MethodParameters` attribute. R8 strips
    `MethodParameters` by default in non-full mode even though the
    kotlinc flag `-java-parameters` (pinned in `app/build.gradle.kts`)
    emits it. The `-keepattributes` rule now includes `MethodParameters`.
  - Companion regression test `I18nProguardRulesTest` (9 tests) pins
    the contract so both bugs can't reappear: the 5 `_Translation$_*`
    classes are kept, `MethodParameters` is kept, and the typo'd
    v1.1.0/v1.1.1 `Translation$_*` rules are explicitly forbidden.
  - AVD-verified on Phone AVD (API 36) with the **release** APK:
    `topResumedActivity = MainActivity`; the bottom nav renders the
    standard 5 tabs ("For you / Songs / Albums / Artists / Playlists");
    the intro dialog "👋 Hello there!" renders the full KordX open-beta
    message; no FATAL / NoSuchElementException in logcat. Issue: #2.
    PR: #4.

## [1.1.1] - 2026-07-15

### Fixed
- **App crash on Android 13+ (API 33+) at startup** — `RadioNativeReceiver.start()`
  was the only production `Context.registerReceiver` call site missed in the
  v1.1.0 "Debug receiver gating" slice. On API 33+ (Tiramisu), the unflagged
  `registerReceiver` overload throws `SecurityException` at runtime, which
  propagated out of the `Radio` constructor → the `KordX` ViewModel
  constructor → the framework's `ViewModelProvider` factory, surfacing as
  the red "Cannot create an instance of class com.android.rockages.kordx.KordX"
  crash screen and a fully black-holed `MainActivity` on every API 33+ device.
  The fix branches on `Build.VERSION.SDK_INT >= TIRAMISU` and passes
  `Context.RECEIVER_NOT_EXPORTED` (correct: `ACTION_AUDIO_BECOMING_NOISY` and
  `ACTION_HEADSET_PLUG` are protected system broadcasts — no external app is
  the sender). The pre-Tiramisu fallback uses the unflagged overload gated
  by `@SuppressLint("UnspecifiedRegisterReceiverFlag")` — same pattern as
  `RadioSession.start()` and `KordXMediaLibraryService.registerDebugReceivers()`.
  Companion regression test `RadioNativeReceiverRegistrationTest` (5 tests)
  pins the contract. Issue: #2. PR: #3. AVD-verified on API 36:
  `topResumedActivity` = `MainActivity`; `dumpsys activity broadcasts`
  confirms `RadioNativeReceiver` is registered for `HEADSET_PLUG` +
  `AUDIO_BECOMING_NOISY`; no `FATAL` / `SecurityException` in logcat.

## [1.1.0] - 2026-07-11

### Added
- Per-architecture release APKs (arm64-v8a, armeabi-v7a, x86, x86_64) via
  `splits.abi`. The arm64-v8a release APK is **9.4 MB** (down from 85 MB
  universal debug). A 5th **universal** release APK is also produced for
  F-Droid and manual sideloads (5 APKs total per release, all signed).
- A signed-release CI workflow at `.github/workflows/release.yml` that
  builds the 5 APKs and attaches them to the GitHub Release on `v*.*.*`
  tag push. The release keystore lives as 4 GitHub Secrets
  (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
  set by the repo owner; the workflow decodes them at build time and
  cleans up the ephemeral runner afterwards. Manual upload fallback
  documented in [`docs/RELEASING.md`](docs/RELEASING.md).
- R8 + resource shrinking for the release build type, with hand-written
  ProGuard rules (`app/proguard-rules.pro`) for the kotlinx.serialization
  i18n codegen. Gradle heap increased to 8 GB to fit the kover + detekt +
  R8 pipeline.
- detekt 1.23.6 static analysis with a Compose-aware rule set
  (`config/detekt/detekt.yml`) and per-module `detekt-baseline.xml` files
  that absorb pre-existing style findings, so new code is held to the rule
  set while the build stays green.
- Kover 0.8.3 coverage report infrastructure (multi-module artifact
  aggregation, class-name excludes for generated i18n / Room / BuildConfig
  classes). Coverage data is currently reported as 0% due to upstream
  kover issue [#810](https://github.com/Kotlin/kotlinx-kover/issues/810);
  the report infrastructure is correct and coverage data will auto-populate
  once the upstream fix lands.
- GitHub Actions CI workflow at `.github/workflows/ci.yml` (pinned
  `ubuntu-24.04`, Gradle dependency caching, all 4 modules in the build
  matrix, release + lint-release verification, detekt, explicit
  `GITHUB_TOKEN` permissions). Runs on every push to `main` and every PR.
- A `MediaSearchActivity` alias forwarding path so the AAOS voice-search
  button reaches the existing `RadioSession.handlePlayFromSearch` route
  (previously a dead code path). 15 source-based regression tests pin the
  contract.
- A `Radio.play()` recursion guard for the stale-id path; the Slice 22
  `ACTION_SHUFFLE_ALL` direct-field-write workaround is reverted in favor
  of the canonical `RadioShorty.playQueue(...)` API.
- A `BuildConfig.DEBUG` gate around the 4 debug-only broadcast receivers
  in `RadioSession` and `KordXMediaLibraryService`. The production media
  receiver (PLAY_PAUSE / PREVIOUS / NEXT / STOP) is intentionally NOT
  gated.
- Settings UI cleanup: the "Consider Contributing" stripe and the
  Play-Store / GitHub / F-Droid `LinkChip` rows are removed from all 7
  settings surfaces. The KordX "K" logo is shrunken to 70% via a
  VectorDrawable `<group>` transform across all 3 foreground drawables.
  10 dead WebP raster mipmaps (API 26+) are deleted.
- The Hindi (`hi`) and Telugu (`te`) locales replace the previously
  shipped Belarusian (`be`) and Okinawan (`ryu`) locales. All 18 locales
  now have the full 251-key set; no more empty-string fallbacks for the
  Slice 30h additions (`Repeat` / `Shuffle` / `More` / `CollapseNowPlaying`).
- DropdownMenu modernization across 30 Compose surfaces (Material 3
  tokens), long-menu organization (dividers between groups), dialog and
  surface radius upgrade (24 dp → 28 dp), and a token consistency pass on
  the remaining Compose hierarchy.
- A dark-theme + light-theme variant of the KordX "K" logo under
  `app/src/main/assets/{darklogo,lightlogo}.svg`.
- An `AGPL-3.0` `LICENSE` file at the repo root and a `CONTRIBUTING.md`
  guide for new contributors.

### Changed
- The release build is now R8-minified; the debug build is not. ABI splits
  are enabled in release (per-architecture APKs) and disabled in debug
  (single universal APK for fast local iteration).
- `kordx-ui.xml` (an early validation UI dump) and the `kordx_logo_design/`
  scratchpad are moved out of the repo root into `archive/`. The brand
  tokens they captured live in `app/src/main/res/values/themes.xml` and
  the launcher icon drawables.

### Notes
- The "missing search icon on top bar" report from 2026-07-10 was not
  reproducible on a 2026-07-11 re-check: the search icon is present at
  `[22,232][147,357]` and wired to `Home.kt → SearchViewRoute(...)` since
  the branding commit `1c8fd18`. No code change.
- 258 JVM unit tests pass (248 in `:app`, 10 in `:metaphony`); `:app:lintDebug`
  and `detekt` are green; the on-device smoke (Phone AVD, API 36) confirms
  `dumpsys media_session` shows `active=true`.
- The dev / test bed (`charan1601/remusic`) hosts the slice workflow +
  cleanup specs + screenshots; the public release (this repo) ships only
  the polished source tree. Promotion happens at milestone boundaries.

## [1.0.0] - 2026-07-11

### Added
- Offline music playback from on-device audio files.
- Library browsing by songs, albums, artists, genres, and filesystem folders.
- Storage Access Framework (SAF) picker for choosing which media folders to index.
- Playlists and favorites with persisted queue, shuffle, and repeat state.
- Android Auto support via the Media3 media-browse session.
- Material 3 UI with light/dark themes and dynamic color.
- Search across the library.
- Sleep timer.
- Multi-language support with 18 shipped locales (de, en, es, fa, fi, fr, hi,
  it, ja, pl, pt, ro, ru, te, tr, uk, vi, zh-Hans).
- Native audio metadata reading via vendored taglib (JNI) in the `metaphony`
  module.

### Notes
- Minimum SDK is 31 (Android 12); target/compile SDK is 35.
- Built with Kotlin 2.1, Jetpack Compose 1.7 / Material 3, Room 2.6, and
  AndroidX Media3 1.7.
- Licensed under AGPL-3.0.
