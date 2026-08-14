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

**Phase 0 (Groundwork) is build-verified on this machine.** Current session
confirmed Google Maven reachability (`curl -sS -o /dev/null -w "%{http_code}" https://dl.google.com/`
returned `302`), installed/used Temurin JDK 17 at `/home/bud/.local/jdks/jdk-17`,
and used the existing Android SDK at `/home/bud/Android/Sdk`.

Real build results:

```bash
export JAVA_HOME=/home/bud/.local/jdks/jdk-17
export ANDROID_HOME=/home/bud/Android/Sdk
export ANDROID_SDK_ROOT=/home/bud/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

./gradlew :app:compileDebugKotlin   # BUILD SUCCESSFUL in 40s
./gradlew assembleDebug             # BUILD SUCCESSFUL in 2m 3s
```

`compileDebugKotlin` produced only existing deprecation warnings for
`Icons.Outlined.TrendingUp` and `Icons.Outlined.Send`; no Kotlin compile
errors. `assembleDebug` produced `app/build/outputs/apk/debug/app-debug.apk`
(size observed: 62,767,865 bytes) and only warned that
`libandroidx.graphics.path.so` could not be stripped, which Gradle packaged as-is.

On-device smoke result against the connected Galaxy A12:

```bash
adb shell getprop ro.product.model      # SM-A125U
adb shell getprop ro.build.version.release # 11
adb install -r app/build/outputs/apk/debug/app-debug.apk # Success
adb shell am start -n com.paperweight.os/.MainActivity
adb shell uiautomator dump /sdcard/window.xml
```

The device is already Device Owner for
`com.paperweight.os/.admin.PaperweightDeviceAdminReceiver`. With no SD card
present, `MainActivity` launched successfully and the UI hierarchy contained
the expected Phase 0 gate text:

> SD card required
>
> Paperweight OS stores its media vault and backups on a removable SD card,
> not internal storage. Insert a card of at least 2GB to continue — this
> screen clears itself automatically once one is detected.

A tail of `adb logcat` after launch showed no `FATAL EXCEPTION` for
`com.paperweight.os`.

Manual grep checks were also repeated after the build:
- Broad search across `app/src/main/java/com/paperweight/os` for deleted
  pairing/transport symbols (`PairingActivity`, `PairingViewModel`,
  `PairingScreen`, `QrCodeAnalyzer`, `SessionStore`, `SessionCookieJar`,
  `DynamicBaseUrlInterceptor`, `network.ApiClient`, `network.AuthApi`,
  `retrofit2.*`, `com.jakewharton.retrofit`, `okhttp3.logging`) — zero matches.
- Search for deleted API interface names (`Dashboard*Api`, `StreamApi`,
  `LibraryApi`, `AuthApi`) found only one harmless comment in
  `network/models/AudienceModels.kt` referencing `DashboardScheduleApi`.

**Phase 1 (Local data layer) is code-complete and verified on the connected
A12.** Build, install, no-card gate, card formatting/mounting, valid-card
dashboard entry, and Phase 1 data-layer instrumentation tests have all been
exercised. The card used here is a marketed 2GB card that formats to ~1.8GiB
usable / 2,002,780,160 raw bytes, so the app threshold was corrected from
binary 2GiB to decimal 2GB (`2_000_000_000L`) to match the written requirement.

Phase 1 added Room/KSP, a fixed DB filename (`paperweight-os.db`), the v1 local
entities/DAOs, `AppPreferences`, repositories, `ServiceLocator`, an exported
Room schema, and a real instrumented test suite. `Phase1DataLayerInstrumentedTest`
was first run red against missing data-layer classes, then green after
implementation. Final result: 5 tests, 0 failures, 0 errors on `SM-A125U - 11`.

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

## Status: what's built (Phase 1)

- **Room/KSP added**: Gradle now applies KSP, adds Room runtime/ktx/compiler,
  WorkManager, NanoHTTPD, zxing, and androidTest dependencies. WorkManager,
  NanoHTTPD, and zxing are present because the approved Phase 1 plan's Gradle
  change list brings them in with the local data layer baseline, even though
  their feature use lands in later phases.
- **Fixed DB filename**: `AppDatabase.DATABASE_NAME = "paperweight-os.db"`.
  Do not rename casually; Phase 3 `RestoreManager` depends on this known path.
