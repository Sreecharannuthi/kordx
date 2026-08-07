# Changelog

All notable changes to KordX are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.8.0] - 2026-08-07

### Changed
- Updated the first-launch welcome dialog with useful media-folder guidance and a close button.
- Changed the default Home tabs to For You, Songs, Albums, and Playlists; Artists remains available as an optional tab.
- Set the default For You section order to Suggested songs, Suggested albums, and Suggested artists.

## [1.7.6] - 2026-08-07

### Fixed
- **Notification progress was suppressed by every song being reported as live.** `MediaItem.Builder` gives items a default live configuration, and `RadioTimeline` was copying it into each static song window. Media3 therefore published `PlaybackState.position=-1` and `speed=0`; timeline windows now explicitly clear `liveConfiguration`, command availability is initialized before session listeners attach, and the first real position triggers a full timeline snapshot. Verified on the Phone AVD with hardware GPU acceleration.

## [1.7.5] - 2026-08-07

### Fixed
- **CI and release SDK setup now follows the Gradle compile SDK.** The workflows resolve the compile SDK from `gradle/libs.versions.toml` before installing the matching Android platform and Build Tools, preventing hard-coded SDK drift.
- **Media-center progress refresh remains enabled.** The Media3 periodic position-update path and OEM notification refresh regression coverage remain part of this release.

## [1.7.4] - 2026-08-07

### Fixed
- **Release workflow SDK alignment.** CI and release validation now install Android SDK 36 and Build Tools 36, matching the project's compile SDK and Media3 1.10.1 requirements.
- **Media-center progress refresh.** Retained Media3 periodic position updates and the OEM notification refresh path so elapsed playback time stays current across platform media controls.

## [1.7.3] - 2026-08-07

### Fixed
- **Media-center progress snapshots could remain stale on OEM media controls.**
  The Media3 session now enables its supported periodic position-update path,
  while retaining the adapter's throttled typed refresh for notification surfaces
  that do not extrapolate elapsed time.

## [1.7.2] - 2026-08-05

### Fixed
- **Notification media-player elapsed time remained at `00:00` while the lock-screen
  progress worked.** Some OEM notification cards do not extrapolate the platform
  playback position from its timestamp and speed. `RadioForwardingPlayer` now sends
  a typed platform playback-state refresh once per elapsed second while playing.
  The refresh is throttled from KordX's existing position stream and deliberately
  omits aggregate player events, so Media3 does not rebuild the notification or
  disturb artwork and transport controls on every update.

## [1.7.1] - 2026-08-05

### Fixed
- **Notification progress bar had no usable range and remained at the start.** The
  Media3 migration did not publish each song's duration through canonical
  `MediaMetadata.durationMs` before decoder preparation, so Android's platform
  media controls received an unknown duration. Playable song items now carry the
  library duration, `RadioForwardingPlayer` uses it until the decoder reports a
  live duration, and timeline windows and periods expose the same finite range.
  Android can extrapolate the moving position from the published playback state
  without synthetic 100 ms position-discontinuity events.

## [1.7.0] - 2026-08-04

### Fixed
- **Notification Next / Previous did not change tracks.** `RadioForwardingPlayer`
  ignored `mediaItemIndex` and treated every `seekTo()` call as a position seek, so
  the notification's track-skip controls stayed on the current song. The adapter
  now routes `mediaItemIndex == currentIndex + 1` to `radio.shorty.skip()` and
  `mediaItemIndex == currentIndex - 1` to `radio.shorty.previous()`. Same-index
  calls still seek within the current song.
- **Notification progress bar was frozen at 00:00 while audio was playing.** Media3's
  notification manager refreshes on playback-state events, not on position
  discontinuities alone. Position ticks now also emit
  `EVENT_PLAYBACK_STATE_CHANGED` so the live position and chronometer render
  in the notification.
- **Audio continued in the background after dismissing KordX from Recents.** The
  Media3 foreground service and shared `Radio` engine kept playing while the
  activity task was gone. `KordXMediaLibraryService.onTaskRemoved()` now
  stops playback, clears the queue, releases audio focus and the wake lock,
  removes the notification, and stops the service. Auto-resume on launch
  remains enabled for the next app launch.
- **Auto-resume on launch restarted from the beginning of the saved song.** A
  `Queue.Modified` event was dispatched after the staged player was prepared at
  the persisted position, so `syncPlayerPlaylist()` observed the temporary
  `0` position and overwrote the restore. `RadioQueue.afterAdd()` now
  dispatches the queue update before staging the new player.
