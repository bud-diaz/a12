# Handoff: Paperweight OS on-device-backend pivot

Written as the running handoff for the Paperweight OS Android build-out.
**The project pivoted from a thin remote client to an on-device backend.**
Everything below the "Latest validation update" section describes that
pivot's Phase 0 (of 12). This file is self-contained — a new session
shouldn't need anything from a previous session's local state to pick this
up, only this repo.

## Read first

- `/home/user/a12/CLAUDE.md` — project constraints and current on-device backend
  architecture. It has been rewritten after the pivot; it no longer describes the
  old QR-pairing/remote-dashboard architecture as current scope.
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

**Phase 5 LAN HLS playback blocker is patched and device-validated on the physical A12.**
This session added an explicit debug-only `Generate Phase 5 validation tone` path:
`DebugBuild` gates the UI to debuggable builds, `ValidationBroadcastSeeder`
generates a real app-private WAV and inserts it as a public vault track, and the
existing broadcast pipeline encodes it into AAC/HLS for the embedded server. The
playlist writer was also corrected to omit an invalid `#EXT-X-MAP` for packed
ADTS AAC; `ffprobe` had rejected the previous empty `init.aac` map with HTTP 416.

Real validation on `SM-A125U - 11` / A12 Wi-Fi IP `10.0.0.145`:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
# BUILD SUCCESSFUL
# :app:testDebugUnitTest NO-SOURCE
# app-debug.apk 71,291,613 bytes
# sha256 745b719ce87c2dbea572dca0990bb106c4857c7603ac2e6e8d727062b416fe9e

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperweight.os.broadcast.Phase5ValidationToneInstrumentedTest
# Starting 1 tests on SM-A125U - 11
# Finished 1 tests on SM-A125U - 11
# BUILD SUCCESSFUL

./gradlew :app:connectedDebugAndroidTest
# Starting 15 tests on SM-A125U - 11
# Finished 15 tests on SM-A125U - 11
# BUILD SUCCESSFUL

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.paperweight.os/.MainActivity
# Success; MainActivity resumed after dismissing keyguard
# mLockTaskModeState=LOCKED; wlan0 IP=10.0.0.145; LISTEN *:8080

# Broadcast screen UI showed the debug-only button, then after tapping it:
#   Phase 5 validation tone
#   Broadcast queue -> Phase 5 validation tone

curl -i http://10.0.0.145:8080/status
# HTTP/1.1 200 OK; application/json
# {"isRunning":true,"nowPlayingTitle":"Phase 5 validation tone",
#  "nowPlayingArtist":"Paperweight OS","durationMs":12000,"queueLength":1}

curl -i http://10.0.0.145:8080/live/playlist.m3u8
# HTTP/1.1 200 OK; application/vnd.apple.mpegurl
# #EXTM3U
# #EXT-X-VERSION:7
# #EXT-X-TARGETDURATION:6
# #EXT-X-MEDIA-SEQUENCE:0
# #EXTINF:6.014,
# segment-0.aac

curl -i -H 'Range: bytes=0-15' http://10.0.0.145:8080/live/segment-0.aac
# HTTP/1.1 206 Partial Content
# Content-Type: audio/aac
# Content-Range: bytes 0-15/98036

ffprobe -hide_banner -v error -show_entries stream=codec_name,codec_type,sample_rate,channels \
  -of default=noprint_wrappers=1 http://10.0.0.145:8080/live/playlist.m3u8
# codec_name=aac
# codec_type=audio
# sample_rate=44100
# channels=2
```

The service/listener checks also showed `BroadcastService isForeground=true`,
`LISTEN *:8080`, `mLockTaskModeState=LOCKED`, and `ResumedActivity` still
`com.paperweight.os/.MainActivity`. This validates the A12 LAN HLS endpoint as a
real playable AAC stream from a LAN client. Bud should still do the final human
ear-check by pressing Play at `http://10.0.0.145:8080/` or opening
`http://10.0.0.145:8080/live/playlist.m3u8` in VLC from another Wi-Fi device,
but the previous `404` blocker is resolved.

**Android Settings kiosk exception + nav button validated on the physical A12.**
Current session changed `DeviceOwnerPolicy` to keep Android Settings explicitly
allowlisted and also resolve the concrete package for `Settings.ACTION_SETTINGS`
at runtime, then added an `Android Settings` item to the dashboard drawer. Real
validation on `SM-A125U - 11`:

