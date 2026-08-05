# Progress — On Time

Update this file at the end of every work session: move completed items to "Done",
keep "In Progress" to a single item, and make sure "Next Step" is concrete enough
that a fresh session can pick it up with no other context.

## Done

- Requirements captured in [`architecture/requirements.md`](../../architecture/requirements.md)
  (includes "any quantity" of schedule events + per-event Enabled toggle).
- **Phase 8**: Implemented `ScheduleEvent` data model, `ScheduleEditorActivity` (with full day-of-week and "Daily" checkbox logic), and `ScheduleListActivity` (with RecyclerView, direct toggle support, and add/edit/delete flows).
  (includes "any quantity" of schedule events + per-event Enabled toggle).
- Architecture/design documented in [`architecture/architecture.md`](../../architecture/architecture.md).
- Decision log started: [`decisionLog.md`](decisionLog.md).
- Dev topology clarified: dev server (workspace host, persistent, reached via SSH)
  is separate from the local machine currently in use (desktop A Linux, desktop B
  Windows, or off-site laptop Linux) which has the Pixel 8 Pro physically attached.
  Resolved with an SSH reverse-tunnel adb workflow — see
  [`architecture/remote-adb-workflow.md`](../../architecture/remote-adb-workflow.md).
- Portable CLI dev-env bootstrap scripts written for the **dev server**:
  `scripts/setup-dev-env.sh` (Linux/macOS), `scripts/setup-dev-env.ps1` (Windows).
- Lightweight adb-only installer scripts written for **local machines**:
  `scripts/setup-adb-only.sh`, `scripts/setup-adb-only.ps1`.
- Ran `setup-dev-env.sh` on the dev server (this Debian host): JDK 17, Android SDK
  cmdline-tools, platform-tools, platforms;android-34, build-tools;34.0.0 all
  installed under `.tools/` (gitignored). `local.properties` written with
  `sdk.dir`.
- Project skeleton created by hand (no Android Studio wizard available):
  `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`,
  `app/build.gradle.kts` (namespace/applicationId `com.kappacs.ontime`, minSdk 26 /
  targetSdk 34 / compileSdk 34), `app/src/main/AndroidManifest.xml`,
  `MainActivity.kt`, `activity_main.xml`, `strings.xml`, `themes.xml`.
- **Phase 1**: Setup development environment and verified build.
- **Phase 2**: Designed and implemented Main Screen UI (background sound controls, alarm status, navigation).
- **Phase 3**: Implemented Audio Foreground Service (`AudioPlaybackService`) for background playback.
- **Phase 4**: Implemented DataStore-backed `SettingsRepository` and `SettingsActivity` to persist and edit settings.
- **Phase 5-7**: Implemented `FadeController` for smooth audio transitions and fade-in/out.
- **Phase 10**: Implemented `BootAndUpgradeReceiver` to restore state on reboot/upgrade (including background sound, active alarms, and snoozed alarms).
- **Open Issues Resolved**: Fixed background service crashes, implemented primary/secondary/tertiary alarm status, honored 12/24 hour format preferences, added next event time to schedule list, and fixed alarm triggering and reboot persistence.

## In Progress

- None

## Next Step (pick up here in a fresh session)

1. Implement automated tests or next phase.

## Phase checklist (mirrors the live todo list; update both together)

- [x] Phase 1: Setup development environment and verify build
- [x] Phase 2: Design and implement Main Screen UI (background sound controls, alarm status, navigation)
- [x] Phase 3: Implement Audio Foreground Service (AudioPlaybackService) for background playback
- [x] Phase 4: Implement DataStore-backed SettingsRepository and SettingsActivity
- [x] Phase 5-7: Implement FadeController for smooth audio transitions and fade-in/out
- [x] Phase 8: Implement ScheduleEvent data model and ScheduleEditorActivity
- [x] Phase 9: Implement ScheduleRepository (Room database) and ScheduleEngine (AlarmManager)
- [x] Phase 10: Implement BootAndUpgradeReceiver to restore state on reboot/upgrade
