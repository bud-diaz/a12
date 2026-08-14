# Handoff: Paperweight OS build-out

Written as the running handoff for the Paperweight OS Android build-out.
**All 9 Milestone 4 dashboard screens are now implemented**: Overview,
Broadcast, Schedule, Vault, Station, Audience, Analytics, Earnings, and
Settings. This file is self-contained — a new session shouldn't need
anything from the previous session's local state to pick this up, only this
repo and `paperweightv1`.

## Read first

- `/home/bud/a12/CLAUDE.md` — project constraints and v1 scope, kept current
  through this session (rewritten once already, see "Key decisions" below).
- `paperweightv1`'s `studio/src/views/*.tsx` and `studio/src/lib/api.js` —
  the literal source of truth for what each screen does and which endpoints
  it calls. Not a mockup, not a guess — read the real files when in doubt.

## ✅ Latest validation update

The previous handoff's main blocker has been cleared: the all-9-screen build
now compiles and installs in this environment against a real connected Galaxy
A12.

Validated on latest `main` (`f4135db`, merged PR #2):

```text
./gradlew :app:compileDebugKotlin
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest      # NO-SOURCE, but task succeeds
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Observed results:

```text
compileDebugKotlin: BUILD SUCCESSFUL
assembleDebug: BUILD SUCCESSFUL, app-debug.apk 88M
connectedDebugAndroidTest: BUILD SUCCESSFUL
adb install -r: Success
```

Device state after install/HOME recovery:

```text
device: R58RB5AYA7L
model: SM-A125U
api: 30
package: com.paperweight.os
versionName: 1.0
lastUpdateTime: 2026-08-14 12:31:32
foreground: com.paperweight.os/.pairing.PairingActivity
lock-task: LOCKED after HOME
camera permission: granted=true, POLICY_FIXED
crash buffer: no com.paperweight.os FATAL EXCEPTION / AndroidRuntime entries
```

Only compiler warnings were deprecation notices for `Icons.Outlined.TrendingUp`
and `Icons.Outlined.Send` recommending the AutoMirrored variants. They are not
validation blockers.

Important boundary: this is build/install/kiosk validation, **not** live
backend validation. The A12 is still at QR pairing, so the 9 dashboard screens
have not yet been exercised against a paired Paperweight station.

## Status: what's built and what has been validated

- **Gradle project scaffold** — builds and installs on a real Galaxy A12.
  Latest verified on connected `SM-A125U` / API 30 with `adb install -r`.
  *(Verified in an earlier session with a working local Android SDK.)*
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
- **Core networking (M2)** — `network/ApiClient.kt` (Retrofit + OkHttp with
  a `DynamicBaseUrlInterceptor` since the base URL is only known after
  pairing), `SessionCookieJar.kt`. Every dashboard area now has its own
  Retrofit interface + models file (see repo layout below) wired into
  `ApiClient`.
- **Navigation shell (M3)** — `ui/nav/DashboardApp.kt`: hamburger-triggered
  `ModalNavigationDrawer` + `NavHost` with all 9 destinations
  (`DashboardDestination.kt`). **All 9 now route to real screens** —
  `ComingSoonScreen` is gone, nothing references it anymore (deleted).
- **Shared Compose primitives (M3+)** — `ui/components/`: `ViewHeader`,
  `MetricTile`, `PanelCard`, `EmptyStateView`, `ScreenState`/
  `ScreenStateScaffold`, and (new this session) `DropdownField` — a shared
  label+tap-menu select used by every screen with a small fixed choice set,
  promoted out of Schedule once Audience needed the same pattern.
- **Overview (M4, compiled+device-verified)** — `ui/dashboard/overview/`:
  polls `stream/status` every 5s, loads library/analytics/earnings once per
  visit. Reference pattern for every screen since.
- **Broadcast (M4, compiled+device-verified)** — `ui/dashboard/broadcast/`:
  rotation mode, restart, queue poll/removal. Live mic streaming
  (`AudioRecord` → `/api/dashboard/live/chunk`) is still an open stretch
  item, not attempted this session (explicitly out of scope for this pass).
- **Schedule (M4, compiled; live-backend unverified)** — `ui/dashboard/schedule/`: blocks +
  smart playlists CRUD (inline forms, no modal system), "next 24h" preview
  panel, "enable scheduled mode" action. Real endpoints are `/api/schedule*`,
  not `/api/dashboard/schedule*` — don't assume the `dashboard/` prefix
  pattern holds here. Block/playlist mutations are desktop-platform gated
  (403 possible) — surfaced via the server's real error message, not a
  generic failure. No polling — Studio's three schedule queries have no
  `refetchInterval`, unlike Overview/Broadcast.
- **Analytics (M4, compiled; live-backend unverified)** — `ui/dashboard/analytics/`: reuses the
  pre-existing `DashboardAnalyticsApi` in full (no new Retrofit interface).
  Only the `live` stats poll (10s, matching Studio's own interval — not the
  5s used elsewhere). Canvas bar chart for 30-day history, same technique as
  Overview's `WeekHistoryChart`. All-time "most played" is computed
  client-side by joining `library/structure` with `analytics/playcounts`,
  same as Studio does.
- **Earnings (M4, compiled; live-backend unverified)** — `ui/dashboard/earnings/`: reuses the
  pre-existing `DashboardEarningsApi` in full. Revenue hero + mix bar, tip
  presets editor (mirrors `TipConfigModal.tsx`: 3 dollar presets + a
  custom-amount toggle), top earners. "Payment settings" button points at
  the separately-built Settings screen (no shared modal system to reuse).
- **Audience (M4, compiled; live-backend unverified)** — `ui/dashboard/audience/`: today's
  insights, audience-memory search/segments/people (live-updates via
  `LaunchedEffect`, no debounce — matches Studio's own per-keystroke
  refetch), marketing contacts, automations (pause/rule enable+mode/sweep/
  send), participation (poll create/open-close, request accept/decline),
  radio-host toggle (desktop-gated — a 403 on the initial load degrades
  gracefully to a "locked" status rather than failing the whole screen,
  since react-query treats each query independently and this port should
  too for a known-gated endpoint), external catalog search + import. Rule
  enable/mode updates are two *separate* single-field request DTOs
  (`UpdateRuleEnabledRequest`/`UpdateRuleModeRequest`), not one dual-nullable
  DTO — the server's automations route uses `input.mode !== undefined`
  (strict-presence, not nullish-coalescing) so an explicit JSON `null` for
  the field you're *not* touching would be read as "clear this field," not
  "leave it alone." Worth remembering if more partial-update endpoints show
  up in Vault/Station-style screens later.
- **Station (M4, compiled; live-backend unverified)** — `ui/dashboard/station/`: public URL,
  Cloudflare tunnel (token/zone/hostname, connect/disconnect, 5s tunnel-
  status poll only when a tunnel is actually configured/managed), directory
  searchability (with the health recheck Studio triggers on failure),
  telemetry secret, paperweighthq address, setup-progress checklist +
  signup prompt. The itemized "which readiness check failed" list from
  Studio's `searchable` error handling was *not* ported (would need parsing
  a `checks` map out of a raw HTTP error body) — only the top-level error
  message and the health recheck were kept. `milestones` values are
  `occurred_at` timestamp strings (presence = reached), not booleans —
  confirmed from the server route, not assumed from the JSDoc (which was
  stale/wrong on this point).
- **Vault (M4, compiled; live-backend unverified, largest screen)** — `ui/dashboard/vault/`:
  track + collection pricing (inline forms replacing `TrackPriceModal`/
  `ProjectPriceModal`), collection track add/remove/reorder (up/down arrows,
  computed reordered id list like Studio's `moveTrack`), artwork upload via
  real `okhttp3.MultipartBody` (picked via
  `rememberLauncherForActivityResult(GetContent())`, read off the main
  thread), highlight toggle, access tokens (create+auto-assign, revoke,
  tier change, per-token assignment management as an independently-fetched
  side panel). "Access control" and "Add to vault" (new media upload) open
  Studio modals with no Android equivalent screen — both stubbed as a
  notice rather than invented. Collection delete uses an inline "Delete
  this collection? Yes/No" confirm instead of `window.confirm`.
- **Settings (M4, compiled; live-backend unverified)** — `ui/dashboard/settings/`: workspace
  motion toggle (genuinely local/ephemeral, matches Studio's own
  unpersisted `useState`), notifications (webhook + go-live toggle), RSS
  feed (enable + scope), track glow color (hex text field + swatch preview
  — no native color-picker widget, a deliberate scope trim), listener
  account recovery (email → reset link → copy), docs viewer (list + inline
  content panel instead of a modal; `/api/docs/{id}` returns raw
  text/Markdown, not JSON, so it's fetched as `okhttp3.ResponseBody` and
  decoded manually off the main thread — not run through the shared
  kotlinx.serialization converter). **`DesktopSection` intentionally
  dropped entirely** — confirmed decision #6 below, no Electron-equivalent
  bridge exists or should be invented on Android.

## Validation status and remaining gaps

The full local Android validation baseline now passes against all 9 screens:

```text
./gradlew :app:compileDebugKotlin     # BUILD SUCCESSFUL
./gradlew assembleDebug               # BUILD SUCCESSFUL, APK 88M
./gradlew :app:testDebugUnitTest      # BUILD SUCCESSFUL / NO-SOURCE
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Latest physical-device facts observed:

```text
device: R58RB5AYA7L
model: SM-A125U
api: 30
package: com.paperweight.os
versionName: 1.0
lastUpdateTime: 2026-08-14 12:31:32
foreground: pairing.PairingActivity
kiosk: lock-task returns to LOCKED after HOME
camera permission: granted=true, POLICY_FIXED
crash buffer: no com.paperweight.os FATAL EXCEPTION / AndroidRuntime entries
```

Still not exercised against a real paired backend: the QR redeem flow and every
screen's real data rendering/mutations need `npm run dev` (or a deployed
Paperweight station) on the same Wi-Fi as the A12, with a browser logged into
Studio showing the pairing QR. Do not claim backend end-to-end success for any
screen until that pairing has happened and the screens hit real endpoints.

