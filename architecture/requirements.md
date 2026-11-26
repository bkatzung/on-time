# "On Time" Sound Machine And Alarm Clock Android App

- Must be able to play/loop audio tracks
  - Background sound tracks (e.g. white noise, water tracks)
  - Alarm tones
- Must run even with screen locked
- If possible, pending state (playing sound, scheduled alarms) should automatically resume after app upgrade/restart
- Must be able to schedule events
  - Specific days of the week (checkboxes)
  - "Daily" checkbox sets all days on unset->set; clears all days on set->unset
  - Specific time (with minute-level resolution)
- Schedulable events (any quantity):
  - Stop background sound
    - Fade-out time (seconds; default 0)
  - Start alarm sound 
    - Fade-in/transition time (seconds; default 0)
    - Transition strategy if background sound is playing at alarm event:
      - Transition background sound to *silent* over first `t` seconds
      - Transition to alarm sound over next `t` seconds
      - (Total transition time 2 * t seconds)
  - "Enabled" toggle allows enable/disable without removal + recreation
- User Actions
  - Play background sound for HH:MM (00:00 = until stopped)
    - Timed sound uses settings-based fade time
  - Stop backgound sound
  - Dismiss alarm
  - Snooze alarm (using settings-based delay in minutes)
  - Alarm controls need to be clearly distinct from background sound controls
- Settings
  - Background sound source
  - Alarm sound source
  - Timed background fade time (seconds; default 0)
  - Alarm snooze time (minutes; default 5)
  - Haptic feedback enabled (boolean; default false/off)
- Implementation
  - Kotlin
  - Android Studio

# Updated Requirements - 2026-08-04

- Add the day abbreviation to the tertiary alarm status (e.g. "Wed 08:00" instead of just "08:00"; it's a bit confusing to see just "08:00", unchanged, when the alarm has just gone off on Tuesday at 08:00)
- Can we get the two-pass rotary time selector in the event editor (the one that works more like setting hands on an analog clock rather than the digit-dials style)?
- Remove "Trigger Alarm" from the main page and add `Play` and `Stop` buttons in the alarm settings.
- Haptic feedback should be configurable in settings (default off / disabled).
- Volume and Power buttons are too easy to press accidentally or mistake one for the other; do not use them as alternate inputs for snooze or dismiss.
