# Changelog

All notable changes to KordX are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.1] - 2026-07-22

### Fixed
- **Radio playback stability (PB1–PB10)** — fixed leaked `Fader` timers and
  handler-scorch in `RadioPlayer`, audio-focus leaks in `Radio.stop()`,
  wake-lock acquisition during playback, swapped notification play/pause
  labels, and a pitch/speed misconfiguration in `applyPitch()`.
- **Auto-resume on launch (PB5)** — moved resume from process-start to
  `onKordXActivityReady()` and serialized queue restore with a `Mutex`. The
  resumed position now uses the persisted `playedDuration` instead of
  restarting from 0.
- **Media session update storm** — throttled `RadioSession.update()` to a
  150 ms minimum interval, preventing notification-manager rate-limiting and
  UI sluggishness during playback.
- **Fader identity race** — a `RadioEffects.Fader` stopped by a newer volume
  change can no longer null out the newer fader reference.

### Changed
- **Media session device volume** — `RadioForwardingPlayer` now reports the
  real system `STREAM_MUSIC` volume via `AudioManager` instead of hardcoding
  zero.

## [1.4.0] - 2026-07-23

### Added
- **Artist identity normalization (AX1)** — `Song.normalizeArtistKey()` in
  `:core` normalizes artist names (lowercase, trim, collapse whitespace) so
  spelling/case variants of the same artist merge into one profile in the
  Artists and Album Artists tabs. First-variant-wins canonical display name.
  13 pinning JVM tests.
- **Artists view overhaul (UI9)** — list/grid toggle in the sort bar. List
  view shows A–Z section headers, circular artist artwork, "N albums · N
  tracks" subtitle, and a play button. Grid tiles also show album/track
  counts.
- **Sort-reorder animations (UI10)** — `Modifier.animateItem()` on all song
  lists and artist/album grids; items animate smoothly into place when sort
  order changes.

### Changed
- **Options menu restructure (UI8)** — deduplicated artist/album-artist
  entries in song and album dropdown menus (same artist under different
  spellings appears once); plain artist names instead of "View Artist:"
  prefixes; album entries show the album name.
- **Compact detail headers (UI12)** — artist and album detail pages use a
  compact 56dp-row header instead of a full-width hero banner; the song list
  is now the primary content. Album tiles show "year · N songs" subtitle.
- **Search redesign** — rounded 28dp search bar, empty-state prompt with
  music-note icon, titleSmall section headers, consistent spacing.
- **Transition consistency (UI10-lite)** — every page now slides in from the
  left; only Now Playing / Queue / Lyrics keep the modal slide-up.
- **Now Playing layout compact (UI12)** — tightened gaps across metadata,
  transport controls, seek bar, and bottom bar (~90dp reclaimed); title sits
  closer to the artwork with 20dp breathing room above the seek bar; proper
  top/bottom padding for the system navigation bar.

### Fixed
- **ALBUMS_COUNT sort bug (AX1.4)** — `SortBy.ALBUMS_COUNT` in both artist
  repositories was sorting by `numberOfTracks` (copy-paste bug); now sorts by
  `numberOfAlbums`.

## [1.3.0] - 2026-07-22

### Added
- **Gapless playback via shared ExoPlayer (GP1–GP3)** — replaced per-song
  `MediaPlayer` with a single shared Media3 `ExoPlayer` instance in
  `Radio.kt`. `RadioPlayer` becomes a thin delegate; ExoPlayer's internal
  playlist handles track transitions with zero gap. `RadioForwardingPlayer`
  wraps the real ExoPlayer for Android Auto controls. Two hotfixes:
  main-thread dispatch for all ExoPlayer APIs (`runOnMain` pattern with
  `@Volatile` cached fields) and reduced `DefaultLoadControl` buffers
  (min 5s / max 15s) to prevent OOM on constrained AVDs.
- **Auto-resume on launch** — new `autoResumeOnLaunch` setting (default: off)
  in Player settings. When enabled, the persisted queue auto-starts playback
  on app launch via `Radio.restorePreviousQueue()`.
- **Now Playing a11y + metadata (UI3)** — real `contentDescription` on all 6
  transport controls for TalkBack. M3 Slider replaces hand-rolled 12dp seek
  bar (48dp touch target, proper ProgressBarRangeInfo semantics). Muxer tag
  filtering (Lavf/LAME/iTunes/Nero/Xiph/FLAC/Lavc/Helix) in
  `toSamplingInfoString`. `showLyrics` moved from global mutable
  `NowPlayingDefaults` to `Settings.BooleanEntry` for persistence.
- **Home grid polish (UI4)** — tightened card gap from ~24dp to ~16dp via
  `ResponsiveGrid` contentPadding(8.dp) + Arrangement.spacedBy(8.dp) with
  reduced inner tile padding (12→4dp). Per-tile play FAB shrunk to 28dp chip
  (48dp touch target).
- **README + screenshots (PR1)** — updated with current UI screenshots.
- **detekt config wiring (PR3)** — wired project-level `detekt.yml` to all
  modules via `subprojects { plugins.withType<DetektPlugin> }`. Regenerated
  per-module baselines.
- Real Material You tonal palette (UI2) — replaced the flat single-accent
  color system with secondary/tertiary derived from the primary via HSL
  hue-shift + desaturation. `onPrimary` is luminance-aware so light accents
  render dark text. Added Medium/SemiBold font weights to Inter, Poppins,
  DM Sans, and Roboto.

