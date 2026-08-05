#!/usr/bin/env bash
# On Time - lightweight LOCAL MACHINE adb installer (Linux / macOS).
#
# Run this on the machine that has the Pixel 8 Pro physically attached (over
# USB). It installs ONLY the adb client/server (platform-tools) — the full
# JDK/Android SDK/Gradle toolchain lives on the dev server instead, and talks
# to this local adb server through an SSH reverse tunnel. See
# architecture/remote-adb-workflow.md for the full workflow.
#
# Usage:
#   ./scripts/setup-adb-only.sh
#
# Override install location with ANDROID_ADB_HOME (default: ~/.android-adb).
set -euo pipefail

ADB_HOME="${ANDROID_ADB_HOME:-$HOME/.android-adb}"

detect_os() {
  case "$(uname -s)" in
    Linux*) echo "linux" ;;
    Darwin*) echo "darwin" ;;
    *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
  esac
}

main() {
  command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
  command -v unzip >/dev/null || { echo "unzip is required" >&2; exit 1; }

  local os_tag
  os_tag="$(detect_os)"

  if [ -x "$ADB_HOME/platform-tools/adb" ]; then
    echo "adb already installed at $ADB_HOME/platform-tools/adb"
  else
    echo "Downloading platform-tools ($os_tag)..."
    mkdir -p "$ADB_HOME"
    curl -fL "https://dl.google.com/android/repository/platform-tools-latest-${os_tag}.zip" \
      -o "$ADB_HOME/platform-tools.zip"
    unzip -q -o "$ADB_HOME/platform-tools.zip" -d "$ADB_HOME"
    rm -f "$ADB_HOME/platform-tools.zip"
  fi

  echo
  echo "Done. adb installed at: $ADB_HOME/platform-tools/adb"
  echo
  echo "Add it to your PATH, e.g. add this to ~/.bashrc / ~/.zshrc:"
  echo "  export PATH=\"$ADB_HOME/platform-tools:\$PATH\""
  echo
  echo "Then plug in the Pixel 8 Pro over USB, enable USB debugging, and run:"
  echo "  adb devices    # should show the device as 'unauthorized' or 'device'"
  echo
  echo "Next: add the RemoteForward SSH config entry so the dev server can"
  echo "reach this local adb server — see architecture/remote-adb-workflow.md."
}

main "$@"
