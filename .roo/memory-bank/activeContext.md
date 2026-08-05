# Active Context — On Time

Single source of truth for "what am I doing right now". Overwrite this file's
content each session — it is not a log (use `decisionLog.md`/`progress.md` for
history).

## Status

Phases 1 through 10 have been successfully completed and verified to build.

## Current focus (when resumed)

Phase 10: Implement `BootAndUpgradeReceiver` to restore state on reboot/upgrade (including background sound, active alarms, and snoozed alarms).

## Immediate next step

1. Verify the build and ensure everything compiles successfully.

## Key context a fresh session must know

- This is the developer's first Android app — explain concepts, don't just write
  code silently.
- No Android Studio GUI anywhere in this workflow — everything is Gradle CLI + adb.
- Dev server (where builds run) and the physically-attached Pixel 8 Pro are on
  *different* machines; adb only reaches the device through an SSH reverse tunnel.
  See `architecture/remote-adb-workflow.md` before attempting any `adb`/install
  command on the dev server.
- Package name is `com.kappacs.ontime` (developer's domain, reversed), not a
  generic `com.example.*`.
- Full requirements (including the two later clarifications: unlimited schedule
  events, per-event Enabled toggle) are in `architecture/requirements.md` — always
  re-read it, don't rely on summaries.
- Configure environment (using `. scripts/env.sh`) before builds. Sets `JAVA_HOME`, updates `PATH`, and more.

## Open questions / things to watch

- `commandlinetools-*-11076708_latest.zip` build number in
  `scripts/setup-dev-env.sh`/`.ps1` is a point-in-time snapshot from Google; if
  downloads start failing with 404, check
  https://developer.android.com/studio#command-line-tools-only for the current
  build number and update both scripts.
- `scripts/setup-dev-env.ps1` and `scripts/setup-adb-only.ps1` have not been
  tested on an actual Windows machine yet — validate the first time the developer
  uses desktop B (Windows) or needs Windows adb.
- The SSH `RemoteForward 5037` tunnel approach is designed but not yet validated
  end-to-end on any of the three local machines (desktop A Linux, desktop B
  Windows, off-site laptop). Test this before assuming Phase 0e/0f are unblocked.
- Headless Android emulator on the dev server was explicitly deferred (not
  needed for manual testing since the tunnel provides real-device access from
  anywhere) — revisit only when reaching Phase 11 (automated tests).
