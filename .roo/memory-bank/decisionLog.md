# Decision Log — On Time

Chronological log of technical decisions and why they were made. Append new entries
at the bottom; do not rewrite history. See also the detailed rationale in
[`architecture/decisions.md`](../../architecture/decisions.md) if present, and the
component design in [`architecture/architecture.md`](../../architecture/architecture.md).

## 2026-07-31 — Toolchain: CLI-only, no Android Studio GUI

- **Decision:** Build/run entirely via Gradle wrapper + adb from the command line.
  No Android Studio installation required.
- **Why:** Developer works across multiple machines (Linux and Windows, depending on
  location) and wants a portable setup. Android Studio's first-run wizard is GUI-only
  and not easily automated/reproduced across machines.
- **How:** `scripts/setup-dev-env.sh` (Linux/macOS) and `scripts/setup-dev-env.ps1`
  (Windows) download a self-contained JDK 17 + Android SDK cmdline-tools into
  `.tools/` (gitignored, local to each checkout) and bootstrap the Gradle wrapper.
  Re-run the script on any new machine/checkout; it's idempotent.
- **Gotcha:** Gradle 8.7's `wrapper` task refuses to run in a directory with no
  `settings.gradle(.kts)` present ("Directory does not contain a Gradle build"). The
  project skeleton (settings.gradle.kts, root/app build.gradle.kts, manifest, minimal
  source/res) must exist *before* bootstrapping the wrapper.

## 2026-07-31 — Package name / namespace

- **Decision:** `com.kappacs.ontime` (both `namespace` and `applicationId` in
  `app/build.gradle.kts`), reversed from the developer's `kappacs.com` domain.

## 2026-07-31 — SDK levels

- **Decision:** `minSdk = 26` (Android 8.0 Oreo), `targetSdk = 34`, `compileSdk = 34`.
- **Why:** API 26 is the first version with proper notification channels and modern
  foreground-service behavior, both required by this app (persistent playback
  notification, alarm full-screen intent). Target device (Pixel 8 Pro) runs a far
  newer Android version (Android 17), so this only affects the lower bound.

## 2026-07-31 — Build versions

- Gradle 8.7, Android Gradle Plugin (AGP) 8.5.2, Kotlin 1.9.24, JDK 17 (Temurin).
  These must stay mutually compatible — check the AGP/Gradle compatibility table
  before bumping any one of them independently.

## 2026-07-31 — Device connectivity

- **Decision:** Physical Pixel 8 Pro over adb (USB or wireless debugging), not an
  emulator, since the developer wants to test on real hardware across locations.

## 2026-07-31 — Memory bank location

- **Decision:** Cross-session memory bank lives in `.roo/memory-bank/` (this
  directory), following the standard Roo convention. Requirements and detailed
  architecture/design docs live under `/architecture` per explicit project
  preference (not `/plans` or other locations).

## 2026-07-31 — Dev topology: separate dev server + local machine, adb over SSH tunnel

- **Context:** The dev server (workspace host, where I run build commands) is a
  persistent remote box reached via SSH from three different local machines: two
  desktops (Linux, Windows) on the same Wi-Fi as the dev server, and an off-site
  laptop (Linux) on an unrelated network. The Pixel 8 Pro is physically attached to
  whichever local machine is in use, never to the dev server itself.
- **Decision:** Use SSH reverse port-forwarding (`RemoteForward 5037
  127.0.0.1:5037`) to tunnel the adb client/server protocol from the local
  machine's real adb server, through the existing SSH connection, to the dev
  server. Gradle builds run on the dev server; `adb`/`./gradlew installDebug`
  commands issued there transparently reach the real device via the tunnel —
  works identically regardless of local network (same-LAN or fully off-site),
  since only SSH connectivity is required.
- **Why not wireless debugging / shared Wi-Fi:** would only work for the two
  desktops on the dev server's LAN, not the off-site laptop. The SSH tunnel is a
  single uniform solution for all three machines.
- **How:** `scripts/setup-adb-only.sh` / `.ps1` install *just* platform-tools
  (adb) on each local machine (no JDK/SDK/Gradle needed there). Full workflow
  and SSH config snippet documented in
  `architecture/remote-adb-workflow.md`.
- **Deferred:** Android emulator on the dev server for headless automated
  instrumented tests — evaluate later (Phase 11), not needed for manual
  real-device testing since the tunnel already provides that from anywhere.

## 2026-08-01 — Phase 2 & 3: Bound Service with StateFlow & System Fallbacks

- **Decision:** Use a bound service pattern with a Kotlin `StateFlow` to communicate service state (playback status, remaining time, alarm ringing) to `MainActivity`.
- **Why:** Binding is a robust, process-local way to communicate between an Activity and a Foreground Service. It avoids the overhead and security concerns of local broadcasts, and `StateFlow` provides a clean, reactive, lifecycle-aware way to observe state changes.
- **Decision:** Use standard system default URIs (`Settings.System.DEFAULT_RINGTONE_URI` and `Settings.System.DEFAULT_ALARM_ALERT_URI`) for fallback audio playback.
- **Why:** Guarantees that audio playback works out-of-the-box on any Android device without requiring bundled raw audio files or complex asset management in the initial phases.
- **Decision:** Use standard system-defined default app icon (`@android:drawable/sym_def_app_icon`) in `AndroidManifest.xml` instead of custom mipmap resources.
- **Why:** Avoids build failures due to missing launcher icon resources in the hand-crafted project skeleton, while keeping the build clean and simple.

## 2026-08-04 — Device connectivity: Headless emulation running Android 17

- **Decision:** Switch from physical Pixel 8 Pro over adb reverse tunnel to a headless Android emulator running Android 17 (API 37) on the dev server.
- **Why:** Connecting a physical moving device across different locations proved challenging and unreliable. Running a headless emulator directly on the dev server provides a stable, reproducible, and fully automated environment for testing and development.
- **How:** Install the `emulator` and `system-images;android-37.0;google_apis;x86_64` packages via `sdkmanager`. Create an AVD named `Android17` and run it headlessly using the `-no-window -no-audio` flags.
