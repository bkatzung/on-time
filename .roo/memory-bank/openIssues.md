# Open Issues

All previously identified issues have been successfully resolved:

- **Fixed**: Frequently errors out with "On Time keeps stopping" (resolved by ensuring `startForeground()` is always called synchronously in `onStartCommand()` and using `ContextCompat.startForegroundService()` from background receivers).
- **Fixed**: Primary alarm status should show "Active" (ringing), "Inactive", or "Snoozing for mm:ss" (implemented with a battery-efficient snooze ticker).
- **Fixed**: Secondary alarm status should show "enabled: <n>" (combined with primary status as requested, e.g., `Inactive; enabled: 3`).
- **Fixed**: Tertiary alarm status should show "next: <hh:mm>" if the next alarm will fire within 24 hours (implemented as a conditional second output, e.g., `Next: 08:00`).
- **Fixed**: Should honor user time format preferences (12/24 hour) (implemented across all screens using `DateFormat.is24HourFormat(context)`).
- **Fixed**: Events list should show next event time when enabled (implemented in the schedule list adapter, e.g., `Start Alarm • Mon, Tue, Wed, Thu, Fri • Next: Mon 08:00`).
- **Fixed**: Alarm events do not trigger alarms!! (resolved by scheduling `PendingIntent.getForegroundService()` directly from `AlarmManager` to target `AudioPlaybackService` with `ACTION_TRIGGER_EVENT`, completely bypassing background broadcast receiver restrictions on Android 14/17).
- **Fixed**: Background sound does not continue after reboot (resolved by fixing the foreground service start crash on boot and wrapping service starts in `BootAndUpgradeReceiver` in a `try-catch` block).
- **Added**: High-visibility error-level logging for key success paths (scheduling, triggering, boot restore) so they can be easily verified in logcat.
- **Added**: Build version/time display on the settings page (e.g., `Build: 2026-08-04 03:16`) to verify the latest build is installed and running.
