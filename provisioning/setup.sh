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

echo "Done. Reboot the device to boot straight into Mission Control."