```bash
./gradlew :app:compileDebugKotlin
# BUILD SUCCESSFUL in 2s

./gradlew :app:assembleDebug :app:testDebugUnitTest
# BUILD SUCCESSFUL in 1s
# :app:testDebugUnitTest NO-SOURCE

adb install -r app/build/outputs/apk/debug/app-debug.apk
# Success

# Open dashboard drawer, confirm `Android Settings` item exists, tap it.
# dumpsys activity activities then showed:
# ResumedActivity: com.android.settings/.homepage.SettingsHomepageActivity
# LockTaskController mLockTaskModeState=LOCKED
# mLockTaskPackages u0:[com.paperweight.os, com.android.settings,
#   com.google.android.documentsui, com.samsung.android.app.soundpicker,
#   com.sec.android.app.myfiles, com.sec.android.gallery3d]
```

The A12 was returned to `com.paperweight.os/.MainActivity` afterward with
`mLockTaskModeState=LOCKED`.

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

**Phase 4 (Broadcast engine core) is code-complete and verified on the physical
A12.** This pass replaced the silent AAC/ADTS heartbeat scaffold with a real
source-track path: SAF/file vault URI -> `MediaExtractor`/decoder `MediaCodec` ->
PCM -> AAC-LC encoder `MediaCodec` -> ADTS-framed packed-audio HLS segments ->
atomic `live.m3u8` live-window updates. `BroadcastEngine` now filters public vault
tracks, encodes real audio segments, publishes current track/elapsed/duration and
queue state, rotates through public tracks continuously, and keeps
`BroadcastService` as a sticky foreground service once the restore gate has
completed and the dashboard opens.

`CLAUDE.md` was also rewritten to match the on-device-backend pivot so future
sessions do not resurrect the old QR-pairing/remote-backend architecture.

Phase 4 validation on `SM-A125U - 11`:

```bash
./gradlew :app:compileDebugKotlin :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperweight.os.broadcast.Phase4BroadcastEngineInstrumentedTest
# Starting 5 tests on SM-A125U - 11
# Finished 5 tests on SM-A125U - 11
# BUILD SUCCESSFUL

./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
# Starting 14 tests on SM-A125U - 11
# Finished 14 tests on SM-A125U - 11
# BUILD SUCCESSFUL; testDebugUnitTest NO-SOURCE
```

The Phase 4 instrumented tests now verify ADTS headers, packed-audio playlist
writing, AAC encoding of non-silent PCM, real encoded HLS segment writing, public-
only queue selection, now-playing publication, and generation of real track-audio
segments from a generated WAV fixture on the physical A12.

On-device smoke after install confirmed `MainActivity` resumed into the Overview
screen, `BroadcastService` was running foreground with notification channel
`paperweight_broadcast`, and lockTask remained locked after wake/dismiss-keyguard:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk # Success
adb shell am start -n com.paperweight.os/.MainActivity
adb shell dumpsys activity services com.paperweight.os
# ServiceRecord ... com.paperweight.os/.broadcast.BroadcastService
# isForeground=true foregroundId=404 foregroundNoti=Notification(channel=paperweight_broadcast ...)
adb shell dumpsys activity activities | grep mLockTaskModeState
# mLockTaskModeState=LOCKED
stat -c '%s' app/build/outputs/apk/debug/app-debug.apk
# 65015000
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

- **`broadcast/` package completed for Phase 4**:
  - `BroadcastState.kt` defines local `BroadcastState`/`BroadcastQueueTrack`, now
    including elapsed/duration, playlist path, and segment-count state for real
    playback progress.
  - `BroadcastEngine.kt` observes local vault tracks, filters to public tracks,
    decodes/encodes the current track into packed-audio HLS segments, publishes
    live playlist windows over time, rotates continuously through the public
    queue, and supports mode toggle/restart/remove.
  - `BroadcastService.kt` starts the engine as a sticky foreground service with
    notification channel `paperweight_broadcast`.
  - `decode/TrackDecoder.kt` uses `MediaExtractor` to find the audio track and
    either reads raw PCM directly or decodes compressed audio through decoder
    `MediaCodec` into PCM.
  - `encode/AacEncoder.kt` now uses encoder `MediaCodec` for AAC-LC output and
    wraps each output frame with an ADTS header via `AdtsHeaderWriter`; the old
    silent-frame helper remains only as a fallback/test fixture.
  - `hls/PlaylistWriter.kt`, `hls/SegmentWriter.kt`, and `hls/SegmentStore.kt`
    write packed-audio `.aac` segment files and atomically update `live.m3u8`
    with a sliding live window.
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
- **Phase 4 instrumented tests expanded**:
  `app/src/androidTest/java/com/paperweight/os/broadcast/Phase4BroadcastEngineInstrumentedTest.kt`
  verifies ADTS header construction, HLS playlist writing, non-silent PCM -> AAC
  encoding, real encoded segment writing, public-only queue selection, now-playing
  publication, and real generated-WAV audio segment generation on the physical
  A12.

