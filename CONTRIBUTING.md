# Contributing to KordX

Thanks for your interest in improving KordX! This document explains how to get
set up and how changes are reviewed.

## Development setup

1. Install the toolchain from [BUILDING.md](BUILDING.md):
   JDK 17, Android SDK 35, NDK r27, CMake 3.22.1, and Node.js 22.
2. Clone and open the project in Android Studio (or your editor of choice):

   ```bash
   git clone https://github.com/Sreecharannuthi/kordx.git
   cd kordx
   ./gradlew :app:assembleDebug
   ```

3. (Optional) regenerate i18n after editing translation sources:

   ```bash
   npm install
   npm run i18n:build
   ```

## Code style

- **Kotlin:** the [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
  (`kotlin.code.style=official` is set in `gradle.properties`).
- Keep modules separated by responsibility: UI/logic in `app`, pure utilities
  and models in `core`, persistence in `infra`, and native metadata in
  `metaphony`.
- Run the checks before opening a PR:

  ```bash
  ./gradlew :app:lintDebug detekt :app:testDebugUnitTest :metaphony:testDebugUnitTest
  ```

- New code should come with tests where it makes sense.

## Internationalization

Translations live as TOML sources under `i18n/`. They are compiled into the
JSON assets the app loads at runtime (`app/src/main/assets/i18n/`), which are
committed to the repository, so a normal build needs no extra step. To update
translations, edit the TOML sources and regenerate the bundled assets, then
commit the results. Do not hand-edit the generated JSON.

## Pull requests

1. Fork the repo and create a topic branch from `main`.
2. Keep commits focused and write clear messages.
3. Make sure `lint`, `detekt`, and the unit tests pass.
4. Open a PR against `main` with a short description of the change and the
   motivation behind it.

## Reporting issues

When filing a bug, please include:

- KordX version (Settings → About) and Android version / device.
- Steps to reproduce, expected behavior, and actual behavior.
- Any relevant logs (capture with `adb logcat` if possible).

For feature requests, open an issue describing the use case and why it would
help.

## License

By contributing, you agree that your contributions will be licensed under the
project's AGPL-3.0 license.
