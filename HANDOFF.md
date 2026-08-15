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

**Phase 2 (Vault ingestion) is now build-verified and partially on-device
verified on the connected Galaxy A12.** Current session used Temurin JDK 17 at
`/home/bud/.local/jdks/jdk-17`, Android SDK at `/home/bud/Android/Sdk`, and the
connected `SM-A125U` / Android 11 A12. A real SAF storage-path gap was found and
then fixed: setup now tells the operator to create/select a `Paperweight` folder
on the SD card, and `VaultFileStore` writes directly to `vault/` when that
granted folder is already the Paperweight root.

Real build results:

```bash
export JAVA_HOME=/home/bud/.local/jdks/jdk-17
export ANDROID_HOME=/home/bud/Android/Sdk
export ANDROID_SDK_ROOT=/home/bud/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

./gradlew :app:compileDebugKotlin assembleDebug # BUILD SUCCESSFUL in 3m 18s
./gradlew :app:testDebugUnitTest                 # BUILD SUCCESSFUL; testDebugUnitTest NO-SOURCE
```

`assembleDebug` produced `app/build/outputs/apk/debug/app-debug.apk`
(size observed: 64,590,663 bytes). Warnings were the already-known Compose icon
deprecations and the existing `libandroidx.graphics.path.so` strip warning.

On-device smoke result:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk # Success
adb shell am start -n com.paperweight.os/.MainActivity
adb shell uiautomator dump /sdcard/window.xml
```

With the valid SD card mounted as `public:179,97` / `/storage/ED4F-17F7`, the
app launched to the dashboard, navigation opened, and the Vault screen rendered
Phase 2 content (`Add to vault`, `Your vault`, and the empty "Nothing ingested
yet" state). `mLockTaskModeState=LOCKED`.

The Phase 2 lockTask allowlist fix is confirmed: tapping `Add to vault` launched
`com.google.android.documentsui/com.android.documentsui.picker.PickActivity`
inside lockTask, and `dumpsys activity` showed the DocumentsUI task as
`LOCK_TASK_AUTH_WHITELISTED` with `mLockTaskModeState=LOCKED`.

A direct instrumented validation of the ingestion backend path was also run on
the real A12 using a temporary test and a generated sample WAV. Result:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperweight.os.vault.Phase2VaultIngestionInstrumentedTest
# Starting 1 tests on SM-A125U - 11
# Finished 1 tests on SM-A125U - 11
# BUILD SUCCESSFUL
```

The test exercised `VaultIngestor.ingest()` against a real persisted SAF tree
grant, verified metadata fallback/duration/mime, copied the sample into the
granted SD tree, and persisted the resulting `VaultTrackEntity` into Room.
Physical file observed afterward:

```bash
/storage/ED4F-17F7/A12Phase2Grant/Paperweight/vault/a12-phase2-sample.wav
# 176,444 bytes
```

**Important gap found and fixed:** Android 11 DocumentsUI will not grant the
SD-card root itself (`Can’t use this folder / To protect your privacy, choose
another folder`). The validation had to create/grant a subfolder
(`A12Phase2Grant`). Setup now tells the operator to create/select a folder named
`Paperweight` on the SD card, and `VaultFileStore` treats a granted folder named
`Paperweight` as the vault root, creating `vault/` directly underneath it instead
of nesting `Paperweight/Paperweight/vault`. If an older/nonstandard grant points
one level above Paperweight, the code still falls back to creating/using
`Paperweight/vault/` under that granted tree.

Remote adb automation confirmed the picker opens, but did not complete a full
human-style `OpenMultipleDocuments` file selection through DocumentsUI; the
backend ingest/copy/Room path was verified by the direct instrumented test
above.

**Phase 3 (Backup & recovery) has started and the core backup/restore path is
build-verified plus instrumented-test verified on the physical A12.** This pass
added the backup package, non-secret preference export/import, backup controls in
Settings, and operator recovery guidance. The tested core path writes
`Paperweight/backups/<timestamp>/` snapshots containing:

- `paperweight-os.db` produced through SQLite `VACUUM INTO` into a temp file,
  then copied into the SAF/DocumentFile snapshot directory;