**Phase 5 (Embedded server + listener player) is build-, A12-, and LAN-HLS-validated.**
Initial local validation on Bud's machine (`JAVA_HOME=/home/bud/.local/jdks/jdk-17`,
`ANDROID_HOME=/home/bud/Android/Sdk`) and physical `SM-A125U - 11`:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
# BUILD SUCCESSFUL
# :app:testDebugUnitTest NO-SOURCE
# app-debug.apk 71,291,613 bytes
# sha256 745b719ce87c2dbea572dca0990bb106c4857c7603ac2e6e8d727062b416fe9e

./gradlew :app:connectedDebugAndroidTest
# Starting 15 tests on SM-A125U - 11
# Finished 15 tests on SM-A125U - 11
# BUILD SUCCESSFUL

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.paperweight.os/.MainActivity
adb shell dumpsys activity activities | grep -E 'ResumedActivity|mLockTaskModeState|mLockTaskAuth'
# ResumedActivity: com.paperweight.os/.MainActivity
# mLockTaskAuth=LOCK_TASK_AUTH_WHITELISTED
# mLockTaskModeState=LOCKED

adb shell dumpsys activity services com.paperweight.os | grep -A3 -B2 BroadcastService
# ServiceRecord ... com.paperweight.os/.broadcast.BroadcastService
# isForeground=true ... channel=paperweight_broadcast

adb shell ss -ltnp | grep ':8080'
# LISTEN 0 0 *:8080 *:*

adb forward tcp:18080 tcp:8080
curl -i http://127.0.0.1:18080/status
# HTTP/1.1 200 OK; application/json
# {"isRunning":true,"nowPlayingTitle":null,...,"listenerCount":0,"queueLength":0}

curl -i http://127.0.0.1:18080/
# HTTP/1.1 200 OK; text/html; bundled listener page loads local hls.min.js/player.js

