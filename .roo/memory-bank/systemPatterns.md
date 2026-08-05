# System Patterns — On Time

Coding/architectural patterns specific to this project. For the full component
diagram and package layout, see
[`architecture/architecture.md`](../../architecture/architecture.md).

## Patterns to follow

- **Service owns playback.** Only the Audio Foreground Service touches
  `MediaPlayer`/`ExoPlayer`. Activities/Fragments never play audio directly — they
  send commands (start/stop/fade) to the Service via Intents or a bound interface.
- **Receivers forward, never do work.** `BroadcastReceiver`s (alarm trigger, boot)
  must only relay an instruction to the Service/Repository and return quickly — no
  playback, no long-running I/O inside `onReceive`.
- **Enabled flag, not deletion.** Schedule events are toggled via an `enabled: Boolean`
  field. The scheduling engine filters on this flag when arming `AlarmManager`; it
  never deletes/recreates the underlying event row when toggled.
- **Stable PendingIntent request codes.** Each schedule event's `AlarmManager`
  PendingIntent request code must be derived deterministically from its persisted
  row ID (not array index or insertion order), since events can be added/removed in
  any order and must remain individually re-armable.
- **Sequential fade transition.** Background→alarm transition is background-fades-to-
  silent over `t` seconds, THEN alarm fades in over the next `t` seconds — implemented
  as two sequential fade operations, not two simultaneous ones.
- **Distinct alarm vs. background UI.** Any shared UI component between alarm
  controls and background-sound controls must still render them as visually and
  interactively separate — this is an explicit requirement, not a style preference.

## Toolchain patterns

- No Android Studio GUI. Use `./gradlew` (wrapper, bootstrapped via
  `scripts/setup-dev-env.sh`/`.ps1`) and `adb` from the command line for all
  build/install/test/debug workflows.
- **Sourcing Environment Variables.** Always use the POSIX dot operator (`. scripts/env.sh`) instead of `source scripts/env.sh` to load environment variables in the shell.
- **Build-Time String.** Whenever building or releasing a new version of the app, update the build-time string in `app/src/main/res/layout/activity_settings.xml` (under the `textBuildInfo` TextView) to the current date and time so the user can verify they are running the latest build.
- Tool binaries (JDK, Android SDK, bootstrap Gradle) live in the gitignored
  `.tools/` directory local to each checkout — never commit them, never assume a
  system-wide install exists.
- **The physical device is never attached to the dev server.** Gradle/build/adb
  commands run on the dev server; the Pixel 8 Pro is attached to whichever local
  machine is in current use. adb reaches it through an SSH reverse tunnel
  (`RemoteForward 5037 127.0.0.1:5037`), not direct USB/LAN — see
  `architecture/remote-adb-workflow.md`. Do not assume `adb devices` will see
  anything on the dev server unless that tunnel is active for the current SSH
  session.