Known caution points:
1. The QR redeem flow itself has still not been live-paired in any session.
2. `LibraryStructure`/`LibraryTrack` DTOs were pulled from
   `paperweightv1/src/api/library.js`'s `formatItem()` and still need a live
   JSON round-trip.
3. `BroadcastQueueItem` intentionally accepts both `id` and legacy `mediaId`
   because `views/Broadcast.tsx` typed `mediaId`, while the current Express
   endpoint returns `id`.
4. Every new DTO this session was written by reading the real `paperweightv1`
   server route handlers. Compile/build now proves the Kotlin is valid, but it
   does not prove the DTO field shapes against a live server response.

## Key decisions made this session (don't re-litigate these without reason)

Carried forward from the previous session (still valid, see a12's
CLAUDE.md for the durable version):

1. **Not the old "Mission Control" mockup** — the real target is the live
   Creator Studio dashboard (`studio/src/AppShell.tsx`).
2. **Scope is 9 screens**: Overview, Broadcast, Schedule, Vault, Station,
   Audience, Analytics, Earnings, and Settings — all 9 are now built.
   Deferred: Activity, Releases, Profile, Tools, Security, Stack/Player.
3. **Full read+write control**, not a read-only monitor.
4. **Auth is QR pairing** — no WebView, direct JSON POST + `Set-Cookie`.
5. **Design tokens match the real Studio app** — lime/coral/red/near-black,
   Manrope/Space Grotesk/DM Mono.
