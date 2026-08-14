# Handoff: Paperweight OS build-out

Written as the running handoff for the Paperweight OS Android build-out.
The repo now has Milestones 0-3, Overview, and Broadcast rotation/queue
controls implemented. This file is self-contained — a new session shouldn't
need anything from the previous session's local state to pick this up, only
this repo and `paperweightv1`.

## Read first

- `/home/bud/a12/CLAUDE.md` — project constraints and v1 scope, kept current
  through this session (rewritten once already, see "Key decisions" below).
- `paperweightv1`'s `studio/src/views/*.tsx` and `studio/src/lib/api.js` —
  the literal source of truth for what each screen does and which endpoints
  it calls. Not a mockup, not a guess — read the real files when in doubt.

## Status: what's built and verified

- **Gradle project scaffold** — builds and installs on a real Galaxy A12.
  Latest verified on connected `SM-A125U` / API 30 with `adb install -r`.
- **Device Owner / kiosk mode** — `admin/PaperweightDeviceAdminReceiver.kt`,
  provisioning flow, `provisioning/setup.sh`, reusable `DeviceOwnerPolicy`,
  boot/package-replaced receiver, lock-task retry logic, and policy-granted
  camera permission for QR pairing. Connected A12 currently reaches
  `pairing.PairingActivity`; lock-task returned to `LOCKED` after HOME.
- **Design system (M0)** — real Studio tokens and fonts, not the abandoned
  mockup's. Colors computed from `studio/src/index.css`'s HSL values;
  Manrope/Space Grotesk/DM Mono fonts fetched from Google Fonts into
  `res/font/`. `ui/theme/{Color,Type,Shape,Theme}.kt`.
- **QR pairing flow (M1)** — `pairing/` package: CameraX + ML Kit scan of the
  QR from Studio's existing "Pair a new device" feature, direct JSON POST to
  `/api/auth/dashboard/device/redeem` (no WebView), session persisted in
  `EncryptedSharedPreferences` via `network/SessionStore.kt`.
- **Core networking (M2, partial)** — `network/ApiClient.kt` (Retrofit +
  OkHttp with a `DynamicBaseUrlInterceptor` since the base URL is only known
  after pairing), `SessionCookieJar.kt`. DTOs/Retrofit interfaces exist for
  Overview and Broadcast: `AuthApi`, `StreamApi`, `LibraryApi`,
  `DashboardAnalyticsApi`, `DashboardEarningsApi`, and `DashboardBroadcastApi`.
- **Navigation shell (M3)** — `ui/nav/DashboardApp.kt`: hamburger-triggered
  `ModalNavigationDrawer` + `NavHost` with all 9 destinations
  (`DashboardDestination.kt`). Overview and Broadcast are real; the other 7
  render `ComingSoonScreen`.
- **Shared Compose primitives (M3)** — `ui/components/`: `ViewHeader`,
  `MetricTile`, `PanelCard`, `EmptyStateView`, and a `ScreenState` /
  `ScreenStateScaffold` pair every screen's ViewModel should use for uniform
  loading/error/content handling (no retry queue, no cache fallback, per
  CLAUDE.md).