### Fixed
- **Now Playing artwork regression** — UI3.4 replaced fixed-height spacers
  with `Spacer(Modifier.weight(1f))` in `NowPlayingBodyContent`. Because
  BodyContent is called inside an unweighted sibling Column in Body.kt,
  the weight spacers consumed the entire parent height, collapsing the cover
  Box to 0px. Reverted to fixed heights (28dp + 20dp). Commit c013c49.
- **Search + queue state (UI1)** — SearchView now debounces via snapshotFlow
  + collectLatest with 250ms delay. Results use LazyColumn for
  virtualization. QueueView selection is id-based so queue mutations no
  longer desync checked state. RadioQueue gained removeByIds helper.
- **Native parser hardening (CR1)** — AudioMetadataParser.cpp now releases
  JNI local references, guards every callback with ExceptionCheck, and
  parses year-only / year-month DATE tags. ProGuard keep rule preserves
  JNI callback names so release builds no longer SIGABRT on first scan.
- **Radio queue / storage correctness (CR2)** —
  SQLiteKeyValueDatabaseAdapter.put uses CONFLICT_REPLACE, RadioQueue.remove
  no longer hits index-deflection bug, shuffled remove deletes from
  originalQueue, Radio.play stale-id path has recursion guard.
- **ExoPlayer memory + AVAILABLE_COMMANDS builder (GP3 hotfix)** — reduced
  DefaultLoadControl buffers (min 5s / max 15s) prevent OOM on AVD 192MB
  heap. Replaced deprecated Commands.Builder.add() loop with single
  addAll(vararg) to avoid repeated FlagSet.Builder allocations.

### Changed
- **Agent documentation consolidation** — moved local-only working docs from
  docs/ into specs/ and added root pointer files to .gitignore.
- **Product Sans font removed (PR2)** — deleted proprietary
  productsans_regular.ttf and productsans_bold.ttf. KordXBuiltinFonts and
  KordXTypography now reference only open-source fonts (Inter, Roboto,
  Poppins, DM Sans).
## [1.2.0] - 2026-07-20

### Added
- **Android Auto (Media3) playback (CR3)** — `KordXMediaLibraryService`
  (androidx.media3.session.MediaLibraryService) replaces the legacy
  `MediaBrowserServiceCompat` service. Adds a browse tree, a Now Playing
  card with the radio queue + custom actions, voice/assisted search via
  `MEDIA_PLAY_FROM_SEARCH`, a recently-played root, and root-level
  SHUFFLE_ALL + SEARCH. minSdk raised to 31 for the Media3
  `setCustomLayout`/`CommandButton` API.
- **Home grid polish (UI4)** — tightened the inter-tile gap on the Home
  grids (Albums / Playlists / Artists / Genres) from ~24 dp to ~16 dp via
  `ResponsiveGrid` `contentPadding` + `Arrangement.spacedBy`, and shrank the
  per-tile play FAB to a 28 dp surface chip while keeping the 48 dp Material
  touch target. Documented the `grid-gap` (8 dp) spacing token.

### Fixed
- **Native metadata parser hardening (CR1)** — `metaphony`
  `AudioMetadataParser.cpp` now releases JNI local references, guards every
  callback with `ExceptionCheck`, and parses year-only / year-month `DATE`
  tags. A ProGuard keep rule preserves the three JNI callback names so
  release (R8) builds no longer `SIGABRT` on the first scan.
- **Radio queue / storage correctness (CR2)** —
  `SQLiteKeyValueDatabaseAdapter.put` uses `CONFLICT_REPLACE`,
  `RadioQueue.remove` no longer hits the index-deflection crash, and
  `Radio.kt` guards stale-id recursion.

## [1.1.3] - 2026-07-15

### Fixed
- **App crashes (minimises and won't reopen) immediately after adding a
  media folder, release builds only** — a third R8/ProGuard bug, this time
  in the `metaphony` native metadata module. `AudioMetadataParser.cpp` calls
  back into Java/Kotlin via JNI using literal method names
  (`FindClass("...AudioMetadataParser")` + `GetMethodID(..., "putTag", ...)`,
  `"putPicture"`, `"putAudioProperty"`). R8 in non-full mode does NOT follow
  these call sites, so it obfuscated the three callback method names. At
  runtime `JNI_OnLoad()` resolves the renamed methods to null, and the first
  native scan call raises `NoSuchMethodError`. Because the error originates
  in native code it escapes the Kotlin `try/catch (Exception)` around the
  scan in `MediaExposer.fetch()`, aborts the runtime (SIGABRT), and kills
  the process — the user sees the app "minimise and not reopen" the instant
  they finish adding a folder.
  - The fix adds `-keep class
    com.android.rockages.kordx.metaphony.AudioMetadataParser { *; }` to
    `metaphony/consumer-rules.pro` (auto-merged into the app's R8 config),
    preserving the class FQN and the three JNI callback method names. The
    private external `readMetadataNative` is auto-kept by R8 already.
  - Reproduced on an AVD running the release APK: adding `/sdcard/Music` and
    tapping Done produced `Fatal signal 6 (SIGABRT) ... (DefaultDispatch)`
    with `NoSuchMethodError: no non-static method
    "...AudioMetadataParser.putTag(Ljava/lang/String;Ljava/lang/String;)V"`.
    The debug APK never showed it because debug builds skip R8.
  - Companion regression test `MetaphonyProguardRulesTest` (5 tests) pins the
    rule and the three callback names so the JNI contract can't silently
    break again.

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
  documented in [`specs/RELEASING.md`](specs/RELEASING.md).
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