- **New `data/db/entity/` package** with the v1 local tables:
  `VaultTrackEntity`, `VaultCollectionEntity`,
  `VaultCollectionTrackCrossRef`, `VaultHighlightEntity`,
  `ScheduleBlockEntity`, `SmartPlaylistEntity`, `ListenerTokenEntity`,
  `AnalyticsEventEntity`, `AnalyticsDailyRollupEntity`,
  `ListenerSessionEntity`, and `StationProfileEntity`.
- **New DAO package**: `VaultDao`, `ScheduleDao`, `TokenDao`, `AnalyticsDao`,
  and `StationDao`, using `Flow` observers plus Room `@Upsert` writes.
- **New `AppDatabase`** under `data/db/`: Room database version 1,
  `fallbackToDestructiveMigration()` for v1 only, and exported schema at
  `app/schemas/com.paperweight.os.data.db.AppDatabase/1.json`.
- **New `data/prefs/AppPreferences.kt`**: SharedPreferences-backed non-secret
  config flows for station name, server port, backup retention count, and
  backup interval. This is intentionally non-secret only; frp secrets later
  belong behind `androidx.security.crypto` and do not round-trip through
  backups.
- **New repositories**: `VaultRepository`, `ScheduleRepository`,
  `StationRepository`, `TokenRepository`, `AnalyticsRepository`, and a thin
  `BroadcastRepository` composition holder for later broadcast phases.
- **New `di/ServiceLocator.kt`**: composition root for database, preferences,
  and repositories, replacing the old remote-client composition point for
  future rewiring.
- **New instrumented tests**:
  `app/src/androidTest/java/com/paperweight/os/data/Phase1DataLayerInstrumentedTest.kt`
  verifies fixed DB name, Vault/Schedule/Station repository persistence, and
  AppPreferences round-trips against an in-memory Room database on the real A12.

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
3. **Phase 0 now has real build + card-path verification** — see "Latest
   validation update." `compileDebugKotlin`, `assembleDebug`, APK install on
   the real A12, no-SD-card gate, SD-card public formatting/mounting, and
   valid-card dashboard entry all passed.
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
6. **2GB means decimal 2GB, not binary 2GiB.** The real card used for
   validation is marketed as 2GB and reports 2,002,780,160 bytes raw / ~1.8GiB
   usable after Android formats it. The SD-card gate now uses
   `2_000_000_000L`, so a legitimate 2GB card satisfies the requirement.
7. **Phase 1 data layer is intentionally not wired into dashboard screens yet.**
   The Phase 0 ViewModel stubs remain in place; screen-by-screen rewiring starts
   in later phases as each domain gets implemented. Phase 1's deliverable is the
   local persistence/composition foundation and tests, not visible UI data.

## What's left

Phases 2–12 per the plan file. Immediate next steps in order:
1. **Phase 2 — Vault ingestion**: SAF picker (one-time SD-card tree grant),
   metadata extraction, `VaultFileStore` writing into `Paperweight/vault/`
   on the card, and Vault screen's "Add to vault" path.
2. **Phase 3 — Backup & recovery**: see plan decision #12.
3. **Phase 4 — Broadcast engine core**: decode/encode/segment/playlist
   pipeline, `BroadcastEngine`, `BroadcastService`, Overview/Broadcast rewiring.
4. Phases 5–12 as detailed in the plan file.

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
├── data/                           // NEW (Phase 1)
│   ├── db/AppDatabase.kt            // Room DB, fixed name paperweight-os.db
│   ├── db/entity/                   // Vault, schedule, token, analytics, station tables
│   ├── dao/                         // VaultDao, ScheduleDao, TokenDao, AnalyticsDao, StationDao
│   ├── prefs/AppPreferences.kt      // non-secret device/server/backup config
│   └── repository/                  // local repository facades
├── di/ServiceLocator.kt             // NEW (Phase 1), local composition root
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

Phase 0 build verification now passes in this environment using:

```bash
export JAVA_HOME=/home/bud/.local/jdks/jdk-17
export ANDROID_HOME=/home/bud/Android/Sdk
export ANDROID_SDK_ROOT=/home/bud/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

curl -sS -o /dev/null -w "%{http_code}\n" https://dl.google.com/ # 302
./gradlew :app:compileDebugKotlin # BUILD SUCCESSFUL
./gradlew assembleDebug           # BUILD SUCCESSFUL
```