# With seeded files/hls/live.m3u8 + segment-1.aac under app-private filesDir:
curl -i http://127.0.0.1:18080/live/playlist.m3u8
# HTTP/1.1 200 OK; application/vnd.apple.mpegurl; no-cache
curl -i http://127.0.0.1:18080/live/segment-1.aac
# HTTP/1.1 200 OK; audio/aac; immutable cache headers
curl -i -H 'Range: bytes=4-7' http://127.0.0.1:18080/live/segment-1.aac
# HTTP/1.1 206 Partial Content; Content-Range: bytes 4-7/16
curl -i http://127.0.0.1:18080/live/../paperweight_preferences.xml
# HTTP/1.1 404 Not Found
```

Earlier route smoke used `adb forward` while the device was not reporting a LAN
IPv4. The final closeout pass did show `wlan0` at `10.0.0.145` and used direct
LAN HTTP requests from this machine to the A12 for `/status`, `/live/playlist.m3u8`,
segment range serving, and `ffprobe`.

New `server/` package:
- `EmbeddedHttpServer.kt` — extends NanoHTTPD (already a Gradle dependency
  since Phase 0/1, unused until now), binds `0.0.0.0:<AppPreferences.serverPort>`
  (read once at construction — a port change needs a `BroadcastService` restart
  to take effect, same "restart required" shape `paperweightv1`'s own dashboard
  API uses for equivalent config changes). Routes by URI prefix to the handlers
  below.
- `routes/PlaylistRoute.kt` — serves `<filesDir>/hls/live.m3u8` (the file
  `PlaylistWriter`/`SegmentStore` already atomically rewrite every rotation
  tick) as `application/vnd.apple.mpegurl`, never cached.
- `routes/SegmentRoute.kt` — serves `init.aac`/`segment-<n>.aac` from the same
  `<filesDir>/hls/` directory as `audio/aac`, cacheable (segments are
  immutable once written). The requested filename is validated against an
  exact `(init|segment-\d+)\.aac` pattern before touching the filesystem —
  the only thing between an inbound LAN request and path traversal.
- `routes/StatusRoute.kt` — small JSON (`isRunning`, now-playing title/artist,
  elapsed/duration, listener/queue counts) read straight from
  `BroadcastEngine.state.value`, polled by the listener page.
- `routes/ListenerWebRoute.kt` — serves the vendored static assets below out
  of `assets/listener/` via `context.assets.open(...)`.
- `RangeResponse.kt` — shared `Range:` header parsing → HTTP 206 partial
  responses, used by both the playlist and segment routes.
- Route map: live manifest at `/live/playlist.m3u8`, segments at
  `/live/<filename>`, status at `/status`, listener page at `/`.
- Deliberately **not built**: `VaultVodRoute` (private-track VOD gating is
  Phase 8 scope, not built) and `TelemetryRoute` (Phase 9/10 scope) — same
  per-phase-only discipline as every prior phase.

New `app/src/main/assets/listener/` (vendored, no CDN references, per plan
decision #2's "vendored, not CDN" requirement):
- `index.html`/`styles.css`/`player.js` — hand-written, minimal audio-only
  listener page. `player.js` uses `Hls.isSupported()` → hls.js path, falls
  back to native `<audio>` HLS for Safari/iOS, polls `/status` every 5s for
  the "now playing" line.
- `hls.min.js` (+ `hls.min.js.LICENSE.txt`) — actual hls.js **1.6.16** UMD
  minified build, fetched from the real npm registry tarball
  (`registry.npmjs.org/hls.js/-/hls.js-1.6.16.tgz`) during this session and
  vendored as a static asset. Version chosen to match `paperweightv1`'s own
  `package.json` pin (`"hls.js": "^1.6.16"`) rather than picking an arbitrary
  version, so behavior parity with the existing product is intentional.

Wiring:
- `ServiceLocator.kt` gained `embeddedHttpServer`, composed from
  `appContext` + `appPreferences` + the existing `broadcastEngine`.
- `BroadcastService.kt` now starts `embeddedHttpServer.startServer()`
  alongside the existing `broadcastEngine.start()` in both `onCreate()` and
  `onStartCommand()`, and gained an `onDestroy()` override (didn't exist
  before) that stops the server and cancels a new internal `serviceScope`.
- After starting, the service resolves the device's current LAN IPv4 via a
  new `server/LanAddress.kt` helper (`ConnectivityManager` active-network
  link addresses, first non-loopback IPv4) and upserts `localPort`/`lanUrl`
  into the existing-but-previously-unused `StationProfileEntity` columns via
  `StationRepository`. This deliberately does **not** touch
  `StationScreen`/`StationViewModel` (still the Phase-0 error stub) — Phase 9
  is what reads this field and rewires Station's UI; Phase 5 only populates
  the data so Phase 9 has something real to display.

**Phase 5 LAN HLS playback blocker resolved:** LAN reachability and generated
AAC/HLS playback are now validated on the physical A12. The earlier failure was
not route serving; the broadcast engine had no public vault track to encode, so
`/live/playlist.m3u8` returned `404`. This pass added an explicit debug-only
`Generate Phase 5 validation tone` action that creates a real WAV in app-private
storage, inserts it into the public vault, and lets the existing broadcast engine
encode real AAC segments into `<filesDir>/hls/`.

Validation on the A12 at Wi-Fi IP `10.0.0.145`:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
# BUILD SUCCESSFUL; :app:testDebugUnitTest NO-SOURCE

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperweight.os.broadcast.Phase5ValidationToneInstrumentedTest
# Starting 1 tests on SM-A125U - 11
# Finished 1 tests on SM-A125U - 11
# BUILD SUCCESSFUL

./gradlew :app:connectedDebugAndroidTest
# Starting 15 tests on SM-A125U - 11
# Finished 15 tests on SM-A125U - 11
# BUILD SUCCESSFUL

curl -i http://10.0.0.145:8080/status
# HTTP/1.1 200 OK; application/json
# During active tone playback: nowPlayingTitle="Phase 5 validation tone",
# nowPlayingArtist="Paperweight OS", durationMs=12000, queueLength=1.
# After the 12s tone completes, nowPlayingTitle can return null while the
# generated HLS files remain served.

curl -i http://10.0.0.145:8080/live/playlist.m3u8
# HTTP/1.1 200 OK; application/vnd.apple.mpegurl
# #EXTM3U
# #EXT-X-VERSION:7
# #EXT-X-TARGETDURATION:6
# #EXT-X-MEDIA-SEQUENCE:0
# #EXTINF:6.014,
# segment-0.aac
# #EXTINF:5.967,
# segment-1.aac

curl -i -H 'Range: bytes=0-15' http://10.0.0.145:8080/live/segment-0.aac
# HTTP/1.1 206 Partial Content
# Content-Type: audio/aac
# Content-Range: bytes 0-15/98036

ffprobe -hide_banner -v error -show_entries stream=codec_name,codec_type,sample_rate,channels \
  -of default=noprint_wrappers=1 http://10.0.0.145:8080/live/playlist.m3u8
# codec_name=aac
# codec_type=audio
# sample_rate=44100
# channels=2
```

