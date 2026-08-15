# Phase 5 HLS Playback Blocker Patch Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Close the remaining Phase 5 blocker by giving the A12 an explicit, debug-only way to generate real local HLS audio so `/live/playlist.m3u8` and `/live/segment-*.aac` exist and can be played from another Wi-Fi device.

**Architecture:** The blocker is not LAN reachability anymore; `http://10.0.0.145:8080/` works. The blocker is that the broadcast engine has no public vault track to encode, so the HLS playlist route correctly returns `404`. Add a debug-only “validation tone” action on the Broadcast screen that creates a real short WAV file in app-private storage, inserts it as a public vault track, and lets the existing `BroadcastEngine -> TrackDecoder -> AacEncoder -> SegmentStore -> EmbeddedHttpServer` path produce the playlist/segments. This avoids fake product success: the UI must label it as a validation tone, and it must only be exposed in debuggable builds.

**Tech Stack:** Kotlin, Jetpack Compose, Room, MediaExtractor/MediaCodec, NanoHTTPD, adb physical-device validation on Samsung Galaxy A12.

---

## Context / Current Evidence

- LAN listener page works on real Wi-Fi:
  - `http://10.0.0.145:8080/` returns `HTTP/1.1 200 OK` HTML.
  - `http://10.0.0.145:8080/status` returns `isRunning=true`.
- The blocker:
  - `http://10.0.0.145:8080/live/playlist.m3u8` returns `HTTP/1.1 404 Not Found`.
  - `/status` reports `queueLength: 0`, `nowPlayingTitle: null`.
- Relevant existing files:
  - `app/src/main/java/com/paperweight/os/broadcast/BroadcastEngine.kt`
  - `app/src/main/java/com/paperweight/os/broadcast/hls/SegmentStore.kt` package is currently `com.paperweight.os.broadcast`
  - `app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastScreen.kt`
  - `app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastViewModel.kt`
  - `app/src/main/java/com/paperweight/os/data/repository/VaultRepository.kt`
  - `app/src/main/java/com/paperweight/os/data/db/entity/VaultTrackEntity.kt`
  - `app/src/androidTest/java/com/paperweight/os/broadcast/Phase4BroadcastEngineInstrumentedTest.kt`

## Scope Boundaries

Do:
- Add explicit debug-only validation content generation.
- Use real WAV -> decoder -> AAC -> HLS path, not hardcoded playlist-only scaffolding.
- Keep every user-facing label honest: “Validation tone”, “debug validation”, etc.
- Validate on physical A12 and from second Wi-Fi device/VLC/browser.

Do not:
- Auto-generate fake station content in production.
- Count a silent stub playlist as audio playback.
- Add remote/backend dependencies.
- Start Phase 6 until Phase 5 playback is verified.

---

### Task 1: Add a debug-build gate helper

**Objective:** Provide one reusable way to decide whether debug-only validation UI/actions may appear.

**Files:**
- Create: `app/src/main/java/com/paperweight/os/debug/DebugBuild.kt`

**Step 1: Create the helper**

```kotlin
package com.paperweight.os.debug

import android.content.Context
import android.content.pm.ApplicationInfo

object DebugBuild {
    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
```

**Step 2: Compile**

Run:

```bash
export JAVA_HOME=/home/bud/.local/jdks/jdk-17
export ANDROID_HOME=/home/bud/Android/Sdk
export ANDROID_SDK_ROOT=/home/bud/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Add a validation WAV generator/seeder

**Objective:** Create a real local WAV file and public vault DB row when the operator explicitly requests Phase 5 validation content.

**Files:**
- Create: `app/src/main/java/com/paperweight/os/broadcast/ValidationBroadcastSeeder.kt`
- Uses: `app/src/main/java/com/paperweight/os/data/repository/VaultRepository.kt`
- Uses: `app/src/main/java/com/paperweight/os/data/db/entity/VaultTrackEntity.kt`

**Step 1: Add the seeder**

```kotlin
package com.paperweight.os.broadcast

