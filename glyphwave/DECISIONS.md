# Decisions log

Newest at the bottom of each section. Format: what was decided, why, and what would change it.

---

## Phase 0 — scaffold, toy, raw event logger

### D-001 · Where the code lives (for now)
The session that started the project only had push access to `nicodiprospero/taskhub`, branch
`ccr-3c26dbc9-c16fnl`. The project is developed in the self-contained `glyphwave/` folder there and gets
**extracted to its own repo `nicodiprospero/glyphwave` in Phase 6** with its history intact:

```bash
git subtree split --prefix=glyphwave -b glyphwave-main
git push git@github.com:nicodiprospero/glyphwave.git glyphwave-main:main
```

Nothing in `glyphwave/` references the parent repo.

### D-002 · Build environment split
Development sessions run in a cloud container. That container **cannot reach `dl.google.com`**, which is
where Google Maven (AGP, AndroidX) and the Android SDK are hosted. It also has no adb, so it can't reach the phone.
Consequences:
- `./gradlew assembleDebug`, `adb install`, logcat → run on the owner's PC.
- In the container: `./gradlew -Pglyphwave.coreOnly=true :core:test :core:renderPreviews` (pure JVM).
- Android sources that don't need AndroidX (toy service, Engine) are type-checked in the container against
  Robolectric's `android-all` (API 36 framework jar on Maven Central) + the real Glyph SDK `classes.jar`.
  Compose UI is only compiled on the PC.

### D-003 · Two modules: `:app` + `:core` (pure Kotlin/JVM)
The brief says "single app module unless there's a real reason". The reasons:
1. The compiler then guarantees that DSP, gesture recognizer, mask, canvas, modes and font have
   **no Android dependency**, which the brief requires for the GestureRecognizer and wants for unit tests anyway.
2. Tests and PNG previews run on any JVM in seconds, with no Android SDK, emulator, Robolectric or AAR. That
   includes the cloud container (D-002) and future GitHub Actions CI, where the AAR can't be present.
3. The PNG preview renderer uses `java.awt`, which isn't available in Android unit tests without tricks.

The package layout from the brief stays the same. Android-free files live in
`core/src/main/kotlin/dev/nicodiprospero/glyphwave/…`, and Android ones in `app/src/main/java/…`, with the same packages.
`-Pglyphwave.coreOnly=true` drops `:app` from the build, and the Android plugin is only declared in `:app`.

### D-004 · Pixel value range of `setMatrixFrame(IntArray)` — **0..4095 (confirmed on device)**
Evidence:
- `GlyphMatrixManager.setMatrixFrame(int[])` passes the array straight to `IGlyphService.setMatrixColors`
  with no scaling (bytecode of glyph-matrix-sdk-2.0.aar).
- The SDK's own bitmap → matrix converter (`GlyphMatrixUtils`) has `MAX_BRIGHTNESS = 4095` and
  `BRIGHTNESS_MULTIPLIER = 16`. It maps 0..255 grey to 0..4080/4095, and text ON = 4095.
- The example project is inconsistent: its animation demo uses 255 and its random demo uses 0..2047. With a
  12-bit range, its "255" pixels are only about 6% brightness.

