# Product Context — On Time

## What this is

"On Time" is an Android sound machine + alarm clock app. Full requirements live in
[`architecture/requirements.md`](../../architecture/requirements.md) — read that first,
it is the source of truth for scope. Detailed technical design lives in
[`architecture/architecture.md`](../../architecture/architecture.md).

## Why it exists

Personal project for the developer (first Android app). Combines a background
sound/white-noise player with a schedulable alarm clock, including background→alarm
audio transitions and reboot-safe persisted state.

## Developer context

- First Android app for this developer — explanations of Android/Kotlin concepts are
  expected alongside implementation, not just code.
- Works across multiple physical machines (Linux and Windows depending on location).
  No Android Studio GUI — CLI-only toolchain (see `decisionLog.md`).
- Test device: physical Pixel 8 Pro, connected via adb (USB or wireless debugging).

## High-level goals (see requirements.md for full detail)

1. Play/loop background sound and alarm tones, surviving screen lock.
2. Schedule any number of start-alarm / stop-background events by day-of-week + time,
   each independently enable/disable-able.
3. Manual user actions: timed background play, stop, dismiss alarm, snooze.
4. Settings for sound sources, fade times, snooze delay.
5. Resume pending state (playing sound, armed schedules) after reboot/app upgrade.
