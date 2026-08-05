#!/usr/bin/env bash
# On Time - Headless Emulator Run Script
#
# Starts the "Android17" AVD headlessly in the background and waits for it to boot.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source environment variables
if [ -f "$SCRIPT_DIR/env.sh" ]; then
  . "$SCRIPT_DIR/env.sh"
else
  echo "Error: scripts/env.sh not found. Please run setup-dev-env.sh first." >&2
  exit 1
fi

log() { printf '\n[run-emulator] %s\n' "$1"; }

# Check if AVD exists
if ! avdmanager list avd | grep -q "Name: Android17"; then
  log "Error: AVD 'Android17' not found. Please run ./scripts/setup-emulator.sh first." >&2
  exit 1
fi

# Check if emulator is already running
if adb devices | grep -q "emulator-"; then
  log "An emulator is already running. Current devices:"
  adb devices
  exit 0
fi

log "Starting emulator 'Android17' headlessly in the background..."
log "Logs will be written to $PROJECT_ROOT/emulator.log"

# Start emulator in background
# -no-window: headless
# -no-audio: disable audio
# -gpu guest: use guest rendering (safe for headless servers without X11/KVM)
# -no-accel: run without hardware acceleration (KVM) if permissions are missing
emulator -avd Android17 -no-window -no-audio -gpu guest > "$PROJECT_ROOT/emulator.log" 2>&1 &

log "Waiting for emulator to connect to adb..."
adb wait-for-device

log "Waiting for system boot to complete (this may take a minute)..."
while [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
  sleep 2
done

log "Emulator is fully booted and ready!"
adb devices