Decision: `MatrixSpec.MAX_VALUE = 4095`. Modes always draw normalised 0..1 floats through `MatrixCanvas`, so the
range is a single constant if the phone proves otherwise.
**Confirmation (2026-10-28, owner's Phone (3)):**
- `VALUE_RANGE`: the ramp brightens across the full width, and the 255 half is clearly dimmer than the 4095 half.
- `LOW_END`: all 8 bands (0, 8, 32, 128, 512, 1024, 2048, 4095) can be told apart. With a 255 cap, the top four
  bands would look identical. The two brightest (2048 vs 4095) are the hardest to separate, so perceived
  brightness compresses at the top: gamma correction (Phase 3) is needed. Value 8 is still visible, which sets
  the floor for the idle animation.
- `RIM`: a clean closed ring, which confirms D-005 on hardware.
- `ORIENTATION`: the arrow points up and the marker is on the left, so (0,0) is the **top-left as seen from the back**
  with the phone upright. No flipping needed.

### D-005 · Circle mask = `(2dx)² + (2dy)² ≤ n²`
The LED allocation SVG in the kit was parsed cell by cell. Phone (3) has 489 LEDs with row widths
7, 11, 15, 17, 19, 21, 21, 23, 23, 25×7, 23, 23, 21, 21, 19, 17, 15, 11, 7, which matches the formula exactly
(and it also gives the 13×13 Phone (4a) Pro layout). The table is hard-coded in `CircleMaskTest` as the
reference, so the formula can never drift from the diagram. The `RIM` pattern verifies it on hardware.

### D-006 · Versions
| Item | Version | Note |
|---|---|---|
| Gradle | 9.8.2 | current release |
| AGP | 9.2.0 | built-in Kotlin, so no `kotlin-android` plugin |
| Kotlin | 2.4.21 | latest stable on Maven Central |
| compileSdk / targetSdk | 37 | first PC build: Compose 1.12 (BOM 2026.08.00) requires compileSdk ≥ 37 (D-014) |
| minSdk | 34 | brief |
| Compose BOM | 2026.08.00 | |
| core-ktx 1.17.0, activity-compose 1.11.0, lifecycle 2.9.4, coroutines 1.11.0 | | versions known to exist. Google Maven can't be browsed from the dev container (D-002), so a version sweep is in Phase 6 |
| JVM bytecode | 17 | any JDK ≥ 17 runs the build |

### D-007 · Glyph event handling and timestamps
- Glyph messages are handled on a dedicated `HandlerThread` (URGENT_DISPLAY priority), not the main looper.
  The companion app runs in the same process, so UI work there can't skew the timestamps.
- Each event is stamped with `SystemClock.elapsedRealtimeNanos()` (monotonic, survives deep sleep). The log
  line also carries `queueLag` = `uptimeMillis() − msg.when`, which is how long the message waited in our queue.
- Unknown `what` values, or event strings we don't know, are logged with the **full bundle dump**, so
  undocumented payloads show up during instrumentation.
- Lifecycle markers (onCreate/onBind/connected/onUnbind/onDestroy) and manual "MARK" entries from the app
  go into the same log, which makes the gesture captures easy to read.

### D-008 · Phase 0 long press behaviour
A long press (`change`) advances to the next test pattern. This makes "does change arrive?" visible on the
matrix itself. The GestureRecognizer replaces it in Phase 4.

### D-009 · Exported toy service has no permission guard
The system binds the toy, and Nothing documents no signature permission we could require. Today the
service only displays data. Revisit before Phase 4 if actions become reachable through the binder: we could
validate the caller with `Binder.getCallingUid()` → package `com.nothing.thirdparty`, once that package is
confirmed in logs.

### D-010 · Toy preview + launcher icon are generated
`:core:renderPreviews` renders `Logo` (two-period sine wave) and writes `toy_preview.xml`. That file follows the kit's
512-unit spec: black disc, 13.05-unit LED cells on a 17.82 pitch, unlit cells `#1C1C1C`. The task also writes
`ic_launcher_foreground.xml`, and both are copied into `app/src/main/res/drawable/`. They're reproducible, and they match the matrix exactly.

### D-011 · `GlyphMatrixManager.setGlyphMatrixTimeout(boolean)` exists but is undocumented
Not used in Phase 0. If the matrix blanks during long renders in Phase 3, test `setGlyphMatrixTimeout(false)`.

### D-012 · SDK licence
The Glyph SDK EULA forbids redistribution (so the AAR is gitignored and `app/libs/README.md` explains the
download). It also forbids commercial use without permission. GlyphWave is a free, non-commercial portfolio project.

### D-013 · All Gradle plugins share the root classloader
The first build on the PC failed with `Could not generate a decorated class for type KotlinAndroidTarget >
com/android/build/gradle/api/BaseVariant`. The Kotlin plugin was loaded in the root classloader (for
`:core`) and AGP only in `:app`'s. AGP 9's built-in Kotlin calls into the Kotlin plugin, which could not see
AGP classes. The fix: declare AGP `apply false` in the root `build.gradle.kts`. The core-only mode (D-002/D-003)
switches to `core-only.gradle.kts` as root build file (`rootProject.buildFileName`), so it never resolves AGP.

### D-014 · compileSdk / targetSdk 37
The first full build failed in `checkDebugAarMetadata`: every Compose 1.12 artifact requires compileSdk ≥ 37.
API 37 (Android 17) is the current stable platform, so both compileSdk and targetSdk moved to 37, as the brief
asks for "latest stable". minSdk stays at 34.

---

## Glyph Button — observed event sequence (Phase 0 instrumentation, 2026-10-28)

Device: Nothing Phone (3), toy bound with `longpress=1`, debug mode on. Times from `elapsedRealtimeNanos`.
Queue lag on our event thread: 0–3 ms. Time from `change` to the new frame pushed: about 18 ms.

| Gesture | Raw sequence (Δ from previous) | Notes |
|---|---|---|
| 1 long press (×9 samples) | `action_down` → **`change` +495…504 ms** → `action_up` +199…339 ms | change fires **while held**, at a fixed ~500 ms threshold |
| 2 long presses, fast as possible | down → change +501 → up +339 → **down +368** → change +503 → up +256 | fastest release→down gap 368 ms; fastest change→change 1210 ms |
| 6 s hold | down → change +499 → **up +5497** (total ≈ 5.996 s) | **change is NOT repeated** during a hold; up always arrives |
| short press (< 500 ms) | `action_down` → **`onUnbind` +210 ms**, no `action_up`, no `change` | the system switches toy on release; our service gets torn down |

Answers:
1. `action_down` arrives first, always.
2. `change` fires at a **hold threshold of ~500 ms** (system-side), not on release.
3. `action_up` always arrives after a long press, including 6 s holds. It does **not** arrive after a short press: the toy is unbound instead.
4. `change` is not repeated during long holds. Hold duration must be measured as down→up.
5. The minimum practical change→change gap is about 1.2 s (500 ms hold + release + ≥ 370 ms gap + 500 ms).

### D-015 · Gesture recognizer design (built on the table above)
- **Source of truth is down/up timing.** `change` only proves the system accepted a long press (≥ ~500 ms).
  Hold options stay enabled, because down/up exist.
- A press is classified **on `action_up`** by its duration `d = up − down` (only if a `change` was seen):
  `d < holdMedium (2000 ms)` → one "long press tap"; `holdMedium ≤ d < holdLong (4000 ms)` → HOLD_MEDIUM;
  `d ≥ holdLong` → HOLD_LONG.
- HOLD_MEDIUM / HOLD_LONG also **fire at the threshold crossing** while still held (timer from `action_down`). That
  gives immediate feedback and the progress ring. On release the hold is not emitted twice.
- Multi-press counting: after a long-press tap is released, wait `multiGap` (default **600 ms**, configurable)
  for the next `action_down`. If the next press also reaches `change`, the count goes up. If the gap expires, emit LONG_PRESS /
  DOUBLE_LONG_PRESS / TRIPLE_LONG_PRESS for the count. The brief's "2nd within ~1.2 s" is measured change→change,
  which needs ≥ 1.2 s physically (see 5), so the window is defined on release→down instead.
- `action_down` without `change` followed by unbind = system short press. The recognizer resets and emits nothing.
- Every timeout runs from an injected clock/scheduler, so the recognizer stays pure Kotlin and unit-testable with
  the exact timelines above.

---

## Phase 1 — MediaBridge

### D-016 · `MediaState` lives in `:core`, with album art as a luminance grid
Modes need title, artist, playback state, position and art, but `:core` can't see `Bitmap`. So `MediaBridge`
converts the art once per track, off the main thread: centre crop → 64×64 → luma 0..255 (`ArtPixels`). The
full bitmap is kept separately (`MediaBridge.artBitmap`) for the companion UI only. `positionAt(now)`
extrapolates the position from `PlaybackState` (position + elapsed × speed), so the UI and Now-playing never poll.

### D-017 · The notification listener only exists for the permission
`getActiveSessions()` requires an enabled `NotificationListenerService`. `MediaListenerService` ignores
notifications. A useful side effect: while access is granted, the system keeps it bound, so the process (and
`MediaBridge`) stays alive for the toy even when the app UI is closed.
Sideloading note: Android 13+ "restricted settings" block notification access for apps installed from
a browser or file manager (fix: App info → ⋮ → *Allow restricted settings*). `adb install` is not affected.

### D-018 · Session selection policy (setting, default YT Music only)
- `YT_MUSIC_ONLY`: track `com.google.android.apps.youtube.music` only. If YT Music isn't installed, report
  `YT_MUSIC_NOT_INSTALLED`, distinct from `NO_SESSION` (installed but not running / nothing played yet).
- `ANY_MEDIA_APP`: YT Music if it's playing → any playing session → YT Music → first session.
Persisted with DataStore (`SettingsRepository`, the first setting), and applied live.

### D-019 · YT Music custom actions (for "like song")
Logged on every change as `MEDIA customActions=[name [action], …]`, and listed as buttons in the debug screen.
Result on the owner's phone (YT Music, 2026-10-28):
`[Like [thumbs_up_action], Shuffle off [shuffle_action], Repeat off [loop_mode_action], Dislike [thumbs_down_action]]`.
Sending `thumbs_up_action` likes the song, and the label flips to **"Undo like"**. So the "like song" gesture
action works, and the like *state* can be read from the action name (filled vs outline ♥ icon in Phase 4).

### D-020 · Art is reconverted only on real changes
YT Music re-parcels a new `Bitmap` instance on every metadata *and* playback update, and fills album and duration
in a second metadata update. The first build therefore reconverted the art 3–6× per track and logged each
superseded job as a failure. Fixes:
- `trackKey` = MEDIA_ID, or title + artist (album excluded)
- skip when `bitmap.sameAs(last)` for the same track
- cancellation is rethrown, not logged

---

## Phase 2 — audio sources

### D-021 · One analysis path for both sources
Both sources deliver **mono float blocks** (`RawAudio`, pooled, allocation-free). For source A only the
Visualizer *waveform* is used, not its 8-bit FFT, so Phase 3's FFT/DSP is identical for A and B and the
sources can be swapped freely. `LevelMeter` (core, tested) measures the block rate, RMS/peak, the silence run, and
an auto-gained level.
- A: `Visualizer(0)`, max capture size (1024), max capture rate (≈20 Hz on stock Android), so its block rate is the
  platform's limit, not ours.
- B: `AudioRecord` 48 kHz stereo PCM16 → mono, 800-frame reads = **60 blocks/s**. Usages MEDIA + GAME +
  UNKNOWN, optional `addMatchingUid(YT Music)`.

### D-022 · Engine service rules
- Foreground service type `microphone` (A) or `mediaProjection|microphone` (B). `startForeground()` runs before
  `getMediaProjection()`. It is started only from the app (and later the QS tile), never by the toy.
- `START_NOT_STICKY`: a restart would lack the projection consent and the while-in-use mic grant.
- A projection stopped by the system or user → error + the service stops. Every native resource is released in `stop()`.
- "Silent while playing": the source has been digitally silent > 2 s while the media session is PLAYING. It is logged
  once (`AUDIO … delivers SILENCE`) and shown in red in the app.
- The toy shows `AUDIO_LEVEL` (a disc that grows with the auto-gained level, 30 fps) while the engine runs. This is the
  latency and screen-off probe until the real modes land in Phase 3.

### D-023 · Phase 2 report — source evaluation on the owner's Phone (3) (2026-10-28)

| | A · Visualizer (session 0) | B · Playback capture |
|---|---|---|
| Starts without error | ✅ | ✅ (consent dialog each start) |
| Signal with YT Music playing | ✅ (no silence alarm) | ✅ (YT Music does **not** opt out of capture) |
| Keeps working with the screen off | ✅ | ✅ |
| Perceived latency (level disc vs beat) | "okay" | "quite the same" |
| Block rate / CPU | not measured by owner, so Phase 6 battery pass | idem |
| UX | no prompt, no indicator | screen-recording consent + system cast indicator for the whole session |

**Default: A (Visualizer).** It does the job, survives screen-off, and needs no ongoing screen-recording consent, which
the owner explicitly doesn't want. **B remains selectable** as a fallback (and gives 60 blocks/s and 16-bit
resolution if A ever goes silent after an OS update). `SettingsRepository.DEFAULT_AUDIO_SOURCE = VISUALIZER`.