- `preferences.json` containing only non-secret `AppPreferences` fields;
- `manifest.json` containing timestamp, app version, schema version, DB file,
  and preferences file names.

Phase 3 validation run on `SM-A125U - 11`:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperweight.os.backup.Phase3BackupRecoveryInstrumentedTest
# Starting 3 tests on SM-A125U - 11
# Finished 3 tests on SM-A125U - 11
# BUILD SUCCESSFUL

./gradlew :app:connectedDebugAndroidTest
# Starting 8 tests on SM-A125U - 11
# Finished 8 tests on SM-A125U - 11
# BUILD SUCCESSFUL
```

General build validation after the Phase 3 changes:

```bash
./gradlew :app:compileDebugKotlin assembleDebug :app:testDebugUnitTest
# BUILD SUCCESSFUL; testDebugUnitTest NO-SOURCE
# app/build/outputs/apk/debug/app-debug.apk observed at 65,639,488 bytes
adb install -r app/build/outputs/apk/debug/app-debug.apk # Success
adb shell am start -n com.paperweight.os/.MainActivity
# MainActivity resumed on the physical A12; mLockTaskModeState=LOCKED after wake/dismiss-keyguard.
```

The full first-run Compose restore-vs-start-fresh gate is now wired into the
boot chain before `DashboardApp()` and before any dashboard repository opens
Room. The boot chain is now Device Owner → SD-card present/sized → SAF
`Paperweight` folder grant → backup scan → restore-or-start-fresh decision →
dashboard. Existing installs with an already-present `paperweight-os.db` are
marked as already past the restore decision so app updates don't interrupt a
working device.

Additional gate validation on the physical A12 after install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk # Success
adb shell am start -n com.paperweight.os/.MainActivity
# Restore gate visible:
#   Backup & recovery
#   Before the dashboard opens, choose the SD-card folder named Paperweight...
#   Choose Paperweight folder
# DocumentsUI launched inside lockTask and remained LOCK_TASK_AUTH_WHITELISTED.
# After allowing the folder with no backups present:
#   No existing backup was found in Paperweight/backups/.
#   Start fresh
# Tapping Start fresh continued to the dashboard; mLockTaskModeState=LOCKED.
```

**Phase 4 (Broadcast engine core) has started.** This pass added the first local
broadcast engine scaffold, foreground service, HLS writer primitives, and
Overview/Broadcast screen rewiring. The engine now observes local public vault
tracks, publishes a `BroadcastState`, writes an initial packed-audio HLS window
(`init.aac`, `segment-*.aac`, `live.m3u8`) via `SegmentStore`/`PlaylistWriter`,
and starts as a foreground service once the restore gate has completed and the
dashboard is allowed to open. The current segment payload is a silent AAC/ADTS
heartbeat scaffold for the engine/service/playlist path; full source-track
decode-to-AAC audio rotation still needs to replace that scaffold before LAN
playback can be called product-complete.

Phase 4 validation on `SM-A125U - 11`:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperweight.os.broadcast.Phase4BroadcastEngineInstrumentedTest
# Starting 3 tests on SM-A125U - 11
# Finished 3 tests on SM-A125U - 11
# BUILD SUCCESSFUL

./gradlew :app:compileDebugKotlin assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
# Starting 12 tests on SM-A125U - 11
# Finished 12 tests on SM-A125U - 11
# BUILD SUCCESSFUL; testDebugUnitTest NO-SOURCE
```

On-device smoke after install confirmed `MainActivity` resumed into the Overview
screen, `BroadcastService` was running foreground with notification channel
`paperweight_broadcast`, and lockTask remained locked:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk # Success
adb shell am start -n com.paperweight.os/.MainActivity
adb shell dumpsys activity services com.paperweight.os | grep BroadcastService
# ServiceRecord ... com.paperweight.os/.broadcast.BroadcastService
# isForeground=true foregroundId=404
adb shell dumpsys notification --noredact | grep -A4 'Paperweight broadcast'
# android.title=Paperweight broadcast
adb shell dumpsys activity activities | grep mLockTaskModeState
# mLockTaskModeState=LOCKED
```

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

## Status: what's built (Phase 2)