6. **`window.desktopAPI`-gated Settings sections don't port** —
   `DesktopSection` omitted entirely, now actually implemented as omitted
   (Settings screen shipped this session without it).

New this session:

7. **The original no-compiler blocker is resolved.** The follow-up validation
   session ran `compileDebugKotlin`, `assembleDebug`, unit-test task,
   connected-test task, and `adb install -r` successfully against latest
   `main` with all 9 screens present. Keep future status language at
   "compiled/build-installed/kiosk-smoked, live-backend unverified" until
   QR pairing + endpoint smoke tests are done.
8. **`DropdownField` promoted to a shared component** the moment a second
   screen (Audience) needed the same label+tap-menu select Schedule had
   built privately — follow this same "wait for the second occurrence,
   then promote" discipline for any other pattern that repeats across the
   remaining Milestone 5 work.
9. **Desktop-platform-gated endpoints (403) get their error message
    surfaced, not swallowed into a generic failure** — first needed for
    Schedule's block/playlist mutations, then reused for Audience's
    radio-host/external-import and Vault/Station's various mutations. If
    this pattern shows up a third+ time in Milestone 5, it's a good
    candidate for promotion into a shared `HttpException` extension
    (currently duplicated per-ViewModel, matching the established
    one-file-per-screen self-containment convention — see CLAUDE.md
    "Working conventions").