A secondary compatibility bug was found during validation: `PlaylistWriter` had
emitted `#EXT-X-MAP:URI="init.aac"` for packed ADTS AAC while `init.aac` was a
zero-byte file. `ffprobe` followed the map and failed with `HTTP error 416
Requested Range Not Satisfiable`. The playlist writer now omits `#EXT-X-MAP` for
this packed-audio HLS stream, and `ffprobe` successfully recognizes the LAN URL
as AAC stereo 44.1kHz.

Device/runtime checks also showed `BroadcastService isForeground=true`,
`LISTEN *:8080`, `mLockTaskModeState=LOCKED`, and `ResumedActivity` still
`com.paperweight.os/.MainActivity`. The remaining manual-only check is Bud's
human ear-check from another Wi-Fi device via the listener page or VLC, but the
previous technical `404`/playlist/playability blocker is resolved.

**Phase 9 (Reachability / frp tunnel) is code-complete but NOT build- or
device-validated this session** (same remote-container caveat as Phase 5
above — no `ANDROID_HOME`/SDK/`adb` here).

**The plan's open item is resolved.** This session used `add_repo` to clone
`bud-diaz/paperweightv1` read-only into this environment (it isn't a repo
this session started with in scope, but a public GitHub repo can still be
cloned via the session's git proxy) and read the real registration contract
directly out of `src/api/dashboard.js`, `src/runtime/frp-config.js`,
`src/runtime/frp-supervisor.js`, `src/telemetry/reporter.js`, and
`docs/frp-tunnel-gateway.md`, instead of guessing it. The contract:

1. `POST {PAPE_URL}/api/modules/paperweight/register` — body
   `{slug, stationKey, secret}`. `200` on success; `409` means the slug is
   already claimed by another station (this endpoint *is* the slug-claim
   mechanism — trust-on-first-use, no separate "claim" call).
2. `POST {PAPE_URL}/api/modules/paperweight/frp/tunnel/create` — header
   `x-telemetry-secret: <secret>`, body `{slug, stationKey}`. Response:
   `{hostname, serverAddr, serverPort, authToken, proxyName, subdomain}`
   (all required).
3. `frpc.toml` shape mirrors `paperweightv1`'s own `buildFrpcToml` byte-for-
   byte (`serverAddr`/`serverPort`/`auth.token`/one `[[proxies]]` block,
   `type = "http"`, `localIP = "127.0.0.1"`).
4. `stationKey` — a stable per-install identifier; mirrors
   `paperweightv1`'s `pwinst_<32-hex>` generated-once install key.
5. Default `PAPE_URL` = `https://system.paperweighthq.com` (matches
   `paperweightv1`'s own `config.js` default) — hardcoded, no UI to change it.
6. Supervisor behavior (regex-scan stdout/stderr for
   `start proxy success|login to server success|work connection registered|proxy .* started`
   to flag connected; exponential-backoff reconnect, base 2s × attempt,
   max 5 attempts; SIGTERM-then-SIGKILL-after-2s stop) mirrors
   `paperweightv1`'s own `src/runtime/frp-supervisor.js` line for line.

**The `frpc` ARM64 binary bundling gap flagged as a real risk in the plan is
NOT a gap — it's actually done.** This session fetched the official
`fatedier/frp` `v0.71.0` release tarball
(`github.com/fatedier/frp/releases/download/v0.71.0/frp_0.71.0_linux_arm64.tar.gz`,
via `add_repo` + a direct HTTPS download that this session's proxy allowed),
verified the extracted `frpc` binary really is
`ELF 64-bit LSB executable, ARM aarch64, ... statically linked` (confirmed
with `file`, not assumed), and placed it at
`app/src/main/jniLibs/arm64-v8a/libfrpc.so` (jniLibs-convention path per plan
decision #6, so Android's installer extracts it with the exec bit set).
Its Apache-2.0 `LICENSE` is vendored alongside at
`app/src/main/assets/licenses/frp-LICENSE.txt`. `AndroidManifest.xml` gained
one new attribute, `android:extractNativeLibs="true"` on `<application>` —
required so the binary is actually extracted to a real file
(`context.applicationInfo.nativeLibraryDir`) that `ProcessBuilder` can exec,
rather than left mmap'd inside the APK zip. **This is still unverified on a
real device** — a statically-linked Go ELF binary built for generic Linux
ARM64 *should* run under Android's Bionic-based userland (no glibc/dynamic-
linker dependency), and that's the standard trick for bundling Go binaries
as Android "native libraries," but nobody has actually run it on the A12 yet.
Confirm `frpc --version` (or equivalent) actually executes from
`nativeLibraryDir` as the very first Phase 9 device-validation step.

New `reachability/` package:
- `FrpRegistrationClient.kt` — the two POST calls above via the
  previously-unused `okhttp-core` dependency + `kotlinx.serialization.json`.
- `FrpcConfigWriter.kt` — writes `<filesDir>/tunnel/frpc.toml` (app-internal,
  not the SD card — it's regenerable/secret-bearing, not backed up).
- `FrpcProcessSupervisor.kt` — the `ProcessBuilder` supervisor described above.
- `ReachabilityRepository.kt` — composes registration + config-write +
  supervisor start behind `register(slug): Result<String>` /
  `disconnect()` / `status: StateFlow<TunnelStatus>`.
- `TunnelHealthCheckWorker.kt` — WorkManager periodic (~15 min) HEAD request
  against the stored public URL; only updates
  `StationProfileEntity.lastReachableAt` (new column, see below), does not
  restart `frpc` (the supervisor already self-heals). Scheduled from
  `ReachabilityRepository.register()` on first successful registration,
  cancelled from `disconnect()`.
- `ReachabilityModels.kt` — `FrpTunnelCredentials`, `TunnelStatus` (local
  types, not `network/models` DTOs — that package is being wound down
  file-by-file, no reason to add to it).

**New secrets store**: `data/prefs/SecurePreferences.kt` — `EncryptedSharedPreferences`
(the `androidx.security.crypto` dependency was declared since Phase 0 but
unused until now) holding the per-install `stationKey`, the registration
`secret`, and the frp `authToken`. Deliberately a separate file from
`AppPreferences` so these can never accidentally flow into
`AppPreferences.snapshotNonSecretConfig()` and round-trip through the
automatic SD-card backup — the Keystore key backing it does not survive
reinstall/factory reset. `AppPreferences` gained a plain (non-secret)
`stationSlug` field instead, since the slug itself is just the public
subdomain, not a credential — it's now part of `NonSecretConfig` and
round-trips through normal backup/restore.

**`RecoveryInfoExporter.kt` (Phase 3 placeholder) now has real content**: it
reveals the registration secret and frp auth token on-screen (via Settings'
existing "Show recovery info" button) once they exist, instead of the
Phase 3 explanatory-only placeholder text.

**Schema change — read before installing on the physical A12:**
`StationProfileEntity` gained `lastReachableAt: Long?` and
`AppDatabase.DATABASE_VERSION` was bumped **1 → 2**. Because v1 uses
`fallbackToDestructiveMigration()` (an accepted v1-only shortcut per this
file's Phase 1 notes), the **first app launch after installing this build
will destroy the existing Room database** on Bud's physical A12 — including
whatever vault tracks/schedule/token data is already there from Phase 2/3
testing. **Back up first** (Settings → Back up now) if that data matters,
then restore after reinstalling, or accept the loss if the device only has
disposable test data on it right now. The exported Room schema for version 2
(`app/schemas/com.paperweight.os.data.db.AppDatabase/2.json`) now exists in the
repo after the real local Gradle build run during Phase 5 closeout.

**Station screen fully rewritten, not just trimmed.** The old Retrofit-era
`StationScreen`/`StationViewModel`/`StationUiState` (Cloudflare tunnel setup,
PaperweightHQ telemetry-secret paste, directory searchability toggle,
setup-progress checklist, product-updates signup) is gone entirely — the
Phase 9 build-order description in the plan only calls for "LAN URL, public
frp-tunneled URL, QR codes, tunnel connection status," and nothing in that
list needed any of the old surface, so it was replaced rather than patched.
`network/models/StationModels.kt` is now **fully deleted** (confirmed nothing
else imports it) rather than just trimmed of its Cloudflare-specific types —
every type in that file was Retrofit-era station/telemetry/signup DTO shape
with no place in the new design. New `ui/components/QrCode.kt` wraps
`com.google.zxing:core` (declared since Phase 0 specifically for "QR
generation only," unused until now) for the public-URL QR code.

Settings screen gained a small read-only "Public reachability" panel (slug +
tunnel status text) — registration/slug entry itself lives on Station, per
the plan's file split.

**Known real gaps, not yet exercised (all real-device-only):**
1. Whether `frpc` actually executes from `nativeLibraryDir` on the A12 (see above).
2. A real end-to-end register → create-tunnel round trip against the live
   `system.paperweighthq.com` — this session could not reach it (no network
   egress test attempted beyond fetching `paperweightv1`'s source and the
   `frp` release binary; the actual registration API was never called).
3. Whether the destructive Room migration above is acceptable or needs a
   backup-first pass on the physical device.

**Phase 11 (Earnings deferral) is code-complete but NOT build- or
device-validated this session** (same caveat as Phases 5/9 above).

`EarningsScreen`/`EarningsViewModel`/`EarningsUiState` are **rewritten, not
extended**. The Phase-0 stub set `state` to a permanent `ScreenState.Error`,
but `EarningsScreen.kt` itself was, unusually, a fully-built revenue/tips/
subscriptions UI (`RevenueHeroPanel`, `RevenueMixPanel`, `SupportPanel`,
`TipConfigForm`, `TopEarnersPanel`) that had simply never been reachable
because `ScreenStateScaffold` never calls `content()` while `state` is
`Error`. All of that was deleted rather than kept dormant — leaving a full
hidden payments UI in place would have been exactly the kind of
fake-normal surface this file's decision #2 ("honest local state, not
fabricated Content") warns against, the moment anyone flipped the state to
`Content` in a later change.

New shape: `EarningsUiState` now carries only `pricedPublicTrackCount` /
`lowestSuggestedPriceCents` / `highestSuggestedPriceCents` /
`actionMessage` — no revenue, no tips, no subscriptions. `EarningsViewModel`
moved from the permanent `Error` stub to a real `Content` state, sourced
from `VaultRepository.observeTracks()` (already composed in
`ServiceLocator`, no new DI needed) filtered to public tracks with a
suggested price set. `EarningsScreen` is now a static "Coming soon." header
plus one `PricingSummaryPanel` showing that inert count/range — explicit
copy that nothing is charged and payments aren't part of v1.
`openPaymentSettings()` stays a no-op that reports the same message;
`saveTipConfig()` was removed entirely (there's no tip UI left to call it).

Confirmed by reading the code (not new plumbing): vault pricing
(`suggestedPriceCents`/`minimumPriceCents`/`allowFree` on
`VaultTrackEntity`) already round-trips end-to-end via
`VaultViewModel.saveLocalTrackPricing()` /
`VaultScreen.kt`'s `LocalTrackPriceForm`, built in Phase 2. Nothing new was
needed here beyond reading it for the Earnings summary above.

Deleted: `network/models/DashboardEarningsModels.kt` (only imported by
Earnings' own three files, confirmed via grep before deleting),
`network/models/SettingsModels.kt` and `network/models/StreamModels.kt`
(zero importers anywhere in the app, already fully orphaned before this
session touched them). See decision #1's correction above for what's still
left in `network/models/` and why.

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
   **Correction after Phase 11 (this session):** "fully gone by the time
   Phase 11 finishes" turned out to describe *Earnings' own slice*, not the
   literal whole package — Phase 11 deleted `DashboardEarningsModels.kt`
   (Earnings' own DTOs) plus `SettingsModels.kt`/`StreamModels.kt` (already
   fully orphaned, zero importers anywhere), but six files
   (`DashboardAnalyticsModels.kt`, `AudienceModels.kt`, `VaultModels.kt`,
   `LibraryModels.kt`, `BroadcastModels.kt`, `ScheduleModels.kt`) are still
   genuinely imported by Analytics/Overview, Audience, Vault, Broadcast, and
   Schedule — screens whose own rewrite phases (6, 7, 8, 10) weren't in this
   session's scope. The package can't be literally empty until those phases
   run; don't be misled by the original phrasing here.
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

**Phases 5, 9, and 11 are all code-complete this session but share one
blocking gap: none of it has run through a real Kotlin compiler or on a real
device yet.** This session ran entirely in a remote container with JDK 21 and
no `ANDROID_HOME`/SDK/`adb` — every phase below was written and carefully
self-reviewed, not built. Do this, in order, on Bud's machine before treating
any of the three as closed:

1. **Back up first.** Phase 9 bumped `AppDatabase` version 1→2
   (`fallbackToDestructiveMigration()` will wipe the physical A12's existing
   Room DB on first launch of this build). Run Settings → Back up now on the
   *current* installed build before installing this one, if the existing
   vault/schedule/token data on the device matters.
2. `./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest` —
   first real compile of all three phases' code. Fix whatever a real compiler
   finds; nothing here has been machine-verified yet.
3. `adb install -r` + smoke: confirm boot still reaches the dashboard,
   `BroadcastService` still runs foreground, lockTask still holds.
4. **Phase 5 check:** from a second device on the same Wi-Fi, open
   `http://<lan-ip>:<port>/` in a browser (bundled hls.js listener page) and
   `http://<lan-ip>:<port>/live/playlist.m3u8` in VLC — both should play
   audio once a public vault track exists.
5. **Phase 9 checks, in order:** confirm `frpc` actually executes from
   `context.applicationInfo.nativeLibraryDir` (the jniLibs-bundled binary has
   never been run — see the Phase 9 section above); then a real
   register/create-tunnel round trip against `system.paperweighthq.com` with
   a real slug from the Station screen; then confirm the public URL plays in
   a browser from outside the LAN.
6. **Phase 11 check:** open Earnings, confirm the "Coming soon" shell renders
   with the correct priced-track count/range instead of the old revenue UI
   or an error state.
7. Run `./gradlew :app:connectedDebugAndroidTest` — no new instrumented tests
   were added this session (none of the three phases had an obvious
   device-only behavior worth a *new* test beyond what steps 3–6 already
   exercise manually), so this just confirms nothing from Phases 0–4's
   existing suite regressed.

Phases 6, 7, 8, 10, and 12 (mic go-live, scheduling, access tokens,
analytics/audience, final polish) were **not** touched this session — the
user asked specifically for phases 5, 9, and 11, in that order, skipping the
others for now. Pick those up per the plan file when scoped.

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
├── di/ServiceLocator.kt             // + embeddedHttpServer (Phase 5), + securePreferences/reachabilityRepository (Phase 9)
├── broadcast/                       // Phase 4; BroadcastService now also owns the embedded server (Phase 5)
│   ├── BroadcastEngine.kt / BroadcastService.kt / BroadcastState.kt
│   ├── decode/TrackDecoder.kt
│   ├── encode/AacEncoder.kt / AdtsHeaderWriter.kt
│   └── hls/PlaylistWriter.kt / SegmentWriter.kt / SegmentStore.kt
├── server/                          // NEW (Phase 5) — NanoHTTPD
│   ├── EmbeddedHttpServer.kt / RangeResponse.kt / LanAddress.kt
│   └── routes/PlaylistRoute.kt / SegmentRoute.kt / StatusRoute.kt / ListenerWebRoute.kt
├── reachability/                    // NEW (Phase 9) — frp tunnel
│   ├── FrpRegistrationClient.kt / FrpcConfigWriter.kt / FrpcProcessSupervisor.kt
│   ├── ReachabilityRepository.kt / ReachabilityModels.kt / TunnelHealthCheckWorker.kt
├── network/models/                 // KEPT (see Key decisions #1, corrected after Phase 11)
│   ├── AudienceModels.kt / BroadcastModels.kt / DashboardAnalyticsModels.kt
│   ├── LibraryModels.kt / ScheduleModels.kt / VaultModels.kt
│   // StationModels.kt, DashboardEarningsModels.kt, SettingsModels.kt, StreamModels.kt
│   // deleted (Phases 9 and 11) — see their HANDOFF sections above
└── ui/
    ├── theme/                      // untouched
    ├── nav/                        // untouched
    ├── components/                 // + QrCode.kt (Phase 9, zxing)
    ├── setup/SdCardRequiredScreen.kt  // NEW (Phase 0)
    └── dashboard/                  // Vault (Phase 2), Overview/Broadcast (Phase 4),
                                     // Settings (Phase 3, + reachability panel Phase 9),
                                     // Station (Phase 9, full rewrite),
                                     // Earnings (Phase 11, static shell) all rewired;
                                     // schedule/vault-legacy-panels/audience/analytics
                                     // still use Phase 0 Error stubs or legacy no-ops
        ├── overview/ broadcast/ schedule/ vault/ station/
        └── audience/ analytics/ earnings/ settings/

app/src/main/assets/
├── fonts_licenses/                  // unchanged
├── licenses/frp-LICENSE.txt         // NEW (Phase 9) — Apache-2.0 for the bundled frpc binary
└── listener/                        // NEW (Phase 5) — vendored static player: index.html,
                                      // player.js, styles.css, hls.min.js (+ its LICENSE)

app/src/main/jniLibs/arm64-v8a/libfrpc.so   // NEW (Phase 9) — real fatedier/frp v0.71.0 ARM64 binary
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