- **New `vault/` package**: `MetadataExtractor.kt` (wraps
  `MediaMetadataRetriever` — title/artist/album/duration/mime, falling back
  to the source filename when a track has no title tag), `VaultFileStore.kt`
  (pure SAF `DocumentFile` copy into `vault/` when the already-granted tree URI
  is the operator-selected `Paperweight` folder; otherwise falls back to
  creating/using `Paperweight/vault/` under the granted tree; de-dupes
  filenames), and `VaultIngestor.kt` (owns the one-time tree-grant
  persist/re-validate logic per decision #10, and orchestrates
  extract-then-copy-then-`VaultRepository.upsertTrack` as one `ingest()`
  call, dispatched onto `Dispatchers.IO`).
- **`storagePath` stores the copied file's own SAF document URI** (e.g.
  `content://com.android.externalstorage.documents/tree/.../document/...`),
  not a human-readable relative path — that's the only identifier SAF
  guarantees stays resolvable across app restarts for a tree-granted volume.
  `sourceUri` still records where the file originally came from, for
  reference only.
- **`AppPreferences` gained `vaultTreeUri`** (nullable `Flow<String?>` +
  setter), stored in the existing plain (non-encrypted) prefs file —
  intentionally non-secret, it's just a permission handle over content
  already physically on the card, not a credential.
- **`ServiceLocator` gained `vaultIngestor`**, composed from
  `vaultRepository` + `appPreferences`.
- **Gradle: added `androidx.documentfile:documentfile:1.0.1`** — needed for
  `DocumentFile`/SAF tree operations, added now because this is the first
  phase that needs it (same "add at the phase that needs it" discipline as
  Phase 0/1).
- **`VaultViewModel` rewired for the first time since the pivot** (every
  other dashboard ViewModel is still the Phase 0 stub). `state` now starts
  at `ScreenState.Content(VaultUiState())` (not `Error`) and
  `VaultRepository.observeTracks()` feeds `VaultUiState.localTracks` live.
  `ingestTracks(uris)` drives the SAF-picker → `VaultIngestor.ingest()` loop
  and reports a one-line success/failure summary via the existing
  `actionMessage` field. `saveLocalTrackPricing()` is a real, working
  read-modify-write against `VaultTrackEntity`'s pricing columns (those
  columns and `VaultRepository.upsertTrack`/`getTrack` already existed from
  Phase 1 — wiring pricing edits for locally-ingested tracks was near-zero
  extra cost once ingestion needed a working `Content` state anyway).
- **Every legacy pre-pivot handler on `VaultViewModel`
  (`saveTrackPricing`/collections/highlight/artwork/tokens — the
  `network.models` DTO-shaped surface) now calls `notify(LEGACY_NOT_WIRED)`
  instead of silently no-op'ing.** Previously these were empty function
  bodies; that was fine when the whole screen rendered `ScreenState.Error`
  and none of that UI was reachable. Now that `VaultScreen` renders real
  `Content` (for the ingestion section), the old pricing/collections/token
  UI panels became reachable-but-dead — tapping "Create" on a token, for
  example, would have silently done nothing. Per this file's existing
  decision #2 philosophy ("honest not-wired message beats fake success"),
  every one of those buttons now gives feedback instead of quietly eating
  the tap. `trackPrices`/`projects`/`tokens` all still populate as empty
  lists (nothing fetches them locally yet), so in practice most of that
  legacy UI doesn't render at all today — only the always-visible "Access
  tokens" panel's Create button was actually reachable, and it's covered by
  this fix.
- **`VaultScreen.kt`** gained: the "Add to vault" SAF-picker flow (tree
  grant check → `OpenDocumentTree` if not yet granted → `OpenMultipleDocuments`
  filtered to `audio/*`), a new always-rendered "Your vault" list section for
  `localTracks` (`LocalVaultTrackRow` + `LocalTrackPriceForm`, reusing the
  existing `formatPriceCents`/`centsToDollarText`/`dollarsToCents` helpers),
  and a `formatDurationMs` helper. All pre-existing legacy composables
  (`ProjectRow`, `TrackRow`, `TokenManagerPanel`, etc.) are untouched.