10. **No client-side modal system exists**, so every screen that opens a
    Studio "modal" (`TrackPriceModal`, `ProjectPriceModal`, docs viewer,
    uninstall-confirm, etc.) got ported as an inline expandable
    `PanelCard` instead — consistent across Schedule/Vault/Settings. Two
    modals that don't correspond to a *built* Android screen or feature
    (Vault's "Access control"/"Add to vault", both gated behind features
    this pass didn't implement) were stubbed as a one-line notice via each
    ViewModel's `notify()` rather than inventing new screens beyond
    HANDOFF's stated scope.

## What's left: Milestone 5 (hardening)

With all 9 screens now built and the full baseline compiling/installing, this is next:
- **Live-backend validation**: pair the A12 to a dev or deployed Paperweight
  station and smoke every screen's load path plus at least one safe mutation
  per screen where possible.
- **Session-loss handling**: a 401 from any call should route back to
  `PairingActivity` (clear `SessionStore`, `startActivity` + `finish`), not
  crash or silently retry. Confirmed via full-tree search this session:
  **still not implemented anywhere** — zero matches for `401` or
  `HttpException`-based session handling outside the per-screen
  desktop-gate (403) handling added this session. The natural place is
  likely a shared OkHttp `Authenticator`/`Interceptor` in `ApiClient.kt`
  (one place) rather than duplicating a 401 check across all 9
  ViewModels — this is a good candidate for the "third occurrence,
  promote to shared" rule mentioned above, except session-loss handling
  needs to happen exactly once per app regardless of occurrence count.
- **Sweep every screen's ViewModel** for consistent `ScreenState`/
  `ScreenStateScaffold` usage — now that there are 9 examples instead of
  2, it's worth double-checking Schedule/Analytics/Earnings' "no poll, one-
  shot load" screens against Overview/Broadcast/Station's "some fields
  poll" screens for any drift, now that both patterns are established.
- **Re-confirm "no retry queue, no local cache fallback, no crash"** holds
  on all 9 screens against real network responses, not just compile-time UI
  construction.
- **Broadcast's live-mic stretch** (`AudioRecord` → `/api/dashboard/live/
  chunk`) — still open, not attempted this or the previous session.

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
├── provisioning/SetupActivity.kt   // one-time device-owner claim flow
├── network/                        // M2, one Api interface + models file per area
│   ├── ApiClient.kt                // every *Api interface wired in here
│   ├── AuthApi.kt / StreamApi.kt / LibraryApi.kt
│   ├── DashboardAnalyticsApi.kt / DashboardEarningsApi.kt / DashboardBroadcastApi.kt
│   ├── DashboardScheduleApi.kt / DashboardAudienceApi.kt / DashboardStationApi.kt
│   ├── DashboardVaultApi.kt / DashboardSettingsApi.kt
│   ├── SessionStore.kt / SessionCookieJar.kt / DynamicBaseUrlInterceptor.kt
│   └── models/                     // DTOs, grouped like studio's api.js
└── ui/
    ├── theme/                      // Color/Type/Shape/Theme.kt — done (M0)
    ├── nav/                        // DashboardApp, DashboardDestination — done (M3), all 9 wired
    ├── components/                 // ViewHeader, MetricTile, PanelCard, EmptyStateView,
    │                                //   ScreenState(Scaffold), DropdownField — done
    └── dashboard/
        ├── overview/                // done (M4), compiled+device-verified — reference pattern
        ├── broadcast/               // done for rotation/queue, compiled+device-verified; live mic is stretch
        ├── schedule/                // done (M4), compiles; live backend unverified
        ├── analytics/                // done (M4), compiles; live backend unverified
        ├── earnings/                 // done (M4), compiles; live backend unverified
        ├── audience/                 // done (M4), compiles; live backend unverified
        ├── station/                  // done (M4), compiles; live backend unverified
        ├── vault/                    // done (M4), compiles; live backend unverified, largest
        └── settings/                 // done (M4), compiles; live backend unverified
```

## Verification

**First**, confirm the build environment can actually reach
`dl.google.com` (`curl -sS -o /dev/null -w "%{http_code}\n" https://dl.google.com/`
should return `200`, not a proxy error) — do not waste time debugging
"broken" Kotlin code before ruling out an environment/network problem.

Then the standard baseline:

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

Fix whatever `compileDebugKotlin` surfaces first — that's the highest-value
next step in this entire project right now, ahead of any new feature work.

After a clean compile, install and navigate to each of the 7 new
destinations via the drawer to confirm they render without crashing before
attempting a live-backend pairing session (`npm run dev` in `paperweightv1`
on the same Wi-Fi as the A12, then "Pair a new device" from Studio).
