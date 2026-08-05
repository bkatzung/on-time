#!/usr/bin/env bash
exec adb logcat -s AudioPlaybackService:D FadeController:D BootAndUpgradeReceiver:D ScheduleEngine:D ScheduleEventReceiver:D *:S