- **Detekt CI failure (metaphony-instrumented-test).** The pinned SHA for
  `reactivecircus/android-emulator-runner` was removed from upstream, so the
  job could not resolve the action. Updated to the current v2.38.0 commit
  SHA.

### Added
- **Android Auto safe artwork URIs.** `ArtworkContentProvider` exposes cached
  cover art through validated `content://` URIs (Android for Cars requires
  `content://` or `android.resource://`; `file://` URIs into app-private
  storage are not visible to the car surface).
- **Bounded browse and search pagination.** `onGetChildren()` and
  `onGetSearchResult()` now respect `page` and `pageSize` (default 50, max
  100). Unexpected exceptions return `SessionError.ERROR_UNKNOWN` instead of
  a successful empty list.
- **Service-owned voice search.** A dedicated `MediaSearchActivity` handles
  `MEDIA_PLAY_FROM_SEARCH` without launching the phone Compose UI and
  retries briefly while the application graph is still initializing.
- **Typed Media3 callbacks for shuffle, repeat, and playback parameters.**
  These were previously delivered only through the aggregate `onEvents`
  callback, so the Media3 session could show stale state for those controls.

## [1.6.9] - 2026-08-04

### Fixed
- **Notification / system media player state synchronization.** The Media3 bridge now
  forwards typed player callbacks (`onPlaybackStateChanged`, `onIsPlayingChanged`,
  `onPlayWhenReadyChanged`, metadata, timeline, and position callbacks) in addition to
  aggregate events. This keeps the Media3 session and notification synchronized with
  the app's actual playback state.

## [1.6.8] - 2026-08-04

### Fixed
- **Empty "Auto-resume on launch" Player settings label.** The `AutoResumePlayback`
  i18n key existed in `Translation.g.kt` but was dropped from the TOML sources and
  bundled JSON assets during the i18n migration. Restored `"Auto-resume on launch"`
  across all 18 locales + a regression test.
- **Notification / system media player out of sync (play ▶ + 00:00 while audio
  plays).** The `RadioForwardingPlayer` never dispatched continuous position or
  metadata events, and newly-attached Media3 listeners saw a stale state. It now
  subscribes to `Radio.onPlaybackPositionUpdate` (dispatching
  `EVENT_POSITION_DISCONTINUITY` per tick), sends an initial sync event to new
  listeners, and adds `EVENT_MEDIA_METADATA_CHANGED` to queue events. See DS1/DS3.

### Changed
- **Suggested-songs tile unified with albums/artists.** `SongTile` now delegates to
  `SquareGrooveTile` (same transparent card, padding, layout) and a shared
  `PlayButtonOverlay` (white play arrow on a translucent black circular scrim)
  keeps the play icon visible on light artwork. See DS2.

## [1.6.7] - 2026-08-04

### Removed
- **AAOS support (Android Automotive OS).** User decision: Android Auto
  projection is the only car target — no AAOS/TV placeholder. Deleted the
  cross-process `ArtworkContentProvider` + `ArtworkUriProvider` (only needed
  because AAOS runs a separate UI process), the `.Launcher` activity alias +
  `KordXApplication.reconcileLauncherAlias()`, the
  `androidx.car.app.launchable` meta-data, the AAOS `uses-feature` hardware
  opt-outs, and the `specs/AAOS/` + `AAOS_HOME_MEDIA_LATEST.md` docs.
  `MainActivity` regained a direct `MAIN`/`LAUNCHER` filter (one phone
  launcher icon). Android Auto metadata (`com.google.android.gms.car.*`,
  `TintableAttributionIcon`, `KordXCarTheme`) is retained — it serves the
  Android Auto projection UI.
- **Cross-process artwork provider.** `SongRepository.getArtworkUri()`
  reverts to the in-process cached-cover URI (`artworkCache`), which Android
  Auto resolves natively; `Media3ItemFactory` no longer carries `artworkData`
  bytes.

### Added
- **AU1 — Android Auto artwork delivery.** Media3 song metadata carries
  album artwork via the cached-cover URI (`SongRepository.getArtworkUri()`),
  which Android Auto's projection UI can resolve. The in-process artwork
  cache path is used so no cross-process provider is required.
