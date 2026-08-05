# On Time - lightweight LOCAL MACHINE adb installer (Windows).
#
# Run this on the machine that has the Pixel 8 Pro physically attached (over
# USB). It installs ONLY the adb client/server (platform-tools) — the full
# JDK/Android SDK/Gradle toolchain lives on the dev server instead, and talks
# to this local adb server through an SSH reverse tunnel. See
# architecture/remote-adb-workflow.md for the full workflow.
#
# Usage (PowerShell):
#   .\scripts\setup-adb-only.ps1
#
# Override install location with $env:ANDROID_ADB_HOME (default: %USERPROFILE%\.android-adb).

$ErrorActionPreference = "Stop"

$AdbHome = if ($env:ANDROID_ADB_HOME) { $env:ANDROID_ADB_HOME } else { Join-Path $env:USERPROFILE ".android-adb" }
$AdbExe = Join-Path $AdbHome "platform-tools\adb.exe"

if (Test-Path $AdbExe) {
    Write-Host "adb already installed at $AdbExe"
} else {
    Write-Host "Downloading platform-tools (windows)..."
    New-Item -ItemType Directory -Force -Path $AdbHome | Out-Null
    $zip = Join-Path $AdbHome "platform-tools.zip"
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $AdbHome -Force
    Remove-Item $zip
}

Write-Host ""
Write-Host "Done. adb installed at: $AdbExe"
Write-Host ""
Write-Host "Add it to your PATH for this session with:"
Write-Host "  `$env:Path = `"$AdbHome\platform-tools;`$env:Path`""
Write-Host "(Add the same line to your PowerShell profile to make it permanent.)"
Write-Host ""
Write-Host "Then plug in the Pixel 8 Pro over USB, enable USB debugging, and run:"
Write-Host "  adb devices    # should show the device as 'unauthorized' or 'device'"
Write-Host ""
Write-Host "Next: add the RemoteForward SSH config entry so the dev server can"
Write-Host "reach this local adb server -- see architecture/remote-adb-workflow.md."
