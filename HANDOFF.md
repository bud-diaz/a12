# Handoff: Paperweight OS on-device-backend pivot

Written as the running handoff for the Paperweight OS Android build-out.
**The project pivoted from a thin remote client to an on-device backend.**
Everything below the "Latest validation update" section describes that
pivot's Phase 0 (of 12). This file is self-contained — a new session
shouldn't need anything from a previous session's local state to pick this
up, only this repo.

## Read first

- `/home/user/a12/CLAUDE.md` — project constraints. Note: CLAUDE.md's
  "Station pairing flow" section still describes the *old* architecture
  (pair against an existing Paperweight Studio dashboard). That description
  is superseded by this pivot but CLAUDE.md itself hasn't been rewritten yet
  — treat this HANDOFF.md and the plan file referenced below as the current
  source of truth for the architecture until CLAUDE.md is updated.
- The approved plan for this pivot: `docs/ON_DEVICE_BACKEND_PLAN.md` — has
  the full scope table, technical decisions, package layout, file fates,
  and the 12-phase build order. Read it before starting any phase.
- `paperweightv1`'s `studio/src/lib/api.js` and server route handlers are
  still the literal source of truth for the *domain shapes* kept in
  `network/models/*.kt` (see "Key decisions" below on why those files are
  still around despite the pivot), and will be the source of truth for the
  frp registration contract needed in the reachability phase.

## What changed and why (read this before anything else)

