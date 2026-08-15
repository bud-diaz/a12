#!/usr/bin/env bash
# Fresh A12 setup, per CLAUDE.md provisioning flow:
#   1. Factory reset the device, skip Google account setup entirely.
#   2. Run this script with adb connected over USB.
#   3. Reboot — the app auto-launches into lock-task mode.
set -euo pipefail

PACKAGE="com.paperweight.os"
ADMIN_RECEIVER="${PACKAGE}/.admin.PaperweightDeviceAdminReceiver"
APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"

if [ ! -f "${APK_PATH}" ]; then
    echo "APK not found at ${APK_PATH}" >&2
    echo "Build it first, or pass a path: provisioning/setup.sh <path-to-apk>" >&2
    exit 1
fi

echo "Installing ${APK_PATH}..."
adb install -r "${APK_PATH}"

echo "Claiming device owner..."
adb shell dpm set-device-owner "${ADMIN_RECEIVER}"

cat <<'EOF'
Done. Reboot the device to boot straight into Paperweight OS.

Vault setup note for the operator:
  - If Android ever needs a factory reset/re-provisioning, leave any "erase SD card" option unchecked; Paperweight backups and vault media live on that removable card.
  - Insert the removable SD card before opening the Vault screen.
  - The first time you tap "Add to vault," Android's folder picker will open.
  - Open the SD card, create or select a folder named exactly "Paperweight,"
    then tap "Use this folder."
  - Paperweight OS will store ingested media under Paperweight/vault/.
EOF
