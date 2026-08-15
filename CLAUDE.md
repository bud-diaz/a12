# Paperweight OS

## What this is

Paperweight OS is a single-purpose Android app that turns a Samsung Galaxy A12
into a dedicated Paperweight terminal. The app becomes Android **Device Owner**,
registers itself as the launcher, and uses Android lock-task/kiosk APIs so the
operator stays inside Paperweight.

This project has pivoted: the A12 is no longer a thin client paired to a remote
Paperweight Studio backend. The A12 itself is the local backend for the core
product: local vault, local metadata/config database, continuous HLS audio
broadcast, embedded listener server, local dashboard controls, local backup, and
later frp-based public reachability.

Stock Android and system services remain intact underneath. We are not touching
the OS image, kernel, bootloader, adb, or USB debugging.

## Source-of-truth files

Read these before changing architecture or phase scope:

1. `HANDOFF.md` — running cross-session status, validation results, current
   blockers, and deviations from the plan.
2. `docs/ON_DEVICE_BACKEND_PLAN.md` — approved 12-phase on-device-backend plan.
3. This `CLAUDE.md` — durable project constraints and operating conventions.

If there is a conflict, prefer `HANDOFF.md` for current repo state and
`docs/ON_DEVICE_BACKEND_PLAN.md` for approved phase intent. Update all three when
architecture or operational constraints change.

## Non-negotiable constraints

- **adb must always work.** Device Owner mode must not disable USB debugging,
  revoke adb, hide the escape hatch, or make the physical device unrecoverable.
  If a task seems to require that, stop and ask first.
- **Native UI only.** No WebView for the operator dashboard, no bundled web
  dashboard, and no direct reuse of the React Studio UI. This is a Kotlin /
  Jetpack Compose native rebuild that may reference `paperweightv1` for behavior,
  copy, domain shape, and visual intent.
- **On-device backend for core product.** No QR pairing to an external Studio
  backend. No Retrofit dashboard client. No remote server dependency for vault,
  playback rotation, dashboard control, backup/restore, local analytics, or
  listener LAN playback.
- **Wifi-only target.** The A12 has no SIM/cellular fallback. Handle no-network
  states clearly, but do not build broad offline sync or cloud retry machinery.
- **Single-purpose kiosk.** Once provisioned, the device shows Paperweight and
  only intentional system surfaces. Android Settings and system pickers are
  explicit allowlisted exceptions because provisioning, Wi-Fi, and SAF grants need
  them. Do not expand the allowlist without scope approval.
- **SD card is required.** A removable SD card of at least decimal 2GB is a hard
  boot gate. Vault media and backups live on the card, not internal storage.
- **No fake success.** If a screen/phase is not wired yet, show honest local
  state or an explicit not-wired message. Do not fabricate empty cloud-like data.

## v1 product scope

In scope for the on-device v1:

- Device Owner provisioning flow via `adb install` + `dpm set-device-owner`.
- SD-card-required boot gate.
- One-time SAF grant to a folder named `Paperweight` on the removable SD card.
- Local Room database for vault metadata, schedule, tokens, analytics, station
  config, and related state.
- Local vault ingestion: copy selected audio into `Paperweight/vault/` on the SD
  card and store metadata locally.
- Local backup/recovery: snapshot Room DB + non-secret preferences into
  `Paperweight/backups/<timestamp>/` and restore before Room opens.
- Continuous audio-only HLS “radio” broadcast generated on the A12 from public
  vault tracks.
- Foreground `BroadcastService` for continuous encode/segment/server lifecycle.
- Embedded NanoHTTPD listener server with HLS segment/playlist serving and a
  bundled listener player.
- Native dashboard screens: Overview, Broadcast, Schedule, Vault, Station,
  Audience, Analytics, Earnings, Settings — progressively rewired to local
  repositories/engine/server state by phase.
- Local opaque bearer access tokens for private VOD tracks. Tokens are label-only;
  no listener accounts or email assignment.
- Inert pricing metadata. Payments/tips are deferred; Earnings is a static
  “coming soon” shell until payment infrastructure exists.
- Public reachability through frp in a later phase, matching the current
  `paperweightv1` approach. The device remains local-first; frp is only the public
  network path.

Explicitly out of scope for v1 unless Bud reopens scope:

- Remote Studio pairing / dashboard API client.
- Cloud-hosted Paperweight account dependency for core operation.
- Listener accounts, password reset, marketing automations, polls, email sends,
  or external-platform import.
- Payment processing, subscriptions, webhooks, or an IOU ledger.
- Video / RTMP broadcast.
- Multi-app launcher, arbitrary app whitelist, or user escape UI.
- Play Store distribution. This ships via adb/provisioning.
- Replacing Android itself or altering bootloader/kernel/system image.

## Architecture target

