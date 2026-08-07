# Changelog

All notable changes to KordX are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