The app was a thin client: QR-pair against an existing Paperweight Studio
web dashboard, then hit that remote Express backend over Retrofit for all
nine dashboard screens. The user redirected the project: the Galaxy A12
itself becomes the backend. No pairing, no remote server for the core
product (vault scanning, continuous HLS "radio" broadcast, a listener web
player, and full local read/write dashboard control). Payments/tips are
deferred entirely. Public reachability will use frp (mirroring
paperweightv1's own Cloudflare→frp swap), not DDNS. The device also now
requires a removable SD card (2GB+) as a hard boot gate, uses it as the
default vault + backup storage location, and needs a periodic local backup
system targeting that same card (recovery insurance for a legitimate
re-provisioning after a factory reset — not an FRP bypass; the user was
explicit they aren't attempting that).

Full rationale, scope table (what's kept/dropped from the existing
Retrofit-backed screens), and the 12-phase build order live in the plan
file referenced above. Don't re-derive these decisions from scratch —
read the plan first.

## Latest validation update

**Phase 0 (Groundwork) is code-complete but NOT build-verified.** This
session's environment (a cloud/remote sandbox, not the local machine the
previous pairing-era validation ran on) has no Android SDK installed and
its outbound proxy blocks `dl.google.com` (`CONNECT tunnel failed, response
403`), which hosts the Android Gradle Plugin and Android SDK artifacts —
confirmed via `curl -sS -o /dev/null -w "%{http_code}" https://dl.google.com/`
returning `000`/403 through the proxy. `./gradlew :app:compileDebugKotlin`
fails immediately at plugin resolution, before any of this session's Kotlin
is even parsed:

```
Plugin [id: 'com.android.application', version: '8.6.0', apply: false] was not found...
Searched in: Google, MavenRepo, Gradle Central Plugin Repository
```

This is an environment limitation, not a code issue — do not waste time
re-debugging plugin resolution here; it needs a session with real Google
Maven access (a local machine, per the "Verification" section's documented
`JAVA_HOME`/`ANDROID_HOME` setup, or a cloud environment with a different
network policy).

In lieu of a real compile, this session did a thorough manual review:
- Broad `grep` across the entire `com.paperweight.os` package tree for any
  remaining reference to every deleted symbol (`PairingActivity`,
  `SessionStore`, `SessionCookieJar`, `DynamicBaseUrlInterceptor`,
  `network.ApiClient`, `network.AuthApi`, every deleted `*Api.kt`,
  `retrofit2.*`, `com.jakewharton.retrofit`, `okhttp3.logging`) — zero
  matches.
- For each of the 9 dashboard screens, `grep`'d the `Screen.kt` file for
  every `viewModel.<method>`/`viewModel::<method>` reference and confirmed
  the corresponding stubbed `ViewModel.kt` still declares that exact method
  signature (Kotlin type-checks the `content = { data -> ... }` lambda
  inside `ScreenStateScaffold` at compile time even though it never
  executes while `state` is `ScreenState.Error`, so a missing method there
  would still break the build).
- Confirmed every `network.models.*` type referenced by a stub `ViewModel`'s
  surviving method signatures (`VaultProject`, `Poll`, `ExternalSearchItem`,
  `TipConfig`, `ScheduleBlockRequest`, `SmartPlaylistRequest`,
  `UpdateCollectionRequest`, `VaultPricingRequest`) is still declared in the
  kept `network/models/*.kt` files.

**This must be re-verified with a real build the moment a session has
Android SDK + Google Maven access** — treat Phase 0 as "should compile,
unconfirmed" until then, not "done."

## Status: what's built (Phase 0)

- **`pairing/` package deleted entirely** — `PairingActivity`,
  `PairingViewModel`, `PairingScreen`, `QrCodeAnalyzer`. No more QR
  scan-to-pair flow.
- **Remote transport layer deleted**: `network/ApiClient.kt`,
  `SessionStore.kt`, `SessionCookieJar.kt`, `DynamicBaseUrlInterceptor.kt`,
  `AuthApi.kt`, and every `Dashboard*Api.kt`/`StreamApi.kt`/`LibraryApi.kt`
  Retrofit interface. `network/models/AuthModels.kt` (pairing-redeem DTOs,
  unused elsewhere) also deleted.
- **`network/models/*.kt` DTOs deliberately KEPT for now** — see "Key
  decisions" below, this is a documented deviation from the plan's literal
  file-fate list.
- **`MainActivity.kt` rewired**: boot chain is now Device Owner claim →
  SD card present/sized → `DashboardApp()`. No pairing branch. The SD-card
  check is a live `Flow` (`SdCardMountState.observe`), so pulling the card
  mid-session drops back to the gate screen instead of crashing.
- **New `storage/` package**: `SdCardDetector.kt` (enumerates volumes via
  `ContextCompat.getExternalFilesDirs`, finds the removable one, checks
  capacity via `StatFs` against a 2GB minimum) and `SdCardMountState.kt`
  (a `callbackFlow` wrapping `ACTION_MEDIA_MOUNTED`/`EJECT`/`REMOVED`/
  `UNMOUNTED`/`BAD_REMOVAL` broadcasts).
- **New `ui/setup/SdCardRequiredScreen.kt`** — the blocking gate screen
  shown when no valid card is present; clears itself automatically once a
  live Flow update reports a valid card (no manual "recheck" button needed).
- **`admin/DeviceOwnerPolicy.kt`**: removed the `CAMERA` permission grant
  (was there only for QR scanning). Left a comment marking where
  `RECORD_AUDIO`/`POST_NOTIFICATIONS` silent grants will land once the
  broadcast engine's foreground service is built and those permissions are
  actually declared.
- **`AndroidManifest.xml`**: removed `CAMERA` permission + `camera.any`
  feature + the `PairingActivity` declaration. Deliberately did NOT yet add
  `RECORD_AUDIO`/`FOREGROUND_SERVICE`/service declarations — those are
  added in the phases that actually implement that functionality, not
  speculatively now.
- **Gradle**: removed `retrofit-core`, `retrofit-kotlinx-serialization-converter`,
  `okhttp-logging-interceptor`, all four `camerax-*` artifacts,
  `mlkit-barcode-scanning`. Kept `okhttp-core` (slim, for the future frp
  registration call), `kotlinx-serialization-json` (DTOs + future backup
  manifest export), `androidx-security-crypto` (future frp secrets).
  Room/KSP/WorkManager/NanoHTTPD/zxing are **not yet added** — each lands in
  the phase that actually needs it (Room in Phase 1, etc.), not all at once
  in Phase 0.
- **All 9 dashboard ViewModels stubbed** (Overview, Broadcast, Schedule,
  Vault, Station, Audience, Analytics, Earnings, Settings): each now emits
  `ScreenState.Error("<screen> isn't wired to the on-device backend yet...")`
  from `load()`, and every other public method that a `Screen.kt` file
  references is kept with its exact original signature but an empty/no-op
  body. `Screen.kt`/`UiState.kt` files were **not touched** — the
  `ScreenStateScaffold` component only invokes its `content` lambda when
  `state` is `ScreenState.Content`, so the existing built UI never renders
  while stubbed, but the code still compiles against it.

## Key decisions made this session (don't re-litigate without reason)

1. **`network/models/*.kt` DTOs are deliberately kept for now**, despite
   the plan's file-fate table listing them under "Delete." Reason found
   during implementation: `UiState.kt` files for every dashboard screen
   import these types directly (e.g. `BroadcastUiState` uses
   `BroadcastQueueItem`), and `Screen.kt` files pattern-match on those
   `UiState` fields. Deleting the DTOs in Phase 0 would have forced a full
   rewrite of all 9 `UiState.kt`/`Screen.kt` files in this same pass instead
   of each screen's own dedicated phase — a much larger, riskier change than
   "groundwork." Since these are plain `@Serializable data class`es with no
   actual networking/transport code in them, keeping them temporarily isn't
   a real violation of "remove the remote client" — only the transport
   (`ApiClient`, the `*Api.kt` Retrofit interfaces, session/cookie/URL
   plumbing) was actually deleted. Each screen's own phase should delete its
   slice of `network/models/*.kt` usage as it gets rewired onto Room
   entities/local repositories; the package should be fully gone by the
   time Phase 11 (Earnings) finishes.
2. **Stub ViewModels use `ScreenState.Error`, not fabricated `Content`.**
   Considered building plausible empty-but-valid `UiState` instances instead
   (so screens render normally instead of a red error banner), but that
   would silently show fake/empty data as if it were real, which is worse
   than an honest "not wired yet" message with a working "Try again" retry
   button (already provided generically by `ScreenStateScaffold`).
3. **Manual code review substitutes for a real build in this session** —
   see "Latest validation update." Do not report Phase 0 as "verified" to
   the user or in any future HANDOFF entry until a real `compileDebugKotlin`
   has actually run successfully.
4. **Manifest/permission additions are added per-phase, not front-loaded.**
   `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`,
   `POST_NOTIFICATIONS`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`, and the
   `BroadcastService` declaration all belong to later phases (mic capture,
   the foreground service itself) — adding them speculatively now with no
   corresponding implementation was judged worse than adding them exactly
   when needed.
5. Carried forward from the pre-pivot sessions (still relevant): kiosk/
   Device-Owner mechanics in `admin/`, `provisioning/SetupActivity.kt`, and
   `provisioning/setup.sh` are untouched and still valid — this pivot only
   changes what happens *after* Device Owner is claimed, not the claiming
   flow itself.

## What's left

Phases 1–12 per the plan file. Immediate next steps in order:
1. **Re-verify Phase 0 with a real build** the moment Android SDK + Google
   Maven access is available (see "Verification" below) — this is the
   single highest-priority item, ahead of starting Phase 1.
2. **Phase 1 — Local data layer**: Room DB (add KSP + Room deps), entities/
   DAOs per the plan's package layout, `AppPreferences`, repositories,
   `ServiceLocator`. Use a fixed, known DB filename (Phase 3's
   `RestoreManager` depends on this).
3. **Phase 2 — Vault ingestion**: SAF picker (one-time SD-card tree grant),
   metadata extraction, `VaultFileStore` writing into `Paperweight/vault/`
   on the card.
4. **Phase 3 — Backup & recovery**: see plan decision #12.
5. Phases 4–12 as detailed in the plan file.

Also still open, carried in the plan itself: the frp registration contract
(Phase 9) needs to be read directly from `paperweightv1` in a local session
where that repo is available — don't guess it.

## Repo layout as of this handoff

```
app/src/main/java/com/paperweight/os/
├── MainActivity.kt                 // Device Owner -> SD card gate -> DashboardApp (no pairing)
├── admin/                          // unchanged except CAMERA grant removed
│   ├── BootReceiver.kt
│   ├── DeviceOwnerPolicy.kt
│   └── PaperweightDeviceAdminReceiver.kt
├── provisioning/SetupActivity.kt   // unchanged, still the Device Owner claim flow
├── storage/                        // NEW (Phase 0)
│   ├── SdCardDetector.kt
│   └── SdCardMountState.kt
├── network/models/                 // KEPT (see Key decisions #1), transport layer deleted
│   ├── AudienceModels.kt / BroadcastModels.kt / DashboardAnalyticsModels.kt
│   ├── DashboardEarningsModels.kt / LibraryModels.kt / ScheduleModels.kt
│   ├── SettingsModels.kt / StationModels.kt / StreamModels.kt / VaultModels.kt
└── ui/
    ├── theme/                      // untouched
    ├── nav/                        // untouched
    ├── components/                 // untouched
    ├── setup/SdCardRequiredScreen.kt  // NEW (Phase 0)
    └── dashboard/                  // all 9 screens: Screen.kt/UiState.kt untouched,
                                     //   ViewModel.kt stubbed to ScreenState.Error
        ├── overview/ broadcast/ schedule/ vault/ station/
        └── audience/ analytics/ earnings/ settings/
```

`pairing/` and the transport half of `network/` no longer exist.

## Verification

**This session could not run a real build** — see "Latest validation
update" for why. The next session with real tooling access should run:

```bash
export JAVA_HOME="$HOME/.local/jdks/jdk-17"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# First, confirm dl.google.com is actually reachable in that environment —
# this session's proxy blocked it (403); don't waste time debugging
# "broken" Kotlin before ruling out an environment/network problem again.
curl -sS -o /dev/null -w "%{http_code}\n" https://dl.google.com/

./gradlew :app:compileDebugKotlin
./gradlew assembleDebug
```

If `compileDebugKotlin` fails on anything other than a plugin/dependency
resolution error, that's a real bug in this session's Phase 0 changes —
fix it before starting Phase 1. If it succeeds, update this section (and
the "Latest validation update" section above it) with the real result
before moving on.

Once it compiles: install on a real Galaxy A12
(`adb install -r app/build/outputs/apk/debug/app-debug.apk`), boot with no
SD card inserted and confirm the "SD card required" screen appears, then
insert a 2GB+ card and confirm it clears automatically into the dashboard
(which will show every screen's "not wired to the on-device backend yet"
error state with a working "Try again" button — that's the expected Phase 0
end state, not a bug).