- **i18n `{x}` placeholder fix.** Added `String.substitutePlaceholders()` in
  `CommonTranslation.kt`; generated `Translation.g.kt` helpers now use it instead
  of Kotlin `String.format()`, which never replaced `{x}` / `{y}` placeholders.
  The formatter binds by placeholder name (`{x}` → first arg, `{y}` → second)
  so locales that reorder placeholders (Hindi/Telugu/Japanese) render correctly.
  Unit tests guard substitution, reordering, and the leave-unknown-placeholders
  failure mode.
- **RV14 — Test-shape deepening.** Added JVM unit tests for the previously
  untested `:core` and `:infra` modules: `RoomConvertorsTest` pins URI /
  string-set / string-list / LocalDate serialization round-trips, and
  `CacheDatabaseTest` exercises the real Room `RecentPlaysStore` DAO through
  an in-memory database + Robolectric. Added `SettingsTest` as a state-holder
  (ViewModel substitute) test that verifies boolean, enum, nullable-string,
  and string-set entry persistence via SharedPreferences. Added a metaphony
  androidTest skeleton (`AudioMetadataParserInstrumentedTest`) that parses
  real audio assets through the native taglib bridge, and wired it into CI via
  a new `metaphony-instrumented-test` emulator job.
- **LG4 — Dynamic receiver audit.** Added `DynamicReceiverAuditTest`, a
  source-level scan that asserts every `Context.registerReceiver` call in
  `:app` passes an explicit `RECEIVER_EXPORTED` / `RECEIVER_NOT_EXPORTED` flag,
  is guarded by `BuildConfig.DEBUG`, or lives in a legacy suppression branch.
- **AX2 — Artist merge / alias map.** Added a persisted artist merge map
  (`Settings.artistMergeMap`) that lets users merge duplicate artist profiles.
  The artist detail screen now has a "Merge with…" overflow action; merged
  source names appear as chips under the artist header and can be unmerged.
  Mappings survive rescans and are applied during `ArtistRepository.onSong()`.
  Immediate in-cache migration keeps the UI consistent right after a merge.
  The merge dialog auto-suggests prefix/suffix matches such as "Tanvi" →
  "Tanvi Shah". The default artist-tag separator list now includes `&`,
  `feat.`, `featuring`, `ft.`, ` x `, and ` X ` so compound tags like
  "Krish & Shruthi Hassan" are split into individual artist profiles.
- **AX2b — Album-artist merge / alias map.** Mirrored the artist merge feature
  for album-artist profiles (`Settings.albumArtistMergeMap`). Album-artist
  detail screens and tile overflow menus now offer "Merge with…", show merged
  source chips, and persist mappings across rescans. Album-artist tags are
  split by the same expanded separator set as artist tags.
- **PB11 — Auto-pause / auto-resume for short-form video.** Added
  `pauseOnAudioFocusDuck` player setting (default on). When a short-form video
  app (Reels / Shorts / TikTok) requests transient-may-duck audio focus,
  KordX now pauses instead of ducking to 20% volume and resumes playback when
  the focus returns. The behavior respects the existing `ignoreAudioFocusLoss`
  setting.
- New i18n keys across all 18 locales: `PauseOnAudioFocusDuck`, `MergeWith`,
  `MergeArtists`, `MergeArtistsConfirmation`, `MergedArtists`,
  `SuggestedMerges`.


## [1.6.6] - 2026-08-03