- **No `AndroidManifest.xml` changes** — per plan decision, SAF tree/file
  grants need no manifest permission (no `READ_MEDIA_AUDIO`, no
  `MANAGE_EXTERNAL_STORAGE`); confirmed nothing else in Phase 2 needed a
  manifest change either.

## Status: what's built (Phase 3)

- **New `backup/` package**:
  - `BackupWriter.kt` writes `Paperweight/backups/<timestamp>/` under the same
    operator-granted SAF tree used by Vault. It treats a granted folder named
    `Paperweight` as the root, matching the Phase 2 SAF contract, and falls back
    to creating/using `Paperweight/backups/` if an older/nonstandard grant points
    one level above it.
  - `BackupModels.kt` defines `BackupManifest` and `BackupSnapshot`.
  - `BackupPruner.kt` keeps the newest N snapshot directories by timestamp name.
  - `RestoreManager.kt` copies `paperweight-os.db` into the fixed Room DB path
    and restores non-secret preferences before a new Room instance is opened.
  - `BackupScheduler.kt` defines the WorkManager periodic/one-shot scheduling
    shell and `BackupWorker`.
  - `RecoveryInfoExporter.kt` is a deliberately plain text placeholder for the
    Phase 9 secret-export concept; no secrets exist yet, and automatic backups
    intentionally exclude future Android-Keystore-backed secrets.
- **`AppPreferences` gained non-secret export/import** via
  `snapshotNonSecretConfig()` and `restoreNonSecretConfig(...)`. Exported fields
  are station name, server port, backup retention, backup interval, and the
  non-secret vault tree URI. Future frp/reachability secrets must not be added
  to this automatic backup JSON.
- **Settings screen rewired to local backup controls** instead of the old remote
  webhook/feed/account/docs surface: it now shows SAF target readiness, retention
  count, interval hours, `Back up now`, and `Show recovery info`. Legacy handler
  methods still exist on `SettingsViewModel` and fail soft with a local-only
  message if any old composable reference survives.
- **Provisioning guidance updated** in `provisioning/setup.sh` and `CLAUDE.md`:
  if Android/Samsung factory reset is used for legitimate re-provisioning, leave
  any "erase SD card" option unchecked because vault media and backups live on
  the removable card.
- **New instrumented tests**:
  `app/src/androidTest/java/com/paperweight/os/backup/Phase3BackupRecoveryInstrumentedTest.kt`
  verifies backup snapshot contents, pruning, and restore of DB + non-secret
  preferences before Room reopens. These tests run on the physical A12 and use a
  file-backed `DocumentFile` tree so they do not mutate the real SD card.
- **Restore gate wired into `MainActivity`**: after the SD-card gate clears,
  `MainActivity` now checks `AppPreferences.initialRestoreDecisionMade`. If the
  first-run decision has not happened, it renders `RestoreGateScreen` instead of
  `DashboardApp`, launches `ACTION_OPEN_DOCUMENT_TREE` for the one-time
  `Paperweight` folder grant, scans for the newest backup via `RestoreManager`,
  offers restore vs. start fresh, and only then lets the dashboard open. This is
  deliberately before `ServiceLocator.database` is touched by dashboard
  ViewModels, preserving the plan requirement that restore runs before Room
  creates/opens an empty DB.

## Status: what's built (Phase 4)

- **New `broadcast/` package scaffold**:
  - `BroadcastState.kt` defines local `BroadcastState` and `BroadcastQueueTrack`.
  - `BroadcastEngine.kt` observes local vault tracks, filters to public tracks,
    publishes now-playing/queue state, supports mode toggle/restart/remove, and
    writes an initial HLS window when public tracks are available.
  - `BroadcastService.kt` starts the engine as a sticky foreground service with
    notification channel `paperweight_broadcast`.
  - `encode/AdtsHeaderWriter.kt` writes/parses AAC-LC ADTS headers;
    `encode/AacEncoder.kt` currently provides the silent AAC/ADTS heartbeat
    payload used by the initial HLS scaffold.
  - `hls/PlaylistWriter.kt`, `hls/SegmentWriter.kt`, and `hls/SegmentStore.kt`
    write packed-audio segment files and an atomic `live.m3u8` playlist.
  - `decode/TrackDecoder.kt` can inspect an audio URI via `MediaExtractor` and
    confirm basic track duration/mime metadata, but full source decode into the
    encoder is still the main unfinished Phase 4 item.
