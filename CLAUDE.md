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
- **Single scope.** Once locked in, the device shows the Paperweight Studio
  dashboard (see v1 scope below) and nothing else. No app whitelist, no escape
  UI. The only way out is adb. Settings **is** exposed on this kiosk — that's a
  deliberate, confirmed exception to the general "don't expose device/OS
  settings" spirit, not a loophole to expand further without asking.

## v1 scope

**In scope** — a native Compose port of the real Creator Studio dashboard
(`paperweightv1/studio/src/AppShell.tsx`), not the old Mission Control mockup
(see Design system below), scoped to its core operational views:

- Device Owner provisioning flow (one-time setup)
- **Station pairing flow**: the app has no baked-in backend — on first boot it
  shows a QR scanner; the operator pairs it against a station's Studio
  dashboard (already-logged-in on a desktop/laptop) the same way the existing
  "Pair a new device" mobile-pairing feature works
  (`POST /api/dashboard/devices/pair` → scan → `POST /api/auth/dashboard/device/redeem`).
  This resolves both the station's base URL and the session credential in one
  step — there is no separate "enter server URL" step.
- Lock-task launcher that boots straight into the dashboard once paired
- Native Compose screens: **Overview, Broadcast, Schedule, Vault, Station,
  Audience, Analytics, Earnings, Settings** — full read+write control (real
  mutations, not a read-only monitor), matching what each view does in
  `studio/src/views/*.tsx`
- Live network client hitting the real Paperweight backend the operator paired
  with (see Networking below)
- Design system port: colors, type, shape tokens matching the real Studio app

**Explicitly out of scope for v1** (candidates for a later phase):

- Activity, Releases, Profile, Tools, and Security views
- The Stack (library) and Player modes
- Live video / RTMP broadcast (Broadcast screen's audio-only "Go live" via mic
  is in scope; video go-live is not)
- Offline/local data mode
- Multi-app whitelist or app-launching capability
- Play Store distribution (this ships via `adb install` only)

## Design system (port exactly, do not reinterpret)

Matches the real Studio app's production tokens (`paperweightv1/studio/src/index.css`),
**not** the abandoned `new_pieces/studio-mission-control.html` mockup — an
earlier version of this doc was written against that mockup and was wrong.

- Background: near-black / true black
- Primary (lime): `hsl(69, 100%, 65%)` ≈ `#E4FF4D`
- Accent (coral): `hsl(7, 84%, 68%)` ≈ `#F27969`
- Destructive (red): `hsl(4, 76%, 61%)` ≈ `#E75A50`
- Foreground: `hsl(224, 30%, 94%)` (near-white, cool tint)
- Display/headline type: **Space Grotesk** (Studio's `.font-display`)
- Label/eyebrow/mono-readout type: **DM Mono** (Studio's `.font-mono-ui`)
- Body/UI type: **Manrope** (Studio's base sans)
- Card treatment: glass/vibrancy style, matching the web app's existing look

Fonts are not system fonts — they must be bundled as font resources
(`res/font/`) and wired into the Compose `Typography` object. Do not substitute
system fonts as a placeholder; ask if the font files aren't available yet.

## Architecture

```
app/src/main/java/.../
├── MainActivity.kt                          // LockTaskActivity, boots to dashboard
├── admin/
│   └── PaperweightDeviceAdminReceiver.kt    // DevicePolicyManager hooks
├── pairing/
│   └── PairingActivity.kt                   // QR scan + device/redeem, persists session
├── network/
│   ├── ApiClient.kt                         // Retrofit instance, dynamic-base-URL interceptor
│   ├── SessionCookieJar.kt                  // persisted pw_dashboard_session cookie
│   └── models/                              // response DTOs, grouped like studio's api.js
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Shape.kt
│   ├── nav/                                 // NavHost + drawer shell (9 destinations)
│   ├── dashboard/
│   │   ├── overview/
│   │   ├── broadcast/
│   │   ├── schedule/
│   │   ├── vault/
│   │   ├── station/
│   │   ├── audience/
│   │   ├── analytics/
│   │   ├── earnings/
│   │   └── settings/
│   └── components/                          // reusable Compose pieces (ViewHeader, MetricTile, ...)
└── provisioning/
    └── SetupActivity.kt                     // one-time device-owner claim flow

provisioning/
└── setup.sh                                 // adb commands for fresh A12 setup
```

## Networking

- Retrofit for the API client.
- Base URL and auth are resolved together at pairing time, not hardcoded: the
  operator scans a QR code from an already-authenticated Studio session
  (`pairUrl = "{baseUrl}/pair?pt={pairToken}"`); the app parses `baseUrl` from
  that URL and calls `POST {baseUrl}/api/auth/dashboard/device/redeem` with
  `{ pairToken }` directly (no WebView — that endpoint is plain JSON + a
  `Set-Cookie` response). See `paperweightv1`'s `src/api/auth.js` and
  `src/api/dashboard.js` (`devices/pair` + `device/redeem`) for the exact
  contract.
- The resulting `pw_dashboard_session` cookie and paired `baseUrl` are
  persisted in `EncryptedSharedPreferences` and reused via a custom
  `CookieJar` — this is dashboard auth (full station control), not listener
  auth.
- Network Security Config: the paired `baseUrl` may be a plain local address
  during development (`npm run dev` on the same Wi-Fi) — don't assume
  HTTPS-only; confirm the actual deployment's scheme before hardening cleartext
  policy.
- On connection failure: show a clear inline state in the UI. No retry queue,
  no local cache fallback, no crash. A 401 from any call routes back to the
  pairing flow rather than retrying silently.

## Provisioning flow (per physical device)

1. Factory reset the A12. If this is a legitimate re-provisioning of a used Paperweight device, leave any Android/Samsung "erase SD card" option unchecked so existing `Paperweight/vault/` media and `Paperweight/backups/` snapshots remain on the removable card.
1. Skip Google account setup entirely — Device Owner provisioning requires no
   accounts present on the device. This is a hard Android constraint, not
   optional.
1. `adb install` the APK.
1. `adb shell dpm set-device-owner com.paperweight.os/.admin.PaperweightDeviceAdminReceiver`
1. App auto-launches into lock-task mode on next boot and shows the QR pairing
   screen.
1. On a desktop/laptop already signed into that station's Studio dashboard,
   open "Pair a new device" and show the QR. Scan it with the A12.
1. Once paired, the app boots straight into the dashboard on every subsequent
   launch.

Keep `provisioning/setup.sh` in sync with the device-owner steps as they
evolve; the pairing step happens in-app and isn't part of that script.

## Working conventions

- Confirm scope before adding a screen, feature, or dependency beyond the nine
  views listed under v1 scope — this project has a documented tendency to
  expand past its stated boundary (see: Paperweight web app's Stack/Stash
  growth).
- When referencing the web app for parity, treat `studio/src/views/*.tsx` and
  `studio/src/lib/api.js` as the literal source of truth for behavior and
  endpoint shapes, but describe layout/interaction in plain terms rather than
  assuming direct code portability — different platform, native rebuild.
- Ask before touching adb/USB debugging configuration, Device Owner policy
  scope, or anything that could reduce recoverability of the physical device.