### Fixed
- **Phone notification migrated to Media3 (`DefaultMediaNotificationProvider`) — LG1.**
  Replaced the hand-rolled `RadioNotification` / `RadioNotificationManager` /
  `RadioNotificationService` stack with Media3's `DefaultMediaNotificationProvider`
  (stable notification id `1001`, channel `KordX_media_notification`). This fixes
  the duplicate-notification bug and the `ForegroundServiceDidNotStartInTimeException`
  that could kill the service ~30–40 s after playback started. Also bumps Media3
  1.7.1 → 1.10.1 (fixes artwork downscaling blur #3134 and an FGS race #3270);
  required raising `compileSdk` to 36, AGP to 8.10.0, and the Gradle wrapper to
  8.11.1.
- **Legacy `MediaSessionCompat` deactivated — LG2.** `RadioSession` no longer
  publishes metadata, playback state, or the queue through a second legacy session;
  the single Media3 `MediaLibrarySession` in `KordXMediaLibraryService` is the only
  active session. `dumpsys media_session` now shows exactly one KordX session with
  `state=PLAYING(3)`. Deleted `RadioArtworkCacher.kt` and the legacy `handleAction`
  / `ACTION_*` playback-receiver path.

### Changed
- **Media3 notification lifecycle hardening.** Two runtime findings from on-device
  verification: (1) `RadioForwardingPlayer.getPlaybackState()` now returns
  `STATE_READY` whenever a song is staged (`queue.currentSongId != null`) instead
  of deriving it from `hasPlayer`, which was false during `RadioPlayer` preparing
  and caused Media3 to suppress the real notification. (2)
  `KordXMediaLibraryService.onCreate` now explicitly calls `addSession(session)`
  because `MediaLibrarySession.Builder` does not auto-register with the service;
  `onStartCommand` posts a placeholder only when Media3's real notification is not
  already active, preventing the placeholder from clobbering the real one.
- **Dead-code and doc cleanup — LG3/LG7.** Removed all stale references to the
  deleted `KordXMediaBrowserService` from KDoc, comments, `AndroidManifest.xml`,
  and tests; verified `UpdateRequest` and the legacy `ACTION_*` constants are gone;
  debug receivers remain `BuildConfig.DEBUG`-gated. `libs.media` was evaluated for
  removal but kept because `RadioFocus.kt` still uses `androidx.media` AudioFocus
  compat APIs.
- **`MediaMetadataRetriever` hardening — LG6.** The retriever is already released
  in `finally`; added defensive bounds that skip retriever parsing for files
  > 500 MiB and discard embedded pictures > 20 MiB to avoid scanner OOM on
  outliers. Extracted `saveCoverFile()`, `extractSamplingRate()`, and
  `fallbackSong()` helpers.

### Removed
- `RadioNotification.kt`, `RadioNotificationManager.kt`, `RadioNotificationService.kt`
  and their manifest service entry.

## [1.6.5] - 2026-08-02

### Added
- **Suggested songs home section (SS1–SS4)** — a new opt-in For You section
  that surfaces six random songs in a grid (`SongTile` with artwork, title,
  and artist), excluding the five most recently added songs. Tapping a tile
  plays the section as a queue. Off by default; enable in Settings → Home →
  For you. Sections render in the persisted user order.
- **Persistent ordered Home settings (SS1 follow-up)** — Home tabs and For
  You sections are now stored as ordered lists (`EnumListEntry`) instead of
  unordered sets. The settings dialog's up/down arrows now actually reorder
  the selection, the Done button enables when the order changes, and the
  order survives restarts. The Home bottom-sheet tab grid also follows the
  stored order.
- **Bounded artwork cache (RV10)** — the artwork cache now evicts oldest files
  when the total size exceeds 100 MB, addressing the oldest open user bug
  (#6). Eviction runs automatically after each library scan's orphan cleanup.

### Fixed
- **i18n loader Verifier crash** — adding the 255th translation key pushed the
  generated `_Keys` data class past the JVM method-descriptor limit for its
  synthetic `copy$default`, crashing the app at startup with a `VerifyError`.
  `_Keys` is now a map-backed class and the loader builds a `Map` from JSON,
  removing the limit entirely.

## [Unreleased]

## [1.6.4] - 2026-08-02

### Fixed
- **Groove coroutine scope lifecycle (RV15)** — `Groove.coroutineScope` now uses
  `SupervisorJob() + CoroutineExceptionHandler` and is cancelled in
  `onKordXDestroy()`, so a failing child coroutine no longer cancels the entire
  app scope.
- **`Radio.play()` recursion (RV16)** — stale-id recovery now iterates instead of
  recursing, eliminating a main-thread stack-overflow vector on pathological
  restored queues.
- **Settings tap feel (TX1–TX4)** — dialog tiles debounce rapid toggles,
  `ScaffoldDialog` ignores scrim taps within 250 ms of opening, settings cards
  now use a visible surface so ripples show, and switch tiles deduplicate
  Card/Switch taps to avoid double toggles.

### Changed
- **CI hardening (RV13)** — all GitHub Actions are pinned to commit SHAs,
  `distributionSha256Sum` is set for the Gradle wrapper, and the release
  workflow now runs unit tests + lintDebug + detekt before building signed APKs.
- **Repo admin (RV12)** — closed stale PR #7; its docs-dependent test removal
  was cherry-picked onto main.
- **AAOS doc path fix** — `AndroidManifest.xml` no longer points to the
  removed `specs/architecture/AAOS_HOME_MEDIA_LATEST.md`; the AAOS
  investigation was retired when AAOS support was dropped (Android Auto
  projection remains the only car target).

## [1.6.3] - 2026-08-02

### Changed
- **Comment cleanup (RV5)** — fixed 289 lines of mangled comments across 18
  files. Multi-line comments from the initial commit had their line breaks
  collapsed into `; ` (semicolon + space), producing unreadable run-on lines.
  Line breaks are restored, word-glue typos fixed (`theadio` → "the radio",
  `perentity` → "per-entity", `persong` → "per-song", `mainthread` →
  "main-thread", etc.), the commented-out `toSamplingInfoString` predecessor
  removed, and the single-line run-on KDoc in `RadioAdapterTarget` reformatted.
  No behavior change — 318 tests remain green.

## [1.6.2] - 2026-08-02

### Fixed
- **Media notification crash on first playback (FGS race)** — after a cold
  start or fresh install, rapid `Player.Started` events during auto-resume
  could trigger `RadioNotificationManager.createService()` →
  `startForegroundService()` while the service's `startForeground()` call was
  still pending. If the service START event arrived before a notification
  was available, `startForeground()` was never called and the system killed
  the app with `ForegroundServiceDidNotStartInTimeException`. The manager
  now stores the pending notification *before* requesting the service and
  `onServiceStart()` unconditionally calls `startForeground()` (with a
  minimal placeholder fallback).
- **Playback receiver exported flag** — the headphone/audio-becoming-noisy
  receiver now uses the explicit `RECEIVER_EXPORTED` flag on API 33+ (RV7).
- **Process-wide crash handler** — a main-thread crash now kills the process
  so the next launch starts clean instead of lingering as a zombie (RV6).
- **Wake lock scope** — the wake lock is now held for the full playback
  duration instead of a fixed 10-minute timeout (RV9).

### Changed
- **Eventer thread safety** — subscriber iteration now uses a
  `CopyOnWriteArrayList`, so adding/removing subscribers mid-dispatch no
  longer races (RV8).
- **detekt baseline diet** — the generated `Translation.g.kt` is excluded
  via rule config, dropping the baseline from 780 → 274 IDs (RV4).

### Added
- **Error screen redesign** — the fatal-error screen now uses M3 dynamic
  color with Home / Report / Details recovery actions (ES1).

## [1.6.1] - 2026-08-01

### Added
- **CompactCenter Now Playing controls layout** — play/pause sits at the
  exact horizontal center (⏮ ⏪ ⏯ ⏩ ⏭), reachable from both sides; it is
  now the default layout (applies automatically unless you explicitly
  picked a layout before).
- **Android Auto README section** with a refreshed car screenshot,
  plus refreshed phone screenshots.

### Fixed
- **White screen after a main-thread crash** — `ErrorActivity` is created
  on the main Looper, so when the *main thread* crashed it could never
  appear: the process lingered as a headless zombie (audio playing, white
  window, ANR on next input). The crash handler now detects main-thread
  crashes and kills the process so the failure is loud and the next
  launch starts clean.
- **White screen from an over-popped back stack** — a rapid double
  system-back could race predictive-back and pop past the start
  destination, leaving an empty back stack and a 0×0 ComposeView.
  `compose-navigation` bumped 2.8.4 → 2.8.9 (upstream fix) plus a
  self-heal watchdog that restores Home if the stack ever ends up empty.
- **Play button shown while audio plays after auto-resume** —
  `RadioPlayer._isPlaying` was written inside `runOnMain {}`, but the
  auto-resume coroutine dispatched `Player.Started/Resumed` before the
  posted write landed. The volatile flag is now set synchronously on the
  calling thread.
- **Seek bar accessibility (TalkBack)** — the hand-rolled `pointerInput`
  seek bar was invisible to the accessibility tree and had a 12dp touch
  target. Replaced with a Material3 `Slider` (custom thin thumb/track
  keep the original look): TalkBack announces position and can adjust it,
  and the touch target meets 48dp.
- Removed unused `LinkTile`, core `Http`, and `FileDatabaseAdapter`.

## [1.6.0] - 2026-07-26

### Added
- **Android Auto discovery** — `KordXMediaLibraryService` declares the
  legacy `android.media.browse.MediaBrowserService` intent-filter action, the
  `android.intent.action.MEDIA_BUTTON` action, and `android:appCategory="audio"`
  so Android Auto can discover and surface KordX as a media source.
- **Android Auto session activity** — `MediaLibrarySession.Builder` is
  now configured with `setSessionActivity()`, so tapping the Now Playing card
  or media notification opens KordX `MainActivity`.
- **Content style hints** — every browsable and playable `MediaItem` produced
  by `Media3ItemFactory` now carries `MediaConstants.EXTRAS_KEY_CONTENT_STYLE_*`
  hints (browsable folders default to grid, playable songs default to list,
  tabs and drill-downs override as appropriate).
- **Attribution icon + car theme** — added
  `androidx.car.app.TintableAttributionIcon` and
  `com.google.android.gms.car.application.theme` meta-data to the manifest,
  plus a new `KordXCarTheme` with a gold accent for Android Auto
  playback controls.
- **New brand icon** — launcher foregrounds, monochrome adaptive icon, and
  placeholder artwork now use the `icon.svg` design: a blue (#4285F4) vertical
  bar + a gold (#FBBC05) play triangle on a dark rounded background.

### Changed
- **Default accent color** — the native theme and Compose `ThemeColors.Blue`
  now use `#4285F4` (matched to the new icon), and Blue is the default primary
  color. Active-state icons (shuffle, repeat) switched from cyan to the new
  brand blue.
- **Placeholder artwork** — `placeholder_dark.png` and `placeholder_light.png`
  now render the `icon.svg` logo instead of the legacy cyan "K" mark.

### Removed
- **Legacy logo assets** — deleted the unused `darklogo.svg` and
  `lightlogo.svg` files from `app/src/main/assets/`.

## [1.5.0] - 2026-07-25

### Fixed
- **Auto-resume position race** — `Radio.autoResumeIfEnabled()` no longer
  re-calls `play()` after losing the restore `Mutex` race (which destroyed the
  already-staged player and restarted the song from 0). It now resumes the
  player that `RadioQueue.restore()` already staged at the persisted position.
- **Focus-gain auto-resume after manual pause** — `Radio.pause()`,
  `pauseInstant()`, and `stop()` cancel the pending focus-gain resume via
  `RadioFocus.cancelPendingFocusResume()`; only focus-loss pauses re-arm it.
- **Stale muted volume on start** — `Radio.start()` explicitly resets the
  shared `ExoPlayer` volume to max when fade-in is disabled, guarding against
  an interrupted fade-out leaving the player muted.
- **Startup ANR after GP4** — `onMediaItemTransition` dispatched
  `Events.Queue.Modified`, which `watchQueueUpdates` answered with a playlist
  re-sync (`setMediaItems`), re-firing the same listener in an infinite
  main-thread loop. The transition listener now dispatches only
  `Queue.IndexChanged`, and the playlist sync runs under the
  `isApplyingQueueChange` guard. (Regression test:
  `RadioMediaItemTransitionTest`.)

### Changed
- **Media3-idiomatic resume position** — the persisted start position is now
  applied via `exoPlayer.setMediaItem(item, startPositionMs)` instead of a
  post-prepare `seekTo`; a restore no longer dispatches a seek event.
- **True gapless playback (GP4)** — replaced the per-song `RadioPlayer`
  stop-and-start lifecycle with a single shared `ExoPlayer` playlist built via
  `setMediaItems(...)`. Loop modes map to ExoPlayer repeat modes, and manual
  next/previous use `seekToNextMediaItem()` / `seekToPreviousMediaItem()`.
- **Focus denial is now logged** — `Radio.start()` warns when
  `requireAudioFocus` blocks playback start (previously silent).
- **Fader lifecycle debug logging** — `RadioEffects.Fader` logs start/stop at
  debug level (`Logger.debug`).

### Removed
- **`gaplessPlayback` toggle (PB7)** — the setting was dead code (no playback
  code read it). Removed the `Settings` entry, the Player settings switch, and
  the `GaplessPlayback` i18n key from the TOML sources, the 18 locale JSON
  assets, and the generated `Translation.g.kt`. True gapless playback shipped
  as GP4 (see Changed above).

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
- A `MediaSearchActivity` alias forwarding path so the Android Auto
  voice-search button reaches the existing `RadioSession.handlePlayFromSearch` route
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
