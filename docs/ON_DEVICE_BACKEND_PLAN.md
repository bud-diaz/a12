# Paperweight OS — pivot to on-device backend (no pairing, no remote server)

> Copied into the repo from the approved planning-session output so it
> persists across sessions/environments (the original lived only in an
> ephemeral Claude Code plan file). Treat this as the authoritative plan for
> the on-device-backend pivot alongside `HANDOFF.md`'s running status.

## Context

The app was originally scoped as a thin client: on first boot it shows a QR
scanner, pairs against an *existing* Paperweight Studio web dashboard running
elsewhere, and all nine dashboard screens (Overview, Broadcast, Schedule,
Vault, Station, Audience, Analytics, Earnings, Settings) talk to that remote
Express backend over Retrofit.

The user wants the opposite: the Galaxy A12 itself becomes the backend. No
pairing, no remote server, no cloud dependency for the core product. On boot
the device should scan a local media vault, run a continuous HLS "radio"
broadcast from that vault (with an audio-only mic "Go live" override), serve
a listener web player and the stream itself over an embedded HTTP server, and
give the operator full local read/write control over scheduling, vault
content/pricing, access tokens, and analytics — through the native Compose
dashboard that's already scaffolded. Payments/tips are explicitly deferred
(no processor, no webhooks, no local IOU ledger — just inert price metadata
and a "coming soon" Earnings screen). Public internet reachability is v1 via
DDNS + a one-time manual router port-forward, not an embedded tunnel binary
(later revised to frp — see decision #6 below).

Two more hardware/operational requirements: the app requires a removable SD
card of at least 2GB to be inserted (a hard boot gate, not a soft warning),
and the SD card becomes the default vault storage location. Separately, the
app needs a periodic local backup system that snapshots app data to that SD
card. This isn't about defeating FRP (Factory Reset Protection) — the user
was explicit they weren't able to circumvent it and isn't trying to — it's
about making a *legitimate* re-provisioning (fresh `adb install` +
`dpm set-device-owner`, per this project's existing, sanctioned provisioning
flow) faster to recover from if the device ever factory-resets, since Google/
Samsung account-based backup isn't available on a de-Googled device.

**Important correction from exploration:** all nine dashboard screens already
have full `Screen`/`UiState`/`ViewModel` implementations wired to Retrofit —
not stubs. Several carry surface area that only makes sense with a real
multi-tenant cloud backend behind them (Station: Cloudflare tunnel
provisioning, PaperweightHQ telemetry registration, studio-signup prompts;
Vault: email-based token *assignment* to listener accounts; Audience:
automations/marketing-email sends, polls, participation requests, external
platform search). Porting that verbatim would just reintroduce a cloud
dependency by another name, so cutting it is a first-class deliverable of
this plan, not an oversight — see the scope table below.

## Scope: keep vs. drop

| Feature (found in current code) | Fate |
|---|---|
| Station pairing, remote base URL/session | **Drop** — superseded entirely |
| Cloudflare-specific tunnel UI (token/zones/auto-tunnel via Cloudflare's API) | **Drop** — paperweightv1 itself has already moved off Cloudflare |
| PaperweightHQ telemetry/tunnel registration (`registerTelemetry`, `createHqTunnel`, `paperweighthqTunnelAvailable`) | **Keep, repurposed** — paperweightv1 swapped its own Cloudflare tunnel for frp; mirror that swap here instead of building a separate DDNS mechanism (see reachability decision below) |
| Studio-signup prompt | **Drop** — no cloud studio to sign up for |
| Listener accounts (email-bound token assignment, password reset links) | **Drop** — no accounts backend anymore; tokens become opaque local bearer links with a free-text label |
| Automations, marketing sends, polls, participation requests, external-platform search/import, "creator type"/"radio host" toggle | **Drop** — out of the requested scope, no infra to support email/marketing sends |
| Docs viewer, notify-webhook, feed settings (Settings screen) | **Drop** — Settings gets rebuilt around device/server config |
| Subscriptions | **Drop** — no payment processing exists to originate one |
| Vault pricing metadata (suggested/min price, allow-free, payment type) | **Keep**, stored and editable, never enforced |
| Vault access tokens gating private tracks | **Keep**, redesigned as local bearer tokens (label only, no email) |
| Scheduling (dayparting blocks, smart playlists) | **Keep**, re-hosted locally |
| Analytics (listener counts, top tracks, history) | **Keep**, computed from local listen events |
| Earnings/tips | **Keep as UI shell only** — static "coming soon" state |

## Key technical decisions

1. **HLS pipeline — hand-rolled, no ffmpeg-kit.** `MediaMuxer` can't write MPEG-TS
   at all, and ffmpeg-kit-android is discontinued/oversized for this. Use HLS's
   packed-audio mode instead: raw ADTS-framed AAC segments (`.aac` files)
   referenced straight from the `.m3u8`, no container muxing needed. Pipeline:
   `TrackDecoder` (MediaExtractor+MediaCodec) → fixed-format PCM (44.1kHz
   stereo) → `AacEncoder` (MediaCodec AAC-LC, ~128kbps) → `AdtsHeaderWriter` →
   `SegmentWriter`/`PlaylistWriter` (atomic temp-then-rename `.m3u8`, ~6s
   segments, live-sliding window). Mic capture (`AudioRecord`) feeds the same
   PCM pipeline so "Go live" swaps the source at a segment boundary with no
   listener-side reconnect.
2. **Embedded HTTP server — NanoHTTPD**, not Ktor: lighter for a low-end A12,
   only needs to serve static listener assets, a live-sliding `.m3u8`, range-
   served `.aac` segments, and small JSON status calls.
3. **QR — generation only**, `com.google.zxing:core`. Drop CameraX (4 modules)
   and MLKit barcode-scanning entirely; they existed only for the now-removed
   scan-to-pair flow.
4. **Local DB — Room + KSP** (first use of KSP in this project).
5. **Scheduling — WorkManager for peripheral jobs only** (DDNS refresh,
   analytics rollup, vault maintenance). WorkManager's 15-minute-minimum,
   Doze-deferred firing can't drive second-precision rotation switching, so
   real-time "what plays next" decisions live inside `BroadcastEngine`'s own
   loop, checked against `ScheduleBlockEntity` every time a track/segment
   ends. Scheduled live-mic windows can only ever be a reminder notification
   (a human has to be there to talk) that reverts to rotation if unattended.
6. **Reachability — frp tunnel, matching paperweightv1's own Cloudflare→frp
   swap**, not DDNS+port-forward. paperweightv1 already replaced its
   Cloudflare tunnel with frp; this device registers against the *same*
   frps the real Studio stations tunnel through (the existing
   `paperweighthqTunnelAvailable`/`registerTelemetry`/`createHqTunnel` API
   surface, previously slated for removal, is repurposed for this instead of
   deleted). This is a deliberate, scoped exception to "fully standalone":
   the app's data/logic/media all stay fully local — only the public-
   reachability hop depends on an external frps, exactly the way any
   internet-facing device depends on *some* network path. The upside over
   DDNS+port-forward is real: no operator-side router configuration at all,
   since frp's outbound-dial-from-behind-NAT model needs nothing forwarded.

   frpc is a standalone Go binary + TOML config, not a Kotlin/Java library,
   so: bundle the prebuilt ARM64 `frpc` binary **as a `jniLibs`-convention
   asset** (`src/main/jniLibs/arm64-v8a/libfrpc.so`, not a raw `assets/`
   file) — Android's install process extracts anything under `jniLibs` to
   the app's native-library directory and marks it executable, which is the
   standard, reliable way to ship an arbitrary native executable that needs
   to run as a subprocess on modern Android; a plain `assets/` file usually
   ends up on a noexec-mounted volume. A `reachability/` component generates
   `frpc.toml` at runtime from the registration response (server addr,
   token, remote port/subdomain), launches `frpc` as a supervised
   `ProcessBuilder` subprocess (owned by the same long-lived service as the
   broadcast engine, restart-on-crash, not a periodic `WorkManager` job —
   frpc needs to stay connected continuously, not run on a schedule), and a
   lightweight periodic health check (WorkManager, ~15 min) just confirms
   the public URL still answers.

   **Open item, deferred by design:** the exact registration wire format
   (what `registerTelemetry`/`createHqTunnel` now expect/return post-swap,
   and the frps connection details) lives in `paperweightv1`, which isn't in
   this session's repo scope. The user will run Phase 9 in a local session
   where `paperweightv1` is readily available, reading its actual
   frp-registration server route + frpc config template directly instead of
   guessing the contract here. All other phases don't depend on this and
   can proceed in this environment; Phase 9 is explicitly earmarked for that
   local session.
7. **Access tokens vs. pricing are separate concepts.** Pricing is inert
   metadata on `VaultTrackEntity`. Access gating (`visibility` = public/
   private) means a private track is *never* in the public rotation; it's
   reachable only via a one-time pre-baked VOD HLS encode
   (`/vault/{id}/playlist.m3u8?t=TOKEN`), reusing the same encode pipeline in
   a "freeze after N segments" mode instead of "roll forever." The server
   checks `?t=` against `ListenerTokenEntity` before serving that manifest or
   its segments. (HLS is inherently one-stream-to-everyone, so gating inside
   the *live* rotation isn't sound — gating a separate per-track VOD stream
   is the correct shape.)
8. **Foreground service.** `BroadcastService` runs continuously and declares
   both `specialUse` (default: encode+serve, no mic — needs a
   `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest property) and `microphone`
   (added only while live) as its foreground service types, toggled at
   runtime via `ServiceCompat.startForeground`. Since the app ships via `adb
   install` only, `specialUse`'s Play-Console-review friction doesn't apply.
   Confirmed compatible with kiosk scope: `DeviceOwnerPolicy.apply()` never
   disables the status bar, so a foreground-service notification is already
   a reachable, non-competing system surface today. Note as an operational
   follow-up (not built in this plan): battery-optimization exemption +
   partial wake lock for genuine 24/7 unattended reliability.
9. **`DeviceOwnerPolicy.apply()`** currently silently grants `CAMERA` via
   `setPermissionGrantState`. Once `CAMERA` is removed from the manifest,
   that call throws `IllegalArgumentException` at *every* boot and breaks the
   whole boot-to-dashboard flow — this one-line fix is required even though
   `admin/` is otherwise out of scope for this pivot.
10. **SD card requirement — hard boot gate, not a warning.** `SdCardDetector`
    enumerates storage volumes via `ContextCompat.getExternalFilesDirs(ctx,
    null)`, identifies the removable one with `Environment
    .isExternalStorageRemovable(dir)` + `getExternalStorageState(dir) ==
    MEDIA_MOUNTED`, and checks capacity with `StatFs`. If no removable
    volume is found, or its total capacity is under 2GB, the boot chain
    stops at a blocking "Insert an SD card (2GB+) to continue" screen —
    same severity tier as the existing Device-Owner-claim gate, just a step
    later in the chain (Device Owner → SD card present/sized → vault/backup
    restore offer → dashboard). Also register for `ACTION_MEDIA_MOUNTED`/
    `ACTION_MEDIA_EJECTED` so a card pulled *during* operation degrades the
    running app to a visible "SD card removed" state (broadcast/vault reads
    would fail) instead of crashing, consistent with the project's existing
    "no crash" stance on failure states.

    SAF access to the card (needed for both the vault tree and the backup
    folder below) still requires one manual "choose this folder" tap from
    the operator at first run — Device Owner's silent `setPermissionGrantState`
    only covers runtime permissions, not SAF tree grants, so this one step
    can't be fully automated. It happens once; the resulting tree URI is
    persisted (`takePersistableUriPermission`) and never asked again.
11. **Default vault path = the SD card**, not internal storage. The picker
    (§ vault ingestion) still selects source files from wherever they live,
    but `VaultFileStore` copies ingested audio into a `Paperweight/vault/`
    folder inside the SAF tree granted in decision #10, so the working vault
    is portable and self-contained on removable media.
12. **Periodic backup system, targeting the same SD card.** Since vault media
    already lives on the SD card and a standard Android factory reset wipes
    internal storage but not an inserted SD card (unless the operator also
    ticks "erase SD card" in the reset dialog — call this out explicitly in
    `provisioning/setup.sh`'s comments and in CLAUDE.md's provisioning
    section as a "leave unchecked" instruction), the actual data at risk on
    a reset is the Room DB (vault metadata, schedule, tokens, analytics) and
    `AppPreferences` config — not the media files themselves. So backups
    only need to snapshot DB + config, which keeps them small and fast
    regardless of vault size:
    - `BackupWriter` does a Room `VACUUM INTO` snapshot (clean, consistent
      copy without pausing the live DB) + a JSON export of the non-secret
      parts of `AppPreferences` + a `manifest.json` (timestamp, app version,
      schema version), all written to `Paperweight/backups/<timestamp>/` in
      the same SAF tree as the vault.
    - `BackupScheduler`/`BackupWorker` run this on a WorkManager periodic
      job (interval configurable in Settings, default daily), plus a manual
      "Back up now" action; `BackupPruner` keeps the last N snapshots (e.g.
      7) and deletes older ones.
    - **Secrets don't round-trip through this backup.** `AppPreferences`'
      encrypted fields (frp/DDNS tokens, once decision #6's Phase 9 lands)
      are `androidx.security.crypto`-encrypted with an Android-Keystore-
      backed key; that key does not survive a reinstall/reset (a fresh
      install gets a fresh Keystore key), so backing up the ciphertext alone
      would be useless. Instead: exclude secret fields from the automatic
      backup by design, and add a one-time `RecoveryInfoExporter` action in
      Settings ("show recovery info") that reveals those secrets on-screen/
      as a QR code so the operator can note them down externally, to be
      re-entered by hand after a real re-provisioning.
    - `RestoreManager` runs *before* `AppDatabase` is first opened (Room
      creates an empty DB on first open otherwise) — hooked into the same
      boot-gate chain as decision #10: if a valid `Paperweight/backups/`
      folder is found on the inserted card during first-run setup, offer
      "Restore from backup" vs. "Start fresh" before proceeding, and if
      restore is chosen, copy the snapshot's DB file into place and replay
      the preferences JSON before any repository touches Room.
    - Not verifiable from this sandboxed environment: whether Samsung's
      factory-reset flow on this specific A12 build genuinely leaves an
      inserted SD card untouched by default. Treat as a real-device
      verification item (see Verification below), not an assumption.

## New package layout

```
app/src/main/java/com/paperweight/os/
├── data/db/{AppDatabase,Converters}.kt
├── data/db/entity/  VaultTrackEntity, VaultCollectionEntity,
│                    VaultCollectionTrackCrossRef, VaultHighlightEntity,
│                    ScheduleBlockEntity, SmartPlaylistEntity,
│                    ListenerTokenEntity, AnalyticsEventEntity,
│                    AnalyticsDailyRollupEntity, ListenerSessionEntity,
│                    StationProfileEntity
├── data/dao/  VaultDao, ScheduleDao, TokenDao, AnalyticsDao, StationDao
├── data/prefs/AppPreferences.kt        // EncryptedSharedPreferences: DDNS creds, port, station name
├── data/repository/  VaultRepository, ScheduleRepository, TokenRepository,
│                      AnalyticsRepository, StationRepository, BroadcastRepository
├── storage/
│   ├── SdCardDetector.kt               // enumerates volumes, finds removable SD + capacity
│   └── SdCardMountState.kt             // observable mount/eject state for graceful degradation
├── backup/
│   ├── BackupWriter.kt                 // Room VACUUM INTO + prefs JSON + manifest.json
│   ├── BackupScheduler.kt / BackupWorker.kt
│   ├── BackupPruner.kt                 // retention: keep last N snapshots
│   ├── RestoreManager.kt               // pre-Room-init restore-from-SD-card flow
│   └── RecoveryInfoExporter.kt         // one-time on-screen/QR reveal of secrets that can't survive a Keystore reset
├── vault/  VaultIngestor (SAF picker), MetadataExtractor, VaultFileStore (writes into Paperweight/vault/ on the SD card)
├── broadcast/
│   ├── BroadcastService.kt, BroadcastEngine.kt
│   ├── decode/TrackDecoder.kt
│   ├── encode/{AacEncoder,AdtsHeaderWriter}.kt
│   ├── mic/MicCapture.kt
│   └── hls/{SegmentWriter,PlaylistWriter,SegmentStore}.kt
├── server/
│   ├── EmbeddedHttpServer.kt (NanoHTTPD, binds 0.0.0.0:<port>)
│   ├── routes/{ListenerWebRoute,PlaylistRoute,SegmentRoute,VaultVodRoute,StatusRoute,TelemetryRoute}.kt
│   └── RangeResponse.kt
├── scheduling/{RotationPlanner,SmartPlaylistResolver,ScheduleWorker}.kt
├── reachability/
│   ├── FrpRegistrationClient.kt        // talks to paperweightv1's HQ registration endpoint
│   ├── FrpcConfigWriter.kt             // writes frpc.toml from the registration response
│   ├── FrpcProcessSupervisor.kt        // launches/restarts the bundled frpc subprocess
│   ├── ReachabilityRepository.kt
│   └── TunnelHealthCheckWorker.kt      // periodic (~15min) "is the public URL up" check only
├── (native binary: src/main/jniLibs/arm64-v8a/libfrpc.so — bundled frpc, jniLibs convention for exec bit)
├── tokens/{TokenGenerator,TokenValidator}.kt
├── di/ServiceLocator.kt                // replaces ApiClient as composition root
└── (existing admin/, provisioning/, ui/, MainActivity.kt — see file fates below)

app/src/main/assets/listener/  index.html, player.js, styles.css, hls.min.js (vendored, not CDN)
```

## File fates

**Delete:** `network/ApiClient.kt`, `SessionStore.kt`, `SessionCookieJar.kt`,
`DynamicBaseUrlInterceptor.kt`, `AuthApi.kt`, `StreamApi.kt`, `LibraryApi.kt`,
`DashboardBroadcastApi.kt`, `DashboardAnalyticsApi.kt`,
`DashboardEarningsApi.kt`, `DashboardScheduleApi.kt`, `DashboardStationApi.kt`,
`DashboardVaultApi.kt`, `DashboardAudienceApi.kt`, `DashboardSettingsApi.kt`,
all of `network/models/*.kt`, and the entire `pairing/` package
(`PairingActivity`, `PairingViewModel`, `PairingScreen`, `QrCodeAnalyzer`).
`network/` and `pairing/` end up empty and get removed.

> **Deviation, recorded in HANDOFF.md:** Phase 0 actually kept
> `network/models/*.kt` in place (deleting only the transport layer). See
> HANDOFF.md's "Key decisions" for why — the DTOs are still load-bearing for
> every screen's `UiState.kt` until each screen is individually rewired.

**Repurpose in place:** `MainActivity.kt` (drop the pairing branch, add the
SD-card gate, start `BroadcastService`), `admin/DeviceOwnerPolicy.kt` (fix
per decision #9, add RECORD_AUDIO/POST_NOTIFICATIONS grants),
`provisioning/SetupActivity.kt` (gains the SD-card-required screen and the
first-run "restore from backup vs. start fresh" offer — the boot-gate chain
now runs Device Owner → SD card present/sized → restore-or-fresh → SAF tree
grant → dashboard), and every dashboard Screen/UiState/ViewModel trio —
rewired from `ApiClient`/Retrofit to the new repositories, trimmed per the
scope table (Station loses tunnel/telemetry UI and gains frp status; Vault
loses email token assignment; Audience loses automations/marketing/polls;
Settings rebuilt around server/frp/backup config; Earnings gutted to a
static empty state). `ui/nav/*`, `ui/components/*`, `ui/theme/*` stay
untouched — same routes, same composable signatures.

**Untouched:** `admin/BootReceiver.kt`, `admin/PaperweightDeviceAdminReceiver.kt`,
`provisioning/setup.sh` (though its comments should note the "don't erase SD
card on factory reset" guidance per decision #12).

## Gradle changes (`app/build.gradle.kts`, `gradle/libs.versions.toml`)

- **Remove:** retrofit-core, retrofit-kotlinx-serialization-converter,
  okhttp-logging-interceptor, camerax-{core,camera2,lifecycle,view},
  mlkit-barcode-scanning.
- **Keep:** androidx.security.crypto (repurposed for frp registration
  secrets), kotlinx-serialization-json (server JSON responses + backup
  manifest/preferences export), okhttp-core (kept slim, used for the frp
  registration call once Phase 9 lands), kotlinx-coroutines-android, all
  compose/navigation-compose/material-icons-extended deps.
- **Add:** `androidx.room:room-{runtime,ktx,compiler}` via KSP (add the KSP
  plugin — new to this project), `androidx.work:work-runtime-ktx`,
  `org.nanohttpd:nanohttpd:2.3.1`, `com.google.zxing:core`.
- Room strategy for v1: `fallbackToDestructiveMigration()`, explicitly a
  v1-only shortcut to revisit once real operator data exists on-device.

## AndroidManifest changes

- **App install location stays internal, not the SD card.** The SD card
  requirement (decisions #10–12) is exclusively about vault media + backup
  storage — the APK itself installs to internal storage as normal. No
  `android:installLocation` change is needed (the manifest doesn't currently
  set one, which already defaults to `internalOnly`-equivalent behavior);
  just don't introduce `preferExternal`/`auto` while making the other
  manifest edits below.
- **Remove:** `CAMERA` permission + `camera.any` feature, `PairingActivity`.
- **Add permissions:** `RECORD_AUDIO`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `ACCESS_WIFI_STATE`,
  `WAKE_LOCK`.
- **Not added, deliberately:** no `READ_MEDIA_AUDIO` and no
  `MANAGE_EXTERNAL_STORAGE` (vault + backup storage are SAF-tree, per-URI
  grants on the SD card, not broad filesystem access); no cleartext-traffic
  manifest change (Network Security Config governs the app's *outbound*
  connections, not inbound requests to its own NanoHTTPD server).
- **Add service:**
  ```xml
  <service android:name=".broadcast.BroadcastService" android:exported="false"
      android:foregroundServiceType="specialUse|microphone">
      <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
          android:value="internet_radio_broadcast_encoding" />
  </service>
  ```

## Phased build order

0. **Groundwork** — delete `pairing/`+`network/`, gradle swap, manifest
   changes, fix `DeviceOwnerPolicy`, rewire `MainActivity` past pairing, add
   the `storage/` SD-card-required boot gate (decision #10). App boots
   straight to (still-broken) dashboard once a valid card is present.
1. **Local data layer** — Room DB, entities/DAOs, `AppPreferences`,
   repositories, `ServiceLocator`. Use a fixed, known DB filename so
   Phase 3's `RestoreManager` can find/replace it predictably. Verify with
   in-memory-Room unit tests.
2. **Vault ingestion** — SAF picker (one-time SD-card tree grant, per
   decision #10), metadata extraction, `VaultFileStore` writing into
   `Paperweight/vault/` on the card (decision #11); wire Vault screen's
   "Add to vault."
3. **Backup & recovery** — `BackupWriter`/`BackupScheduler`/`BackupPruner`
   writing `Paperweight/backups/` snapshots to the same SD card tree;
   `RestoreManager` wired into `SetupActivity`'s first-run flow (offer
   restore vs. fresh before Room opens); `RecoveryInfoExporter` +
   backup-now/interval controls in Settings (decision #12).
4. **Broadcast engine core** — decode/encode/segment/playlist pipeline,
   `BroadcastEngine`, `BroadcastService` as foreground service; rewire
   Overview + Broadcast screens.
5. **Embedded server + listener player** — NanoHTTPD routes with Range
   support, vendored listener web assets. End state: LAN playback works.
6. **Mic go-live** — `MicCapture`, source-switching, foreground-service-type
   toggling, Broadcast screen "Go live" control.
7. **Scheduling** — block/smart-playlist CRUD, `RotationPlanner` integrated
   into `BroadcastEngine`, live-slot reminder notification; rewire Schedule.
8. **Access tokens & private vault gating** — token gen/validation, VOD
   pre-encode reusing the Phase-4 pipeline, `VaultVodRoute`; rewire Vault's
   token UI to label-only.
9. **Reachability** — confirm the real frp registration contract against
   `paperweightv1` first (see open item above; this phase runs in the user's
   local session), then `FrpRegistrationClient`/`FrpcConfigWriter`/
   `FrpcProcessSupervisor` + health-check worker; rewire Station (LAN URL,
   public frp-tunneled URL, QR codes, tunnel connection status) and Settings
   (server port, frp registration state).
10. **Analytics & Audience** — heartbeat-based listener sessions, event/
    rollup tables + worker; rewire Analytics and trimmed Audience screens.
11. **Earnings deferral** — static "coming soon" state; confirm vault
    pricing fields round-trip as inert metadata.
12. **Polish & end-to-end verification** (see below).

## Process: HANDOFF.md discipline

`HANDOFF.md` is the project's running cross-session log. For every phase in
the build order above:

- **Before starting the phase**, read `HANDOFF.md` in full to pick up the
  real state of the repo.
- **Before every `git push`**, update `HANDOFF.md` first: what changed, what
  now builds/runs/is verified vs. still unverified, and any new "key
  decision" worth not re-litigating. Commit the `HANDOFF.md` update together
  with (or immediately before) the code it describes.
- **At the wrap-up of each phase**, do a fuller pass: move that phase's items
  out of "What's left" and into "Status," refresh the "Repo layout" tree if
  packages were added/removed/renamed, and note any deviation from this plan
  so the next session isn't misled by a plan that's since diverged from
  reality.

## Verification

1. `./gradlew :app:assembleDebug` builds clean.
2. Boot with **no** SD card inserted; confirm the blocking "insert a 2GB+ SD
   card" gate appears and the app does not proceed past it. Insert a card
   under 2GB (or reuse a known-small one); confirm it's still rejected. Then
   insert a valid card; confirm the gate clears.
3. `adb install -r` onto a provisioned Galaxy A12 with a valid card inserted;
   confirm boot goes straight to Overview, no pairing screen, and grant the
   one-time SAF folder picker for the SD card when prompted.
4. `adb push` sample audio files, ingest via the in-app picker on the Vault
   screen, confirm extracted metadata appears and the files land under
   `Paperweight/vault/` on the card (inspect via `adb shell` or a card
   reader), not internal storage.
5. Trigger "Back up now" in Settings; confirm a new `Paperweight/backups/
   <timestamp>/` snapshot appears on the card with a DB file, prefs JSON,
   and manifest. Wipe app data (`adb shell pm clear com.paperweight.os`,
   leaving the SD card in place) and reinstall; confirm `SetupActivity`
   detects the existing backup and offers restore, and that choosing restore
   brings back the same vault metadata/schedule/tokens without re-ingesting
   media (the media itself never left the card).
6. Broadcast screen shows a "playing now" track; confirm the foreground-
   service notification is present and doesn't interfere with lockTask.
7. **LAN check** from another device on the same WiFi: Station screen's LAN
   URL opened in VLC (`Media > Open Network Stream` →
   `http://<lan-ip>:<port>/live/playlist.m3u8`) plays audio; the same root
   URL in a browser loads the bundled HLS.js player and plays audio.
8. Toggle "Go live," speak into the A12 mic, confirm the stream cuts over
   within one segment interval and reverts cleanly on toggle-off.
9. Create a private vault track + token; confirm a wrong/missing token is
   rejected, the correct token plays, and the track never appears in the
   public rotation.
10. Confirm Overview/Analytics listener counts increment while a listener
    session is open and decay after it closes (heartbeat timeout).
11. Confirm the `frpc` subprocess launches, connects to frps, and
    `TunnelHealthCheckWorker` reports the public URL responding. Unlike
    DDNS+port-forward, this doesn't require any operator router
    configuration — but it does require a real registration contract from
    `paperweightv1` (§ open item) and real network egress, so it still can't
    be exercised until that contract is confirmed and a physical device is
    available.
12. **Full public/internet reachability still can't be verified from this
    sandboxed dev environment** — there's no physical device or real network
    egress here. Steps 3–11 all require the user's actual A12 on real WiFi
    connecting out to the real frps. The frp approach removes the "user must
    configure their own router" verification gap DDNS had, but adds a
    dependency on `paperweightv1`'s frps staying up and the registration
    contract being correct — confirm both before treating Phase 9 as done.
13. **Not verifiable here at all — real-device-only:** whether a genuine
    Samsung factory reset on this A12 leaves an inserted SD card untouched.
    Confirm on the physical device before relying on the backup system as a
    real recovery plan, and update `provisioning/setup.sh`/CLAUDE.md with
    whatever's actually observed.