The assembled APK installed successfully on the connected real Galaxy A12
(`SM-A125U`, Android 11), which is already Device Owner for this app:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk # Success
adb shell am start -n com.paperweight.os/.MainActivity
adb shell uiautomator dump /sdcard/window.xml
```

With no SD card present, the dumped UI hierarchy showed the expected blocking
"SD card required" screen, and post-launch logcat did not show a
`com.paperweight.os` fatal exception.

Additional no-card validation after Bud physically removed the SD card
(2026-08-14 14:47 CDT):

```bash
adb shell 'sm list-volumes all; df -h /storage/* 2>/dev/null || true'
# private mounted null
# emulated;0 mounted null
# /storage only showed emulated storage; no removable SD volume was mounted.

adb shell am start -n com.paperweight.os/.MainActivity
adb shell dumpsys activity activities | grep -E 'ResumedActivity|mLockTaskModeState|mLockTaskAuth'
# MainActivity resumed, mLockTaskAuth=LOCK_TASK_AUTH_WHITELISTED, mLockTaskModeState=LOCKED

adb shell uiautomator dump /sdcard/window.xml
# Visible text included "SD card required" and "Insert a card of at least 2GB"
```

Post-test logcat still showed no `FATAL EXCEPTION` for `com.paperweight.os`.

Card-reinsert validation attempt after Bud reinserted the SD card
(2026-08-14 14:52 CDT):

```bash
adb shell 'sm list-volumes all; sm list-disks; df -h /storage/* 2>/dev/null || true'
# private mounted null
# emulated;0 mounted null
# disk:179,96
# /storage still only showed emulated storage; no removable public volume mounted.

adb shell 'ls -la /dev/block | grep mmcblk1'
# mmcblk1, mmcblk1p1, and mmcblk1p2 block devices existed, so hardware/card
# insertion was visible below Android's storage layer, but vold/StorageManager
# did not expose a mounted removable volume.
```

Bud approved wiping/repartitioning the SD card as public removable storage.
After:

```bash
adb shell sm partition disk:179,96 public
adb shell 'sm list-volumes all; df -h /storage/* 2>/dev/null || true'
# public:179,97 mounted ED4F-17F7
# /dev/fuse 1.8G 864K 1.8G 1% /storage/ED4F-17F7
```

This proved the physical card and Android storage path are usable. The card is
a marketed 2GB card, but Android reports 2,002,780,160 raw bytes / ~1.8GiB
usable after formatting. Because the requirement says "2GB+" in normal
storage-card terms, `SdCardDetector.MIN_CAPACITY_BYTES` was corrected from
binary 2GiB (`2L * 1024 * 1024 * 1024`) to decimal 2GB (`2_000_000_000L`).
After rebuilding and reinstalling:

```bash
./gradlew :app:compileDebugKotlin assembleDebug # BUILD SUCCESSFUL
adb install -r app/build/outputs/apk/debug/app-debug.apk # Success
adb shell am start -n com.paperweight.os/.MainActivity
adb shell uiautomator dump /sdcard/window.xml
# Visible text included:
# Overview isn't wired to the on-device backend yet — coming in a later build phase.
# Try again
# Open navigation menu
# Overview
# and did NOT include "SD card required".
```

Final Phase 0 validation state: build passes, APK installs, no-card gate works,
physical SD card can be formatted/mounted as public removable storage, and the
valid-card path clears into the expected stubbed dashboard error state.

Phase 1 validation:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperweight.os.data.Phase1DataLayerInstrumentedTest
# Starting 5 tests on SM-A125U - 11
# Finished 5 tests on SM-A125U - 11
# BUILD SUCCESSFUL
```

Test report:
`app/build/outputs/androidTest-results/connected/debug/TEST-SM-A125U - 11-_app-.xml`
reported `tests="5" failures="0" errors="0" skipped="0"` for:
- `vaultRepositoryPersistsTracksAndCollections`
- `appDatabaseUsesFixedNameForBackupRestore`
- `scheduleRepositoryPersistsBlocksAndSmartPlaylists`
- `stationRepositoryPersistsLocalStationProfile`
- `appPreferencesRoundTripNonSecretDeviceConfig`

Final build/install/smoke after Phase 1:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest assembleDebug
# BUILD SUCCESSFUL (`testDebugUnitTest` is NO-SOURCE; tests are instrumented)
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Success
adb shell input keyevent KEYCODE_HOME
adb shell uiautomator dump /sdcard/window.xml
# Visible text included:
# Overview isn't wired to the on-device backend yet — coming in a later build phase.
# Try again
# Open navigation menu
# Overview
# and did NOT include "SD card required".
```

`dumpsys activity` after the smoke showed `mLockTaskModeState=LOCKED`, and a
post-smoke logcat tail did not show a `FATAL EXCEPTION` for `com.paperweight.os`.
Phase 1 can proceed to Phase 2.