Owner feedback that shaped Phase 3: *"it's mixing graves and agudos in the same visual, so sometimes it looks out of
tempo; I'd like either a distinction or modes that focus on 808s or highs"*. See D-024 and D-025.

---

## Phase 3 — DSP, modes, live preview

### D-024 · DSP pipeline (core, unit-tested)
`AudioAnalyzer`: ring buffer → `Fft` (1024, Hann, amplitude-normalised) → `SpectrumProcessor` (32 log bands
40 Hz–16 kHz, dB, auto-gain against a running peak decaying 6 dB/s over a 42 dB window, attack 15 ms / release
180 ms, peak hold 300 ms then gravity) → range energies (bass 30–150 Hz, mid 150 Hz–2 kHz, high 4–16 kHz, each
auto-gained + smoothed) → `BeatDetector` on bass energy (1.4× the 1 s rolling average, 250 ms refractory, strength 0..1).
- **The beat is gated on bass dominance** (bass ≥ 35 % of the full-band amplitude). Without the gate, hi-hats
  fired beats in the synthetic test: broadband noise leaks into the bass bins, and relative to its own quiet average it
  spikes. This is exactly the "looks out of tempo" symptom the owner described, so beats now come from kick/808 only.
- Low-end resolution limit: 1024 points at 48 kHz = 47 Hz bins. 808 fundamentals (40–60 Hz) all land in
  bins 1–2, so the "808s" focus shows energy and punch, not exact pitch. Source B could use a longer FFT later.
  Source A is capped at 1024 by the platform.