- **Manifest/service changes**: added foreground-service/network/wake/notification
  permissions needed for the always-on broadcast service and declared
  `.broadcast.BroadcastService` with `foregroundServiceType="specialUse"` and
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE=internet_radio_broadcast_encoding`.
- **`ServiceLocator` gained `broadcastEngine`**, composed from the existing local
  repositories. `MainActivity` starts `BroadcastService` only after the restore
  gate has completed and `DashboardApp()` is allowed, so Phase 3's pre-Room
  restore rule is preserved.
- **Broadcast + Overview screens are no longer error stubs**: `BroadcastViewModel`
  and `OverviewViewModel` now collect the local `BroadcastEngine.state` and render
  real Content state. With no public vault tracks, Overview shows zero catalog /
  nothing queued instead of the old "not wired" error.
- **New instrumented tests**:
  `app/src/androidTest/java/com/paperweight/os/broadcast/Phase4BroadcastEngineInstrumentedTest.kt`
  verifies ADTS header construction, HLS playlist writing, public-only queue
  selection, now-playing publication, and initial HLS file creation on the
  physical A12.

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
4. **Manifest/permission additions are still added per-phase, not front-loaded.**
   Phase 4 added only what the always-on broadcast foreground service now needs:
   `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`,
   `ACCESS_WIFI_STATE`, `WAKE_LOCK`, and the `BroadcastService` declaration.
   `RECORD_AUDIO` / `FOREGROUND_SERVICE_MICROPHONE` still belong to the later mic
   go-live phase and should not be added until that phase actually implements mic
   capture.
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
8. **RESOLVED and verified on the physical A12: the lockTask blocker on the
   SAF/content pickers is fixed via dynamic `PackageManager` resolution instead
   of a hardcoded package name.**
   `DeviceOwnerPolicy.setLockTaskPackages` previously only allowlisted
   `com.paperweight.os` and `com.android.settings`, which blocks
   `ACTION_OPEN_DOCUMENT_TREE`/`ACTION_OPEN_DOCUMENT` (the "Add to vault"
   flow) and `ACTION_GET_CONTENT` (the legacy artwork-upload flow) from
   launching under lockTask, since the system picker resolves to a different
   package. Rather than guess which package that is (AOSP's
   `com.android.documentsui`? Play-Store-updated `com.google.android.documentsui`?
   Samsung's own My Files? — varies by OEM/OS version and can change across a
   system update), `DeviceOwnerPolicy.apply()` now queries `PackageManager`
   at runtime for whatever actually resolves those three intents and
   allowlists exactly that, re-computed on every `MainActivity.onCreate()`
   (self-healing across OS updates). This required adding a `<queries>`
   manifest declaration (`AndroidManifest.xml`) for the three intents, since
   Android 11+ package-visibility filtering would otherwise make
   `queryIntentActivities` return nothing for a non-privileged app.
   Validation on the `SM-A125U` showed the picker resolves to
   `com.google.android.documentsui`, launches from `Add to vault`, and remains
   inside lockTask as `LOCK_TASK_AUTH_WHITELISTED` with
   `mLockTaskModeState=LOCKED`.
9. **`storagePath` on `VaultTrackEntity` now stores the ingested file's own
   SAF document URI (`content://...`), not a relative path string.** The
   Phase 1 instrumented test's example data used a human-readable relative
   path (`"Paperweight/vault/intro.mp3"`), but that was just test fixture
   data, not a contract — Phase 1 didn't have an ingestion path yet to bind
   the real shape. A `content://` document URI is the only thing SAF
   actually guarantees stays resolvable (via `ContentResolver.openInputStream`)
   across app restarts for a tree-granted volume, so that's what Phase 2's
   real `VaultIngestor` writes there. Later phases (broadcast engine reading
   vault files, Phase 3 backup) should treat `storagePath` as an opaque SAF
   URI, not a filesystem path.