```text
app/src/main/java/com/paperweight/os/
├── MainActivity.kt                    # Device Owner -> SD gate -> restore gate -> dashboard
├── admin/                             # Device Owner receiver/policy/boot behavior
├── provisioning/                      # Device Owner setup activity
├── storage/                           # removable SD detection + live mount state
├── data/
│   ├── db/                            # Room database, fixed name paperweight-os.db
│   ├── dao/                           # local DAOs
│   ├── prefs/                         # non-secret preferences; future secrets encrypted separately
│   └── repository/                    # local repository facades
├── vault/                             # SAF ingest, metadata extraction, SD-card file store
├── backup/                            # backup writer/pruner/scheduler/restore/recovery info
├── broadcast/
│   ├── BroadcastEngine.kt             # rotation/decode/encode/segment state machine
│   ├── BroadcastService.kt            # long-lived foreground service
│   ├── decode/                        # MediaExtractor/MediaCodec source decode
│   ├── encode/                        # MediaCodec AAC-LC + ADTS framing
│   ├── hls/                           # segment/playlist writing and pruning
│   └── mic/                           # later mic go-live source
├── server/                            # later NanoHTTPD routes/player/status
├── reachability/                      # later frp registration/config/process supervisor
└── ui/
    ├── setup/                         # SD-card and restore gates
    └── dashboard/                     # native Compose dashboard screens
```

`pairing/` and the Retrofit transport layer are intentionally gone. Some
`network/models/*.kt` DTOs may temporarily remain only because older dashboard
`UiState`/`Screen` files still import them; delete each slice when its screen is
rewired to local state.

## Broadcast/HLS decisions

- Use HLS packed-audio mode: ADTS-framed AAC `.aac` segments referenced from a
  live `.m3u8` playlist. Do not add ffmpeg-kit.
- Target pipeline: `MediaExtractor` + decoder `MediaCodec` -> normalized PCM
  -> AAC-LC encoder `MediaCodec` around 128kbps -> ADTS headers -> ~6s segments
  -> atomic live playlist updates.
- The broadcast engine, not WorkManager, owns real-time rotation. WorkManager is
  only for peripheral jobs like backups, health checks, and rollups.
- Mic go-live is later: `AudioRecord` feeds the same PCM -> AAC -> HLS path and
  switches sources at segment boundaries.
- Private tracks do not enter the public live rotation. Private access later uses
  token-gated VOD HLS generated separately.

## Storage and recovery contract

- Android 11 DocumentsUI does not grant the SD-card root. The operator must
  create/select a folder named `Paperweight` on the removable SD card.
- If the granted SAF tree is named `Paperweight`, write directly under:
  - `Paperweight/vault/`
  - `Paperweight/backups/`
- If an older/nonstandard grant points one level above `Paperweight`, code may
  fall back to creating/using `Paperweight/...` under that tree.
- Store ingested vault file locations as opaque SAF document URIs, not human
  filesystem paths. Treat `VaultTrackEntity.storagePath` as an opaque URI.
- Backups include Room DB + non-secret preferences only. Future frp/reachability
  secrets must not be silently round-tripped through automatic backup JSON;
  Android Keystore-backed secrets need explicit recovery info export.
- For legitimate re-provisioning/factory reset, leave any Samsung/Android
  “erase SD card” option unchecked so vault media/backups remain on the card.

## Provisioning flow

1. Factory reset the A12 if needed. Leave SD-card erase unchecked during any
   recovery/reset flow.
2. Skip Google account setup; Device Owner provisioning requires no accounts.
3. Insert/format a removable SD card of at least decimal 2GB as public/removable
   storage if Android does not mount it normally.
4. Install the APK:
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`
5. Set Device Owner:
   `adb shell dpm set-device-owner com.paperweight.os/.admin.PaperweightDeviceAdminReceiver`
6. Launch the app. It should gate on SD-card presence, then one-time SAF grant / restore decision, then dashboard.
7. Grant/select the SD-card folder named `Paperweight` when prompted.
8. Restore from backup if offered, or start fresh.

Keep `provisioning/setup.sh` synchronized with this flow.

## Build and validation environment

Known local validation setup on Bud's machine:

```bash
export JAVA_HOME=/home/bud/.local/jdks/jdk-17
export ANDROID_HOME=/home/bud/Android/Sdk
export ANDROID_SDK_ROOT=/home/bud/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

Typical validation sequence:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.paperweight.os/.MainActivity
adb shell dumpsys activity activities | grep -E 'ResumedActivity|mLockTaskModeState|mLockTaskAuth'
adb shell uiautomator dump /sdcard/window.xml
```

For instrumented tests, prefer targeted class runs during a phase, then full
`connectedDebugAndroidTest` before closing the phase when a physical A12 is
connected and authorized.

## Development discipline

- Before starting a phase, read `HANDOFF.md` and the relevant section of
  `docs/ON_DEVICE_BACKEND_PLAN.md`.
- Before pushing, update `HANDOFF.md` with what changed, exact validation run,
  what remains unverified, and any new key decisions.
- Add dependencies only in the phase that actually uses them.
- Preserve Device Owner recoverability. Ask before touching adb/USB/debugging,
  lock-task scope, factory reset behavior, or anything that might strand the
  device.
- Use real build/device evidence. Do not claim physical validation without real
  adb/tool output.
- Prefer targeted tests for the new phase seam, then full build/test validation.
- Avoid scope creep. If an idea expands product -> platform -> ecosystem, separate
  the future leverage from the current phase closure and keep the current phase
  tight.
