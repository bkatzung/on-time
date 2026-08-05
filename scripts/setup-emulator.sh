#!/usr/bin/env bash
# On Time - Headless Emulator Setup Script
#
# Installs the Android emulator and Android 17 (API 37) system image,
# then creates a virtual device (AVD) named "Android17".
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

log() { printf '\n[setup-emulator] %s\n' "$1"; }

# 1. Install emulator and system image
log "Installing emulator and Android 17 system image (API 37)..."
sdkmanager --sdk_root="$ANDROID_HOME" "emulator" "system-images;android-37.0;google_apis;x86_64"

# 2. Create AVD
log "Creating AVD 'Android17'..."
# We pipe "no" to answer the custom hardware profile prompt
echo "no" | avdmanager create avd -n Android17 -k "system-images;android-37.0;google_apis;x86_64" --force

log "AVD 'Android17' created successfully!"
log "You can now start it using: ./scripts/run-emulator.sh"
