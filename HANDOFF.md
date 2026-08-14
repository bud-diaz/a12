# Handoff: Paperweight OS build-out

Written at the end of a session that scaffolded the project and built
Milestones 0-3 (plus the first screen of Milestone 4) of the full build-out
plan. This file is self-contained — a new session shouldn't need anything
from the previous session's local state to pick this up, only this repo and
`paperweightv1`.

## Read first

- `/home/user/a12/CLAUDE.md` — project constraints and v1 scope, kept current
  through this session (rewritten once already, see "Key decisions" below).
- `paperweightv1`'s `studio/src/views/*.tsx` and `studio/src/lib/api.js` —
  the literal source of truth for what each screen does and which endpoints
  it calls. Not a mockup, not a guess — read the real files when in doubt.

## Status: what's built and verified

- **Gradle project scaffold** — builds and installs on a real Galaxy A12
  (confirmed by the user before this session's work started).
- **Device Owner boilerplate** — `admin/PaperweightDeviceAdminReceiver.kt`,
  provisioning flow, `provisioning/setup.sh`. Unchanged this session, still
  working.
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
  after pairing), `SessionCookieJar.kt`. DTOs/Retrofit interfaces exist so
  far only for what Overview needs: `StreamApi`, `LibraryApi`,
  `DashboardAnalyticsApi`, `DashboardEarningsApi`, `AuthApi`.
- **Navigation shell (M3)** — `ui/nav/DashboardApp.kt`: hamburger-triggered
  `ModalNavigationDrawer` + `NavHost` with all 9 destinations
  (`DashboardDestination.kt`). Overview is real; the other 8 render
  `ComingSoonScreen`.
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

**Not yet tested on a real device or against a real backend** — this
sandbox has no camera and no network path to a live `paperweightv1`
instance, so none of the pairing flow, the network layer, or Overview's
data rendering has been exercised end-to-end. The user was about to do that
testing when this session ended. Check with them for results before
assuming any of it works as designed — especially:
1. Gradle dependency resolution for the libraries added this session
   (CameraX, ML Kit barcode-scanning, Retrofit, OkHttp,
   kotlinx.serialization + its Retrofit converter, androidx.security-crypto,
   material-icons-extended) — this sandbox can't reach `dl.google.com`, so
   this has never actually been resolved, only reasoned about.
2. A handful of Material icon names used without the ability to verify them
   against the real `material-icons-extended` artifact:
   `Icons.Outlined.{Dashboard,Radio,Schedule,Lock,Public,People,BarChart,
   Payments,Settings,Headphones,LibraryMusic,AccountBalanceWallet}`. If any
   don't resolve, it's a quick swap for a same-purpose icon that does.
3. The QR redeem flow itself (parsing `pairUrl`, POSTing
   `/api/auth/dashboard/device/redeem`, capturing `Set-Cookie`) — zero way
   to exercise this from a sandbox with no camera.
4. `LibraryStructure`/`LibraryTrack` DTOs in
   `network/models/LibraryModels.kt` — the field shape was pulled directly
   from `paperweightv1/src/api/library.js`'s `formatItem()`, which is
   accurate, but the DTOs haven't been round-tripped against real JSON.

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

## What's left: Milestones 4 (screens 2-9) and 5 (hardening)

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
| **Broadcast** | `views/Broadcast.tsx` | `stream/status`, `dashboard/broadcast/queue` (5s poll), `dashboard/broadcast/mode`\|`restart`, `dashboard/live/status`\|`start`\|`chunk`\|`stop` | "Go live" (mic) needs `AudioRecord` → PCM chunk POST to `/api/dashboard/live/chunk` — highest-complexity item in the whole remaining scope. Treat as this screen's stretch goal; mode switch/restart/queue management are standard CRUD and should ship regardless. |
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
│   ├── DashboardAnalyticsApi.kt / DashboardEarningsApi.kt
│   ├── SessionStore.kt / SessionCookieJar.kt / DynamicBaseUrlInterceptor.kt
│   └── models/                     // DTOs, grouped like studio's api.js
└── ui/
    ├── theme/                      // Color/Type/Shape/Theme.kt — done (M0)
    ├── nav/                        // DashboardApp, DashboardDestination, ComingSoonScreen — done (M3)
    ├── components/                 // ViewHeader, MetricTile, PanelCard, EmptyStateView, ScreenState(Scaffold) — done (M3)
    └── dashboard/
        └── overview/                // done (M4, 1/9) — reference pattern for the rest
            ├── OverviewScreen.kt
            ├── OverviewUiState.kt
            └── OverviewViewModel.kt
            // broadcast/, schedule/, vault/, station/, audience/,
            // analytics/, earnings/, settings/ — not yet created
```

## Verification

Same as the original plan: `./gradlew assembleDebug` needs an environment
with real network access (this sandbox can't reach `dl.google.com`) —
Android Studio or CI. Unit-test repository/ViewModel logic against
`MockWebServer` using the JSON shapes documented in `api.js`. Real
end-to-end testing requires `npm run dev` in `paperweightv1` on the same
Wi-Fi as the A12, paired via a QR from a browser logged into that dev
Studio instance.