import android.content.Context
import android.net.Uri
import com.paperweight.os.data.db.entity.VaultTrackEntity
import com.paperweight.os.data.repository.VaultRepository
import com.paperweight.os.debug.DebugBuild
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class ValidationBroadcastSeeder(
    private val context: Context,
    private val vaultRepository: VaultRepository,
) {
    suspend fun seedValidationTone(): Result<VaultTrackEntity> = runCatching {
        check(DebugBuild.isDebuggable(context)) { "Validation tone is only available in debug builds." }

        val dir = File(context.filesDir, "validation").apply { mkdirs() }
        val wavFile = File(dir, VALIDATION_FILE_NAME)
        wavFile.writeBytes(generatedWav(durationSeconds = 12))

        val now = System.currentTimeMillis()
        val track = VaultTrackEntity(
            id = VALIDATION_TRACK_ID,
            title = "Phase 5 validation tone",
            artist = "Paperweight OS",
            album = "Debug validation",
            sourceUri = Uri.fromFile(wavFile).toString(),
            storagePath = Uri.fromFile(wavFile).toString(),
            durationMs = 12_000,
            mimeType = "audio/wav",
            visibility = "public",
            suggestedPriceCents = 0,
            minimumPriceCents = 0,
            allowFree = true,
            createdAt = now,
            updatedAt = now,
        )
        vaultRepository.upsertTrack(track)
        track
    }

    private fun generatedWav(durationSeconds: Int): ByteArray {
        val pcm = generatedPcm(durationSeconds)
        val out = ByteArrayOutputStream()
        fun writeAscii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        fun writeInt(value: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        fun writeShort(value: Int) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())

        writeAscii("RIFF")
        writeInt(36 + pcm.size)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeInt(16)
        writeShort(1) // PCM
        writeShort(CHANNEL_COUNT)
        writeInt(SAMPLE_RATE)
        writeInt(SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE)
        writeShort(CHANNEL_COUNT * BYTES_PER_SAMPLE)
        writeShort(16)
        writeAscii("data")
        writeInt(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }

    private fun generatedPcm(durationSeconds: Int): ByteArray {
        val sampleCount = SAMPLE_RATE * durationSeconds
        val buffer = ByteBuffer.allocate(sampleCount * CHANNEL_COUNT * BYTES_PER_SAMPLE)
            .order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { index ->
            val sample = (sin(2.0 * PI * 440.0 * index / SAMPLE_RATE) * Short.MAX_VALUE * 0.35)
                .toInt()
                .toShort()
            repeat(CHANNEL_COUNT) { buffer.putShort(sample) }
        }
        return buffer.array()
    }

    companion object {
        const val VALIDATION_TRACK_ID = "debug-phase5-validation-tone"
        private const val VALIDATION_FILE_NAME = "phase5-validation-tone.wav"
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_COUNT = 2
        private const val BYTES_PER_SAMPLE = 2
    }
}
```

**Step 2: Compile**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Expose the seeder through `BroadcastViewModel`

**Objective:** Let the Broadcast screen request validation content without coupling Compose directly to repositories.

**Files:**
- Modify: `app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastUiState.kt`
- Modify: `app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastViewModel.kt`

**Step 1: Add a UI-state flag**

In `BroadcastUiState`, add:

```kotlin
val validationToneAvailable: Boolean = false,
```

Expected shape after edit:

```kotlin
data class BroadcastUiState(
    val mode: String = "shuffle",
    val nowPlayingTitle: String? = null,
    val nowPlayingArtist: String? = null,
    val liveActive: Boolean = false,
    val listenerCount: Int = 0,
    val queue: List<BroadcastQueueItem> = emptyList(),
    val actionMessage: String? = null,
    val actionInFlight: Boolean = false,
    val validationToneAvailable: Boolean = false,
) {
    val alternateMode: String = if (mode == "shuffle") "scheduled" else "shuffle"
}
```

If current file differs, preserve existing fields and append only the new flag.

**Step 2: Wire the seeder in `BroadcastViewModel`**

Add imports:

```kotlin
import com.paperweight.os.broadcast.ValidationBroadcastSeeder
import com.paperweight.os.debug.DebugBuild
```

Add properties:

```kotlin
private val services = ServiceLocator.get(application)
private val engine = services.broadcastEngine
private val validationSeeder = ValidationBroadcastSeeder(application, services.vaultRepository)
private val validationToneAvailable = DebugBuild.isDebuggable(application)
```

Replace the existing direct `engine` initialization if needed. The top of the class should avoid calling `ServiceLocator.get(application)` twice.

When building `BroadcastUiState`, add:

```kotlin
validationToneAvailable = validationToneAvailable,
```

Add method:

```kotlin
fun seedValidationTone() {
    viewModelScope.launch {
        _state.value = ScreenState.Content(
            ((_state.value as? ScreenState.Content)?.data ?: BroadcastUiState()).copy(
                actionInFlight = true,
                actionMessage = "Generating Phase 5 validation tone…",
            ),
        )
        validationSeeder.seedValidationTone().fold(
            onSuccess = {
                engine.restart()
                _state.value = ScreenState.Content(
                    ((_state.value as? ScreenState.Content)?.data ?: BroadcastUiState()).copy(
                        actionInFlight = false,
                        actionMessage = "Validation tone added. Wait a few seconds, then open /live/playlist.m3u8 or press Play from another device.",
                    ),
                )
            },
            onFailure = { error ->
                _state.value = ScreenState.Content(
                    ((_state.value as? ScreenState.Content)?.data ?: BroadcastUiState()).copy(
                        actionInFlight = false,
                        actionMessage = "Validation tone failed: ${error.message ?: error::class.java.simpleName}",
                    ),
                )
            },
        )
    }
}
```

**Step 3: Compile**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Add an explicit Broadcast screen validation button

**Objective:** Show a clear operator action when the queue is empty in debug builds.

**Files:**
- Modify: `app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastScreen.kt`

**Step 1: Pass the new callback**

Change the `RotationPanel` call from:

```kotlin
item { RotationPanel(data, onToggleMode = viewModel::toggleMode, onRestart = viewModel::restart) }
```

to:

```kotlin
item {
    RotationPanel(
        data = data,
        onToggleMode = viewModel::toggleMode,
        onRestart = viewModel::restart,
        onSeedValidationTone = viewModel::seedValidationTone,
    )
}
```

**Step 2: Extend the `RotationPanel` signature**

Change:

```kotlin
private fun RotationPanel(
    data: BroadcastUiState,
    onToggleMode: () -> Unit,
    onRestart: () -> Unit,
)
```

to:

```kotlin
private fun RotationPanel(
    data: BroadcastUiState,
    onToggleMode: () -> Unit,
    onRestart: () -> Unit,
    onSeedValidationTone: () -> Unit,
)
```

**Step 3: Add the button below Restart**

Inside `RotationPanel`, after the existing row of `Switch`/`Restart` buttons, add:

```kotlin
if (data.validationToneAvailable && data.queue.isEmpty()) {
    OutlinedButton(
        onClick = onSeedValidationTone,
        enabled = !data.actionInFlight,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Icon(Icons.Outlined.Radio, contentDescription = null)
        Text(text = "Generate Phase 5 validation tone", modifier = Modifier.padding(start = 8.dp))
    }
    Text(
        text = "Debug-only: creates a real local WAV track so the A12 can generate HLS for LAN playback validation.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
```

**Step 4: Compile**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 5: Add focused instrumented test for the debug validation seeder

**Objective:** Prove the seeder creates a public track and the existing engine turns it into a live playlist and non-empty segment.

**Files:**
- Create: `app/src/androidTest/java/com/paperweight/os/broadcast/Phase5ValidationToneInstrumentedTest.kt`

**Step 1: Create the test**

```kotlin
package com.paperweight.os.broadcast

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.paperweight.os.data.db.AppDatabase
import com.paperweight.os.data.repository.BroadcastRepository
import com.paperweight.os.data.repository.ScheduleRepository
import com.paperweight.os.data.repository.StationRepository
import com.paperweight.os.data.repository.VaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class Phase5ValidationToneInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var outputDir: File
    private lateinit var vaultRepository: VaultRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        outputDir = File(context.cacheDir, "phase5-validation-hls").apply {
            deleteRecursively()
            mkdirs()
        }
        vaultRepository = VaultRepository(db.vaultDao())
    }

    @After
    fun tearDown() {
        db.close()
        outputDir.deleteRecursively()
        File(context.filesDir, "validation").deleteRecursively()
    }

    @Test
    fun validationToneSeedsPublicTrackAndBroadcastEnginePublishesHls() = runBlocking {
        val seeded = ValidationBroadcastSeeder(context, vaultRepository).seedValidationTone().getOrThrow()
        val tracks = vaultRepository.observeTracks().first()
        assertThat(tracks.map { it.id }).contains(ValidationBroadcastSeeder.VALIDATION_TRACK_ID)
        assertThat(seeded.visibility).isEqualTo("public")
        assertThat(File(java.net.URI.create(seeded.storagePath)).isFile).isTrue()

        val repository = BroadcastRepository(
            vaultRepository = vaultRepository,
            scheduleRepository = ScheduleRepository(db.scheduleDao()),
            stationRepository = StationRepository(db.stationDao()),
        )
        val engine = BroadcastEngine(context, repository, SegmentStore(outputDir))

        engine.start()
        val state = engine.state.first { it.nowPlayingTitle == "Phase 5 validation tone" && it.segmentCount > 0 }

        assertThat(state.isRunning).isTrue()
        assertThat(File(outputDir, "live.m3u8").isFile).isTrue()
        val segment = outputDir.listFiles()?.firstOrNull { it.name.startsWith("segment-") }
        assertThat(segment).isNotNull()
        assertThat(segment!!.length()).isGreaterThan(AacEncoder.silentAdtsFrame().size.toLong())
    }
}
```

**Step 2: Run only the focused test**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperweight.os.broadcast.Phase5ValidationToneInstrumentedTest
```

Expected:

```text
Starting 1 tests on SM-A125U - 11
Finished 1 tests on SM-A125U - 11
BUILD SUCCESSFUL
```

If the test fails because `File(java.net.URI.create(...))` rejects Android `file://` URI formatting, patch the test to use `Uri.parse(seeded.storagePath).path!!`:

```kotlin
assertThat(File(android.net.Uri.parse(seeded.storagePath).path!!).isFile).isTrue()
```

---

### Task 6: Full build and install on the A12

**Objective:** Produce and install the APK that includes the validation-tone UI.

**Files:**
- Build artifact: `app/build/outputs/apk/debug/app-debug.apk`

**Step 1: Build**

Run:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

Expected:

```text
BUILD SUCCESSFUL
:testDebugUnitTest NO-SOURCE
```

**Step 2: Install**

Run:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.paperweight.os/.MainActivity
```

Expected:

```text
Success
ResumedActivity: com.paperweight.os/.MainActivity
mLockTaskModeState=LOCKED
```

---

### Task 7: Device UI validation — generate the tone

**Objective:** Use the app UI, not adb DB surgery, to seed the validation track.

**Files:**
- Runtime only; no source edits.

**Step 1: Navigate on device**

On the A12:
1. Open the Paperweight drawer.
2. Tap `Broadcast`.
3. If queue is empty, tap `Generate Phase 5 validation tone`.
4. Wait 5–10 seconds.

**Step 2: Verify status from dev machine**

Run:

```bash
curl -i http://10.0.0.145:8080/status
curl -i http://10.0.0.145:8080/live/playlist.m3u8
```

Expected:

```text
/status -> HTTP/1.1 200 OK
/status body includes nowPlayingTitle: "Phase 5 validation tone" and queueLength >= 1
/live/playlist.m3u8 -> HTTP/1.1 200 OK
/live/playlist.m3u8 body includes #EXTM3U and segment-*.aac
```

**Step 3: Verify a segment exists over LAN**

Extract a segment filename from the playlist, then run:

```bash
curl -i http://10.0.0.145:8080/live/segment-0.aac | head
curl -i -H 'Range: bytes=0-15' http://10.0.0.145:8080/live/segment-0.aac
```

Expected:

```text
HTTP/1.1 200 OK
Content-Type: audio/aac
HTTP/1.1 206 Partial Content for the range request
```

---

### Task 8: Second-device playback validation

**Objective:** Close the actual Phase 5 acceptance gate: playback from another device on the same Wi-Fi.

**Files:**
- Runtime only; no source edits.

**Step 1: Browser player**

From Bud’s second device on the same Wi-Fi, open:

```text
http://10.0.0.145:8080/
```

Expected:
- Page loads.
- Pressing `Play` plays/hears the validation tone.
- Page does not sit permanently in a stream error state.

**Step 2: VLC / direct HLS**

In VLC / network stream, open:

```text
http://10.0.0.145:8080/live/playlist.m3u8
```

Expected:
- Stream opens.
- Validation tone plays.

**Step 3: Record evidence**

Capture:

```bash
curl -i http://10.0.0.145:8080/status
curl -i http://10.0.0.145:8080/live/playlist.m3u8 | head -40
adb shell dumpsys activity services com.paperweight.os | grep -E 'BroadcastService|isForeground' -A3 -B2
adb shell dumpsys activity activities | grep -E 'ResumedActivity|mLockTaskModeState|mLockTaskPackages' -A3 -B2
```

Expected:
- Status shows validation tone / queue length.
- Playlist is `200 OK`.
- Service foreground remains true.
- Lock task remains locked.

---

### Task 9: Update `HANDOFF.md`

**Objective:** Replace the remaining Phase 5 gap with exact observed pass/fail evidence.

**Files:**
- Modify: `HANDOFF.md`

**Step 1: Update the Phase 5 section**

Replace the current “Remaining Phase 5 gap narrowed” paragraph with exact results. Template:

```markdown
**Phase 5 LAN playback is verified on the physical A12.** With the A12 on Wi-Fi at
`10.0.0.145`, the debug-only `Phase 5 validation tone` action generated a real
local WAV track, the broadcast engine encoded it into AAC/HLS, and the embedded
server served it over LAN.

Validation:

```bash
curl -i http://10.0.0.145:8080/status
# HTTP/1.1 200 OK
# nowPlayingTitle="Phase 5 validation tone"; queueLength >= 1

curl -i http://10.0.0.145:8080/live/playlist.m3u8
# HTTP/1.1 200 OK
# #EXTM3U ... segment-*.aac

curl -i -H 'Range: bytes=0-15' http://10.0.0.145:8080/live/<observed-segment>.aac
# HTTP/1.1 206 Partial Content
```

Bud also confirmed playback from a second Wi-Fi device at
`http://10.0.0.145:8080/` and/or VLC against
`http://10.0.0.145:8080/live/playlist.m3u8`.
```

**Step 2: Verify docs diff**

Run:

```bash
git diff -- HANDOFF.md
```

Expected: only current validation wording changes.

---

### Task 10: Final verification and commit suggestion

**Objective:** Make sure the patch is shippable and scoped.

**Step 1: Run final validation commands**

Run:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.paperweight.os.broadcast.Phase5ValidationToneInstrumentedTest
```

Expected: both commands `BUILD SUCCESSFUL`.

**Step 2: Inspect diff**

Run:

```bash
git diff --stat
git diff -- app/src/main/java/com/paperweight/os/broadcast/ValidationBroadcastSeeder.kt \
  app/src/main/java/com/paperweight/os/debug/DebugBuild.kt \
  app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastViewModel.kt \
  app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastScreen.kt \
  app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastUiState.kt \
  app/src/androidTest/java/com/paperweight/os/broadcast/Phase5ValidationToneInstrumentedTest.kt \
  HANDOFF.md
```

Expected:
- No production auto-seeding.
- Validation UI is guarded by debug-build check.
- User-facing text clearly says validation/debug.
- Existing Phase 5 LAN URL/server behavior unchanged except HLS exists after explicit validation tone generation.

**Step 3: Suggested commit**

```bash
git add \
  app/src/main/java/com/paperweight/os/debug/DebugBuild.kt \
  app/src/main/java/com/paperweight/os/broadcast/ValidationBroadcastSeeder.kt \
  app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastUiState.kt \
  app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastViewModel.kt \
  app/src/main/java/com/paperweight/os/ui/dashboard/broadcast/BroadcastScreen.kt \
  app/src/androidTest/java/com/paperweight/os/broadcast/Phase5ValidationToneInstrumentedTest.kt \
  HANDOFF.md \
  docs/plans/2026-08-14-phase5-hls-playback-blocker.md

git commit -m "fix: add debug validation tone for phase 5 HLS playback"
```

Do not include unrelated generated files unless intentionally accepted:

```text
app/schemas/com.paperweight.os.data.db.AppDatabase/2.json
```

---

## Acceptance Criteria

- `http://10.0.0.145:8080/` loads from second Wi-Fi device.
- Debug Broadcast screen has an explicit `Generate Phase 5 validation tone` action only in debuggable builds.
- After tapping that action:
  - `/status` reports `nowPlayingTitle = "Phase 5 validation tone"` and `queueLength >= 1`.
  - `/live/playlist.m3u8` returns `200 OK` and valid HLS text.
  - `/live/segment-*.aac` returns `200 OK` as `audio/aac`.
  - A range request to a segment returns `206 Partial Content`.
  - VLC/browser on second Wi-Fi device plays audible tone.
- `com.paperweight.os` has no fatal crash in logcat during the flow.
- Lock task remains active.
- `HANDOFF.md` records exact observed evidence.

## Notes / Risk Controls

- This deliberately uses a debug-only validation tone instead of silently playing fake content in production.
- The real product path remains: operator-ingested public vault tracks drive the station.
- If the validation tone plays but real ingested tracks do not, that becomes a separate Phase 2/4 ingestion/storage URI bug, not a Phase 5 server-route blocker.
---

## Closeout Result (2026-08-15)

Implemented and validated on Bud's physical `SM-A125U - 11` A12. Final evidence:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
# BUILD SUCCESSFUL; :app:testDebugUnitTest NO-SOURCE
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
# MainActivity resumed; mLockTaskModeState=LOCKED; BroadcastService running; LISTEN *:8080
# A12 wlan0 IP: 10.0.0.145

# After tapping the Broadcast screen's debug-only `Generate Phase 5 validation tone` action:
curl -i http://10.0.0.145:8080/status
# HTTP/1.1 200 OK; nowPlayingTitle="Phase 5 validation tone"; queueLength=1

curl -i http://10.0.0.145:8080/live/playlist.m3u8
# HTTP/1.1 200 OK; #EXTM3U; segment-0.aac

curl -i -H 'Range: bytes=0-15' http://10.0.0.145:8080/live/segment-0.aac
# HTTP/1.1 206 Partial Content; Content-Type: audio/aac; Content-Range: bytes 0-15/98036

ffprobe -hide_banner -v error -show_entries stream=codec_name,codec_type,sample_rate,channels \
  -of default=noprint_wrappers=1 http://10.0.0.145:8080/live/playlist.m3u8
# codec_name=aac
# codec_type=audio
# sample_rate=44100
# channels=2
```

Phase 5's technical blocker is closed: the embedded server now serves a real AAC/HLS stream generated by the existing broadcast pipeline from explicit debug validation content. The only remaining check is Bud's human ear-check from another Wi-Fi device/browser/VLC; no code blocker remains for Phase 5.