- Measured on the synthetic 120 BPM track: one beat every 500 ms ± 50 at both 60 Hz (B) and 20 Hz (A) block rates,
  zero beats on hats only, ≤ 1 on a steady bass tone. Tests: `BeatDetectorTest`, `SpectrumProcessorTest`, `FftTest`.

### D-025 · Modes and focus (answering the owner's feedback)
1. **Radial**: mirrored wedges, bass at the bottom and highs at the top, with peak dots. The core flashes on beats.
2. **Bars**: one bar per column, clipped to the circle, with falling peak dots.
3. **Pulse**: the bass sets the main ring radius, the mids a dim inner ring. Each beat launches a ring outward and flashes the rim.
4. **Bass · Treble** (new, from the owner's request): separate zones. The centre disc is bass, the outer ring is a treble
   sparkle, and a thin dim ring is the mids. A hi-hat can never move the kick visual.
- **Focus** (Full / 808s / Mids / Highs) zooms Radial and Bars into one range. `BandView` re-gains locally, because
  with the global gain "808s" saturated and "Highs" barely moved (hats sit ~20 dB under the kick). The local gain
  **resets when the focus changes**: a test caught 808s → Highs going dark because the bass level carried over.
- Idle: a breathing dot (perceived 7–17 %). With the engine stopped, a dim arc on the bottom rim hints "start the engine".
  Idle shows when the engine is off, or after 1.5 s of silence.
- Long press = next mode (the brief's default mapping) until Phase 4's recognizer. The choice is persisted.

### D-026 · `MatrixMode.render(audio, media, tMs, canvas: MatrixCanvas)`
This deviates from the brief's `out: IntArray`: the canvas wraps the same array and gives clipping plus a 0..1 API. Modes
draw **perceived** brightness. `MatrixRenderer` then applies mask → `OutputStage` (gamma LUT 2.2 + brightness cap,
default 80 %) → drive values. Gamma, brightness and fps (30/60) are settings.

### D-027 · One renderer for toy, app preview and PNGs
`Engine.renderer()` is shared. While the toy renders, it publishes each frame (`Engine.matrixFrame`) and the app
preview mirrors it. When the toy isn't active, the app drives the same renderer itself, so modes can be tuned with the
phone face up. `:core:renderPreviews` uses the same `MatrixRenderer` on synthetic audio, so the PNGs match the device.
The render path is allocation-free (frames, LUT and BandViews are preallocated). Slow frames > 8 ms and slow analysis > 4 ms are logged.

### D-028 · Phase 3.1: sync and balance fixes from on-device feedback (2026-10-28)
Owner, on Phase 3 with hip-hop: *"designs look nice but only partially in sync; bars look like they're only on one side."*
What the logs showed and what changed:

| Symptom / evidence | Cause | Fix |
|---|---|---|
| `AUDIO stats rate=19.4/s` | The Visualizer listener caps at ~20 Hz, and each 1024-sample snapshot covers 21 ms of every 50 ms, so kicks between snapshots are weak or missed and every hit is up to 50 ms late | **Poll `getWaveForm()` at 60 Hz** on the visualizer thread: overlapping windows of the most recent audio, 3× the update rate. The listener is kept as a fallback |
| Partial beat sync on hip-hop | "Energy > 1.4 × 1 s average": sustained 808s raise the average, so kicks stop standing out | **Bass spectral flux** (rise in log magnitude over 30–150 Hz bins) with an **adaptive threshold** (mean + 1.5·std over 1 s), a 180 ms refractory period, and the bass-dominance gate kept. New tests: overlapping 60 Hz polling, and sustained 808 notes (one beat per note) |
| `slow frame 12–16 ms` on every frame, on the **main thread** | Render + `setMatrixFrame` IPC on the UI thread, competing with Compose | Render loop on its own `GlyphWave-render` HandlerThread (display priority), fixed-rate drift-free scheduling. Render and push timings are logged separately (`RENDER … fps · render avg · push avg`) every 5 s |
| Bars "only on one side" | Bass on the left, where columns are short on a round matrix. Music's natural high-frequency roll-off makes the right side look dead | Bars **mirrored**: bass in the centre (tallest) columns, highs toward both edges. **Spectral tilt compensation of +3 dB/octave** around 1 kHz for every mode. Band release 180 → 120 ms for snappier motion |

### D-029 · Radial and Bass · Treble redesigned (owner: "bars 10× better, treble and radial ugly")
Root causes on a 25×25 matrix: hard on/off edges (jagged staircases), and no structure (Radial was lopsided
with bass at the bottom only; the treble was random per-LED sparkle that read as noise).
- **Anti-aliasing everywhere:** `MatrixCanvas.dot()` / `splat()` give edge LEDs partial brightness by coverage, so
  round shapes look round and moving dots glide.
- **Radial v2, a "breathing flower":** 4-fold mirror (bass on the vertical axis, highs on the horizontal one), 6 points
  per quarter, smoothed with their neighbours and cosine-interpolated. The shape is a circle that breathes with the overall
  level (0.9) plus gentle lobes from band differences (0.8). Bright anti-aliased rim, faint fill, beat-flash core,
  one slow rotation every 24 s. v1's 24 hard wedges and the intermediate spiky-star version were rejected on the PNG previews.
- **Bass · Treble v2:** anti-aliased bass disc, a **shockwave ring per beat**, and **12 treble dots orbiting** at r≈10.2. Each
  dot follows one slice of 4–16 kHz (mirrored), grows and brightens with it, and the orbit speeds up with treble energy.

### D-030 · Bars v3 and Pulse v2 get the Radial treatment (owner: "radial and treble look amazing, do the same to bars and pulse")
- **Bars v3:** a smoothed spectral profile (9 neighbour-blended points per half, cosine-interpolated across the 13 columns)
  so bars flow into each other. **Anti-aliased tops** (the top LED gets the fractional height), a faint glow above each bar,
  and sub-pixel peak dots at 50 %. Still mirrored, bass in the centre.
- **Pulse v2:** all rings are coverage-based (anti-aliased), and the fill fades into the main ring. The main ring breathes with
  gentle 4-fold mirrored spectral lobes (±2.4 cells), rotating every 30 s. Mids ring, beat shockwaves and rim flash are kept.
Design rule from now on, for every mode: **anti-aliased geometry, a smoothed and interpolated spectrum, mirrored structure.**

### D-031 · Visualizer error -3 ("Cannot initialize Visualizer engine")
Seen on device after reinstalls and restarts, while the UI still said RUNNING. Error -3 (NO_INIT) means a session-0 Visualizer is
still held: either ours, from an asynchronous release racing the next start, or another app's (likely suspect on Nothing
OS: Glyph Interface › Music visualization). Fixes:
- `stop()` releases **synchronously** (waits ≤ 1 s).
- Creation **retries 5× with backoff** (400, 800, 1200, 1600 ms), each attempt logged.
- After that, the error is **fatal for the engine**: the service stops, the UI shows STOPPED plus a hint (turn off the other
  visualizer, or use source B). A RUNNING status without audio can no longer happen.

---

## Phase 4 — gestures (started while the owner is away from the PC)

### D-032 · GestureRecognizer (core, 14 tests replaying the Phase 0 captures)
This refines D-015 on one point: **HOLD_MEDIUM fires on release, HOLD_LONG fires at the 4 s crossing.** If both fired at
their crossings, a 6 s hold would trigger play/pause *and then* now-playing. Now each crossing gives only *feedback*
(`HOLD_MEDIUM_REACHED`: haptic tick plus a ring marker), and the action is unambiguous:
- release before 2 s → tap (multi-press counting)
- release between 2 and 4 s → HOLD_MEDIUM
- reaching 4 s → HOLD_LONG immediately

Other rules:
- Multi-press window 600 ms from release to the next press. TRIPLE fires on the 3rd release without waiting.
- A press that becomes a hold discards pending taps.
- A press with no `change` (system short press) and an unbind mid-gesture both emit nothing (`CANCELLED` feedback).
- Change-only fallback: if a `change` arrives without a preceding down, the recognizer counts changes and `supportsHolds = false`.
- Pure logic with no clock: the toy feeds events and calls `advance(now)` every render frame. `holdProgress()` drives the ring.
