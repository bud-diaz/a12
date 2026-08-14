# Paperweight OS

## What this is

A single-purpose Android app that turns a Samsung Galaxy A12 into a dedicated
Paperweight terminal. The app becomes Android **Device Owner**, registers itself
as the only launcher, and locks the device into itself via `lockTask` mode.
Stock Android and all system services keep running underneath — we are not
touching the OS image, kernel, or bootloader. We are using Android’s own
device-management APIs (the same ones used for point-of-sale tablets and
corporate-owned kiosk phones) to make this app the only thing a user can reach.

This is a **separate project** from the offline LLM assistant idea (tabled).
Do not conflate the two. This app is a live, networked client — not a local-only
inference device.

## Non-negotiable constraints

- **adb must always work.** Device Owner mode does not disable USB debugging —
  that is intentional and is the escape hatch. Never add code that disables
  USB debugging, revokes adb, or otherwise closes this door. If a task seems to
  require it, stop and ask first.
- **Native UI only.** No WebView, no bundled web build. This is a from-scratch
  Kotlin/Jetpack Compose rebuild of the dashboard UI, referencing the existing
  Paperweight web app (repo: `paperweightv1`) for visual and structural intent,
  not for code reuse.
- **Wifi-only device.** No SIM, no cellular fallback. Handle “no network”
  gracefully (clear inline state, no crash) but do not build offline
  queue-and-retry sync logic — that’s out of scope for v1.
- **Single scope.** Once locked in, the device shows Mission Control and
  nothing else. No app whitelist, no escape UI, no settings shortcuts exposed
  to the end user. The only way out is adb.

## v1 scope

**In scope:**

- Device Owner provisioning flow (one-time setup)
- Lock-task launcher that boots straight into the dashboard
- Native Compose port of the **Mission Control** dashboard screen only
- Live network client hitting the real Paperweight backend (same backend as
  the web app — base URL and auth mechanism TBA, do not hardcode placeholder
  endpoints as if they were final)
- Design system port: colors, type, shape tokens matching the web app

**Explicitly out of scope for v1:**

- Stack/Stash library tab
- Any other dashboard screen beyond Mission Control
- Offline/local data mode
- Multi-app whitelist or app-launching capability
- Play Store distribution (this ships via `adb install` only)

## Design system (port exactly, do not reinterpret)

- Background: near-black
- Accent: acid green `#39ff14`
- Display/numeric type: DM Serif Display
- UI/body type: Space Mono
- Card treatment: glass/vibrancy style, matching the web app’s existing look

Fonts are not system fonts — they must be bundled as font resources
(`res/font/`) and wired into the Compose `Typography` object. Do not substitute
system fonts as a placeholder; ask if the font files aren’t available yet.

## Architecture

```
app/src/main/java/.../
├── MainActivity.kt                          // LockTaskActivity, boots to dashboard
├── admin/
│   └── PaperweightDeviceAdminReceiver.kt    // DevicePolicyManager hooks
├── network/
│   ├── ApiClient.kt                         // Retrofit instance, base URL
│   ├── AuthInterceptor.kt                   // token/session handling
│   └── models/                              // response DTOs matching backend shape
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Shape.kt
│   ├── dashboard/                           // Mission Control screen(s)
│   └── components/                          // reusable Compose pieces
└── provisioning/
    └── SetupActivity.kt                     // one-time device-owner claim flow

provisioning/
└── setup.sh                                 // adb commands for fresh A12 setup
```

## Networking

- Retrofit for the API client.
- Base URL and auth mechanism are **TBA** — do not invent or assume an
  endpoint shape. If a network task comes up before these are provided, stop
  and ask rather than scaffolding against a guessed API.
- Network Security Config file will be required (Android blocks cleartext by
  default) — confirm the backend is HTTPS-clean before wiring this up.
- On connection failure: show a clear inline state in the UI. No retry queue,
  no local cache fallback, no crash.

## Provisioning flow (per physical device)

1. Factory reset the A12.
1. Skip Google account setup entirely — Device Owner provisioning requires no
   accounts present on the device. This is a hard Android constraint, not
   optional.
1. `adb install` the APK.
1. `adb shell dpm set-device-owner com.paperweight.os/.admin.PaperweightDeviceAdminReceiver`
1. App auto-launches into lock-task mode on next boot.

Keep `provisioning/setup.sh` in sync with this list as it evolves.

## Working conventions

- Confirm scope before adding a screen, feature, or dependency not listed
  above — this project has a documented tendency to expand past its stated
  boundary (see: Paperweight web app’s Stack/Stash growth). Stay in Mission
  Control until it’s solid.
- When referencing the web app for parity, describe what to port in plain
  terms (layout, spacing, interaction) rather than assuming direct code
  portability — different platform, native rebuild.
- Ask before touching adb/USB debugging configuration, Device Owner policy
  scope, or anything that could reduce recoverability of the physical device.