- **Overview screen (M4, 1 of 9)** — `ui/dashboard/overview/`: fully wired
  end-to-end. `OverviewViewModel` polls `stream/status` every 5s (matching
  Studio's `refetchInterval`) and loads `library/structure`,
  `analytics/history`, `earnings`, `analytics/activity` once per visit.
  Renders stat tiles, a Canvas-drawn week-over-week bar chart, and a recent
  activity list.
- **Broadcast screen (M4, 2 of 9, partial)** — `ui/dashboard/broadcast/`:
  real station rotation + broadcast queue controls matching `views/Broadcast.tsx`.
  Polls `stream/status` and `dashboard/broadcast/queue` every 5s, can switch
  shuffle/scheduled mode, restart broadcast, and remove queued tracks. Live mic
  streaming (`dashboard/live/start|chunk|stop` with `AudioRecord`) is still the
  stretch item from the original handoff.

## Validation status and remaining live-backend gap

Local Android validation has been run successfully in this environment:

```text
./gradlew :app:compileDebugKotlin
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest      # NO-SOURCE, but task succeeds
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Latest physical-device facts observed:

```text
device: R58RB5AYA7L
model: SM-A125U
api: 30
package: com.paperweight.os
kiosk: pairing.PairingActivity, lock-task returns to LOCKED after HOME
```

Still not fully exercised against a real paired backend: the QR redeem flow,
Overview data rendering, and Broadcast actions need `npm run dev` (or a deployed
Paperweight station) on the same Wi-Fi as the A12, with a browser logged into
Studio showing the pairing QR. Do not claim backend end-to-end success until the
A12 is actually paired and the screens hit real station endpoints.

Known caution points:
1. The QR redeem flow itself (parsing `pairUrl`, POSTing
   `/api/auth/dashboard/device/redeem`, capturing `Set-Cookie`) has not been
   live-paired yet in this handoff.
2. `LibraryStructure`/`LibraryTrack` DTOs in
   `network/models/LibraryModels.kt` were pulled from
   `paperweightv1/src/api/library.js`'s `formatItem()` and still need a live
   JSON round-trip.
3. `BroadcastQueueItem` intentionally accepts both `id` and legacy `mediaId`
   because `views/Broadcast.tsx` typed `mediaId`, while the current Express
   endpoint returns `id`.

## Key decisions made this session (don't re-litigate these without reason)

These were explicit, confirmed choices — see the a12 CLAUDE.md for the
durable version, but the reasoning is worth carrying forward:

1. **Not the old "Mission Control" mockup.** a12's original CLAUDE.md
   described a single-screen port of `paperweightv1/new_pieces/
   studio-mission-control.html`. That mockup was abandoned. The real target
   is the live Creator Studio dashboard (`studio/src/AppShell.tsx`).
2. **Scope is 9 screens, not 1**: Overview, Broadcast, Schedule, Vault,
   Station, Audience, Analytics, Earnings, **and Settings** — Settings was
   explicitly confirmed as an intentional override of CLAUDE.md's original
   "no settings shortcuts" language (this kiosk should be "a fully
   functioning version of Paperweight," per the user). Deferred to a later
   phase: Activity, Releases, Profile, Tools, Security, and the Stack/Player
   modes.
3. **Full read+write control**, not a read-only monitor — screens should
   wire real mutations, matching what Studio's views actually do.
4. **Auth is QR pairing**, reusing paperweightv1's existing device-pairing
   feature (`POST /api/dashboard/devices/pair` on an already-authenticated
   Studio session → QR → `POST /api/auth/dashboard/device/redeem`). This
   resolves both the station's base URL and the session credential in one
   scan. No WebView — the redeem call is plain JSON + a `Set-Cookie`
   response, called directly.
5. **Design tokens match the real Studio app**, not CLAUDE.md's original
   stale spec (which was written against the abandoned mockup): lime
   primary `hsl(69,100%,65%)` ≈ `#E4FF4D`, coral accent `hsl(7,84%,68%)` ≈
   `#F27969`, red destructive `hsl(4,76%,61%)` ≈ `#E75A50`, near-black
   background. Fonts are Manrope (body) + Space Grotesk (display/headlines)
   + DM Mono (eyebrow/mono labels) — not DM Serif Display/Space Mono.
   Sourced from Google Fonts (both Manrope and Space Grotesk are variable
   fonts; DM Mono is static weights).
6. **`window.desktopAPI`-gated Settings sections don't port.** Studio's
   `SettingsView.tsx` has a `DesktopSection` that only renders inside the
   Electron app (IPC to quit/restart/uninstall/import-folder). There's no
   Android equivalent and no reason to invent one — just omit it, it isn't a
   CLAUDE.md scope question, the bridge simply doesn't exist here.

## What's left: Milestone 4 (remaining screens + Broadcast live stretch) and Milestone 5 (hardening)

Each screen follows the same pattern already established by Overview:
1. Read the real screen source in `paperweightv1/studio/src/views/*.tsx`
   (already read once this session — see notes below per screen — but
   re-read for exact field names before writing DTOs, don't rely on memory).
2. Add DTOs to `network/models/` and a Retrofit interface to `network/`,
   matching `studio/src/lib/api.js`'s exact documented request/response
   shapes for that view's endpoints. Wire new interfaces into
   `ApiClient.kt`.
3. Add a `ui/dashboard/<screen>/` package: `<Screen>UiState.kt`,
   `<Screen>ViewModel.kt` (extends `AndroidViewModel`, exposes
   `StateFlow<ScreenState<UiState>>`, uses `ApiClient`), `<Screen>Screen.kt`
   (Composable, uses `ScreenStateScaffold` + the shared primitives).
4. Swap that destination's `ComingSoonScreen(...)` for the real screen in
   `ui/nav/DashboardApp.kt`'s `DashboardNavHost`.

Screen-by-screen notes (source file, endpoints, what's notable) — this is
the same table from the original build plan:

| Screen | Source | Key endpoints (see `api.js` for exact shapes) | Notes |
|---|---|---|---|
| **Broadcast** | `views/Broadcast.tsx` | `stream/status`, `dashboard/broadcast/queue` (5s poll), `dashboard/broadcast/mode`\|`restart`, `dashboard/live/status`\|`start`\|`chunk`\|`stop` | Rotation mode, restart, queue poll, and queue removal are implemented in native Compose. Remaining stretch: "Go live" mic streaming via `AudioRecord` → PCM chunk POST to `/api/dashboard/live/chunk` plus status/start/stop controls. |
| **Schedule** | `views/ScheduleView.tsx` | `dashboard/schedule` CRUD, `schedule/smart-playlists` CRUD, `schedule/preview` | Two list+form sections (blocks, smart playlists) plus a 24h preview panel. Straightforward CRUD forms. |
| **Vault** | `views/Vault.tsx` | `dashboard/vault/pricing`, `.../highlight`, `dashboard/media`, `dashboard/accounts`, `dashboard/tokens` (+assignments), `dashboard/media/{id}/artwork` (multipart) | Largest screen (353 lines source). Pricing forms, collection management, access tokens. Multipart artwork upload needs `okhttp3.MultipartBody`. |
| **Station** | `views/Station.tsx` | `dashboard/station`, `.../health`, `.../cloudflare/*`, `.../telemetry/*`, `dashboard/setup-progress` | Public URL / tunnel / telemetry config; several independent mutation groups, mostly simple forms + status chips. |
| **Audience** | `views/AudienceView.tsx` | `dashboard/today`, `.../audience-memory/*`, `dashboard/audience`, `dashboard/automations`, `dashboard/participation/*`, `dashboard/creator-type`, `dashboard/radio-host`, `dashboard/external-search` | Dense (124 lines) but mostly list+toggle patterns, no unusual complexity. |
| **Analytics** | `views/Analytics.tsx` | `dashboard/analytics/live`\|`history`\|`top`\|`subscribers`\|`playcounts`, `library/structure` | `DashboardAnalyticsApi` already has most of what's needed (`live`, `history`, `top`, `subscribers`, `playcounts` all already defined) — this screen is mostly UI work, reusing the existing API interface. Canvas bar charts, same technique as Overview's `WeekHistoryChart`. |
| **Earnings** | `views/Earnings.tsx` | `dashboard/earnings`, `dashboard/tip-config` | `DashboardEarningsApi` already exists in full (`earnings()`, `tipConfig()`, `updateTipConfig()`) — this screen is UI-only work reusing what's already there. Revenue mix bar, top-earners list. |
| **Settings** | `views/SettingsView.tsx` | `dashboard/settings` (get/update), `dashboard/accounts` + `.../reset-link`, `api/docs` | **Drop `DesktopSection` entirely** (see decision #6 above). Everything else — notifications/webhook, RSS feed toggle, track glow color, account recovery link generator, docs viewer — is standard form/list work. |

After all 9 screens: **Milestone 5 (hardening)** —
- Session-loss handling: a 401 from any call should route back to
  `PairingActivity` (clear `SessionStore`, `startActivity` + `finish`), not
  crash or silently retry. Not yet implemented anywhere.
- Sweep every screen's `ViewModel` for consistent use of `ScreenState` /
  `ScreenStateScaffold` — Overview's `OverviewViewModel` is the reference
  pattern (single `Job`, cancel-and-relaunch on retry, one-shot load +
  optional light polling).
- Re-confirm the "no retry queue, no local cache fallback, no crash" network
  constraint holds on every screen, not just Overview.

## Repo layout as of this handoff

```
app/src/main/java/com/paperweight/os/
├── MainActivity.kt                 // gates on device-owner + pairing, then DashboardApp
├── admin/PaperweightDeviceAdminReceiver.kt
├── pairing/                        // QR scan + redeem (M1, done)
│   ├── PairingActivity.kt
│   ├── PairingScreen.kt
│   ├── PairingViewModel.kt
│   └── QrCodeAnalyzer.kt
├── provisioning/SetupActivity.kt   // device-owner claim wait screen
├── network/                        // M2, partial — grows per-screen from here
│   ├── ApiClient.kt                // wire new *Api interfaces in here
│   ├── AuthApi.kt / StreamApi.kt / LibraryApi.kt
│   ├── DashboardAnalyticsApi.kt / DashboardEarningsApi.kt / DashboardBroadcastApi.kt
│   ├── SessionStore.kt / SessionCookieJar.kt / DynamicBaseUrlInterceptor.kt
│   └── models/                     // DTOs, grouped like studio's api.js
└── ui/
    ├── theme/                      // Color/Type/Shape/Theme.kt — done (M0)
    ├── nav/                        // DashboardApp, DashboardDestination, ComingSoonScreen — done (M3)
    ├── components/                 // ViewHeader, MetricTile, PanelCard, EmptyStateView, ScreenState(Scaffold) — done (M3)
    └── dashboard/
        ├── overview/                // done (M4, 1/9) — reference pattern for the rest
        │   ├── OverviewScreen.kt
        │   ├── OverviewUiState.kt
        │   └── OverviewViewModel.kt
        ├── broadcast/               // done for rotation/queue; live mic is stretch
        │   ├── BroadcastScreen.kt
        │   ├── BroadcastUiState.kt
        │   └── BroadcastViewModel.kt
        // schedule/, vault/, station/, audience/,
        // analytics/, earnings/, settings/ — not yet created
```

## Verification

Current Android validation baseline:

```bash
export JAVA_HOME="$HOME/.local/jdks/jdk-17"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

./gradlew :app:compileDebugKotlin
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

End-to-end station validation still requires `npm run dev` in `paperweightv1`
on the same Wi-Fi as the A12, then pairing via Studio's "Pair a new device" QR.
After pairing, smoke Overview and Broadcast against the real backend before
claiming network/runtime correctness.