10. **Phase 2 now has real compiler, install, picker-launch, and direct
    ingestion feedback from the physical A12, and the SAF-root assumption has
    been corrected.** Android 11 DocumentsUI refuses an SD-card-root tree grant
    (`Can’t use this folder`), so the product contract is now: operator creates
    or selects a `Paperweight` folder on the SD card as the SAF root. The setup
    script, pre-device-owner setup copy, and Vault screen copy all say that.
    `VaultFileStore` no longer nests `Paperweight/Paperweight/vault` when the
    granted tree is already named `Paperweight`; it writes directly to `vault/`
    under that tree. Older/nonstandard grants still fall back to
    `Paperweight/vault/` under the granted tree.
11. **Automatic backups are non-secret by design.** Phase 3 snapshots contain
    the Room DB and a JSON export of plain `AppPreferences` fields only. Future
    frp/reachability secrets must not be added to `preferences.json`; Android
    Keystore-backed secrets will need a separate one-time recovery-info export
    because the Keystore key will not survive reinstall/factory reset.

## What's left

**Phase 2's remaining validation is manual end-to-end polish, not the previous
storage-contract blocker.** Build/install/UI smoke now passes with the corrected
operator copy and `VaultFileStore` path logic. Before starting Phase 3, run one
final human/manual picker pass: choose the intended `Paperweight` folder, pick a
real audio file through `OpenMultipleDocuments`, confirm it appears under "Your
vault," confirm the physical file lands in `Paperweight/vault/` without a nested
`Paperweight/Paperweight/`, and confirm "Edit price" round-trips through the app
DB.

Phase 3 remaining real-device recovery validation:
1. Run a real/manual backup-now pass against the actual SD card via Settings,
   then inspect `Paperweight/backups/<timestamp>/` on the card.
2. Run the practical restore rehearsal: leave the SD card inserted, clear app
   data/reinstall, grant/select the `Paperweight` folder, choose restore, and
   confirm vault metadata/config returns without re-ingesting media.

Phase 4 remaining work before moving to Phase 5:
1. Replace the silent AAC/ADTS heartbeat scaffold with true source-track decode
   (`MediaExtractor`/`MediaCodec`) feeding AAC encoding and segment rotation from
   the copied vault file URIs.
2. Validate with a real ingested audio track that `live.m3u8` + segments are
   playable locally before relying on Phase 5's NanoHTTPD/LAN player.
3. Improve Broadcast/Overview metrics once the engine is playing real tracks
   continuously (elapsed/queue/current-track state instead of the current minimal
   now-playing scaffold).

Phases 5–12 per the plan file, once Phase 4 is complete:
1. **Phase 5 — Embedded server + listener player**: NanoHTTPD routes with Range
   support, vendored listener web assets, LAN playback.
2. Phases 6–12 as detailed in the plan file.

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
├── data/                           // Phase 1, prefs extended in Phase 2
│   ├── db/AppDatabase.kt            // Room DB, fixed name paperweight-os.db
│   ├── db/entity/                   // Vault, schedule, token, analytics, station tables
│   ├── dao/                         // VaultDao, ScheduleDao, TokenDao, AnalyticsDao, StationDao
│   ├── prefs/AppPreferences.kt      // + vaultTreeUri (Phase 2), non-secret backup export/import (Phase 3)
│   └── repository/                  // local repository facades
├── backup/                         // NEW (Phase 3)
│   ├── BackupWriter.kt / BackupPruner.kt / RestoreManager.kt
│   ├── BackupScheduler.kt / BackupModels.kt
│   └── RecoveryInfoExporter.kt
├── vault/                          // NEW (Phase 2)
│   ├── MetadataExtractor.kt         // MediaMetadataRetriever wrapper
│   ├── VaultFileStore.kt            // pure SAF copy into Paperweight/vault/
│   └── VaultIngestor.kt             // tree-grant persist/check + ingest() orchestration
├── di/ServiceLocator.kt             // Phase 1 composition root, + vaultIngestor (Phase 2), + broadcastEngine (Phase 4)
├── broadcast/                       // NEW (Phase 4)
│   ├── BroadcastEngine.kt / BroadcastService.kt / BroadcastState.kt
│   ├── decode/TrackDecoder.kt
│   ├── encode/AacEncoder.kt / AdtsHeaderWriter.kt
│   └── hls/PlaylistWriter.kt / SegmentWriter.kt / SegmentStore.kt
├── network/models/                 // KEPT (see Key decisions #1), transport layer deleted
│   ├── AudienceModels.kt / BroadcastModels.kt / DashboardAnalyticsModels.kt
│   ├── DashboardEarningsModels.kt / LibraryModels.kt / ScheduleModels.kt
│   ├── SettingsModels.kt / StationModels.kt / StreamModels.kt / VaultModels.kt
└── ui/
    ├── theme/                      // untouched
    ├── nav/                        // untouched
    ├── components/                 // untouched
    ├── setup/SdCardRequiredScreen.kt  // NEW (Phase 0)
    └── dashboard/                  // Vault rewired for local ingestion (Phase 2);
                                     // Overview/Broadcast collect BroadcastEngine state (Phase 4);
                                     // settings rewired for backup controls (Phase 3);
                                     // remaining screens still use Phase 0 Error stubs
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

