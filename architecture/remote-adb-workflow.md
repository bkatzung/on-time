# Device Connectivity & Headless Emulator Workflow

## Primary Workflow: Headless Emulator on Dev Server

To avoid the complexity of connecting physical devices across different locations, we use a headless Android emulator running Android 17 (API 37) directly on the dev server.

### Topology

- **Dev server** — persistent host reachable via SSH. Holds the git repo, JDK, Android SDK, Gradle, and the headless Android emulator.
- **Local machine** — used only to SSH into the dev server. No local adb or device connection is required.

### Setup and Usage

1. **Install Emulator & System Image**:
   Run the setup script to install the emulator and the Android 17 system image:
   ```bash
   ./scripts/setup-emulator.sh
   ```

2. **Start the Emulator**:
   Start the emulator headlessly in the background:
   ```bash
   ./scripts/run-emulator.sh
   ```
   This starts the emulator with `-no-window -no-audio` flags.

3. **Verify Connection**:
   ```bash
   adb devices
   ```
   The emulator should appear as `emulator-5554`.

4. **Build and Install**:
   ```bash
   ./gradlew installDebug
   ```

---

## Alternative Workflow: Remote adb (SSH Tunnel) for Physical Devices

If you still need to test on a physical device (e.g., Pixel 8 Pro) attached to your local machine, you can use the SSH reverse tunnel workflow.

### Topology

- **Dev server** — persistent host reachable via SSH from everywhere. Holds the git repo, JDK, Android SDK, and Gradle.
- **Local machine** — whichever physical computer you're using right now. The physical device is attached here over USB. Only needs the lightweight `adb` client/server.

### Why this works regardless of network/location

`adb`'s client/server protocol is plain TCP on port 5037. SSH reverse port forwarding (`-R`) forwards that port from the local machine, through the SSH connection, to a socket on the dev server. The adb *client* on the dev server doesn't distinguish a tunneled socket from a real local server — commands issued on the dev server (`adb devices`, `./gradlew installDebug`, `adb logcat`) transparently reach the real device attached to whichever local machine currently holds the tunnel open.

## One-time setup per local machine

1. Run the adb-only installer:
   - Linux/macOS: `./scripts/setup-adb-only.sh`
   - Windows (PowerShell): `.\scripts\setup-adb-only.ps1`
2. Add adb to `PATH` (the script prints the exact line).
3. Connect your device to the local machine. You can do this via USB or Wi-Fi:

   ### Option A: USB Connection
   - Plug in the Pixel 8 Pro via USB.
   - Enable **Developer Options** → **USB debugging**.
   - Accept the RSA key prompt on the phone.
   - Verify with: `adb devices`.

   ### Option B: Wireless Debugging (Wi-Fi)
   - Ensure both your Pixel 8 Pro and your local Windows machine are on the **same Wi-Fi network**.
   - On your phone, go to **Settings** → **System** → **Developer options**.
   - Scroll down to **Wireless debugging** and toggle it ON.
   - Tap on the **Wireless debugging** text to open its settings page.
   - Tap **Pair device with pairing code**. Note the **Wi-Fi pairing code** and the **IP address & Port** (e.g., `192.168.1.100:45678`).
   - In PowerShell on your local Windows machine, run:
     ```powershell
     adb pair <IP_ADDRESS>:<PAIRING_PORT>
     ```
     Enter the pairing code when prompted.
   - Once paired, look at the main **Wireless debugging** screen for the **IP address & Port** under "IP address & Port" (the port is usually different from the pairing port, e.g., `192.168.1.100:34567`).
   - In PowerShell on your local Windows machine, run:
     ```powershell
     adb connect <IP_ADDRESS>:<CONNECTION_PORT>
     ```
   - Verify with: `adb devices` (should show your device as connected).

4. Add an SSH config entry so the reverse tunnel is established automatically every
   time you connect to the dev server (works with Windows' built-in OpenSSH client,
   Git Bash, WSL, and native Linux/macOS ssh — all read the same `~/.ssh/config`
   syntax; on Windows this file is `%USERPROFILE%\.ssh\config`):

   ```
   Host devserver
       HostName <dev-server-hostname-or-ip>
       User <your-username>
       RemoteForward 5037 127.0.0.1:5037
   ```

   Replace `devserver` / `HostName` / `User` with your real values. If VS Code
   Remote-SSH already has a working `Host` entry for this box, just add the
   `RemoteForward` line to it — no separate connection needed.

   (PuTTY users: equivalent is a "Remote" tunnel rule in Connection → SSH →
   Tunnels: Source port `5037`, Destination `127.0.0.1:5037`, direction Remote.)

## Every session, on the dev server

```bash
adb kill-server   # clear any stale local server before trusting the tunnel
adb devices       # should list the Pixel 8 Pro through the tunnel
./gradlew installDebug
adb logcat
```

## Gotchas

- Only one local machine's tunnel can bind the dev server's port 5037 at a time. If
  switching physical machines, fully close the previous SSH connection first (or its
  forward will hold the port and the new one will silently fail to forward).
- Keep `platform-tools` versions reasonably in sync between the local adb server and
  the dev server's adb client — both are installed via Google's "latest"
  platform-tools zip, so re-running the setup scripts periodically keeps them
  aligned.
- This tunnel only carries the adb protocol. It does not give the dev server generic
  USB access — don't expect other USB tooling (e.g. `lsusb`-based flashing tools) to
  work through it.