Phase 2 validation:

```bash
export JAVA_HOME=/home/bud/.local/jdks/jdk-17
export ANDROID_HOME=/home/bud/Android/Sdk
export ANDROID_SDK_ROOT=/home/bud/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

./gradlew :app:compileDebugKotlin assembleDebug
# BUILD SUCCESSFUL in 3m 18s
# app/build/outputs/apk/debug/app-debug.apk = 64,590,663 bytes

./gradlew :app:testDebugUnitTest
# BUILD SUCCESSFUL; testDebugUnitTest NO-SOURCE

adb install -r app/build/outputs/apk/debug/app-debug.apk
# Success
adb shell am start -n com.paperweight.os/.MainActivity
adb shell uiautomator dump /sdcard/window.xml
# Vault screen visible with "Add to vault", "Your vault", and
# "Nothing ingested yet — tap \"Add to vault\"..."
```

LockTask / picker validation:

```bash
# Tap Add to vault from the Vault screen
adb shell dumpsys activity activities | grep -E 'ResumedActivity|mLockTaskModeState|mLockTaskAuth'
# ResumedActivity: com.google.android.documentsui/com.android.documentsui.picker.PickActivity
# mLockTaskAuth=LOCK_TASK_AUTH_WHITELISTED
# mLockTaskModeState=LOCKED
```

On this A12, DocumentsUI resolves the picker package as
`com.google.android.documentsui`, confirming the dynamic allowlist fix works.

Direct ingestion validation on the real A12 used a temporary instrumented test
(`Phase2VaultIngestionInstrumentedTest`) and a generated WAV pushed to the SD
card. The temporary test was removed after the run so the repo is not left with
a hardcoded card UUID test fixture.

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperweight.os.vault.Phase2VaultIngestionInstrumentedTest
# Starting 1 tests on SM-A125U - 11
# Finished 1 tests on SM-A125U - 11
# BUILD SUCCESSFUL
```

JUnit XML reported `tests="1" failures="0" errors="0" skipped="0"` for
`com.paperweight.os.vault.Phase2VaultIngestionInstrumentedTest`.

Physical copied file observed afterward:

```bash
adb shell 'find /storage/ED4F-17F7/A12Phase2Grant/Paperweight -maxdepth 3 -type f -print -exec ls -l {} \;'
# /storage/ED4F-17F7/A12Phase2Grant/Paperweight/vault/a12-phase2-sample.wav
# -rwxrwx--- ... 176444 ... a12-phase2-sample.wav
```

Important limitation/blocker from validation: Android 11 DocumentsUI refuses to
grant the SD-card root itself, showing `Can’t use this folder` / `To protect
your privacy, choose another folder`. The direct ingestion test therefore used a
created subfolder grant (`A12Phase2Grant`), which makes current code write under
`A12Phase2Grant/Paperweight/vault/`, not SD-card-root `Paperweight/vault/`.
Resolve that SAF-root contract before Phase 3 backup/recovery relies on it.
Remote adb automation did not complete a full manual file pick through
`OpenMultipleDocuments`; the picker launch and backend ingestion/copy/Room path
were verified separately as above.
