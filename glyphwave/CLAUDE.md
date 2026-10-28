# GlyphWave — project rules for every session

YouTube Music visualizer for the **Nothing Phone (3) Glyph Matrix** (25x25 circular LED matrix),
shipped as a **Glyph Toy**, with media + visualizer functions on **long presses** of the Glyph Button.
Real daily-driver app and public portfolio project (github.com/nicodiprospero) — not a demo.

Read `DECISIONS.md` before changing anything structural. Log every non-obvious decision there.

## Working rules
- Work phase by phase (see bottom). Each phase is built, installed and verified before the next.
- Don't ask the owner about things that can be decided sensibly: decide, log in DECISIONS.md, continue.
  Only stop for physical actions on the phone (grant permission, look at the matrix, press the button),
  and then say exactly what to do and what they should see.
- End of each phase: report what was built, what to do on the phone, exact commands, expected result.
- Log tag: `GlyphWave`. Every test instruction includes the exact logcat filter, e.g.
  `adb logcat -v monotonic -s GlyphWave:V`
- Glyph debug mode (expires after 48 h — re-run if the toy stops responding):
  `adb shell settings put global nt_glyph_interface_debug_enable 1`
- Build: `./gradlew assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- JVM-only checks (no Android SDK needed): `./gradlew :core:test :core:renderPreviews`.
  Open `core/build/matrix-previews/*.png` and look at them before asking the owner to look at the phone.

## Stack
- Kotlin, Gradle KTS, version catalog (`gradle/libs.versions.toml`), latest stable AGP, JDK 17 bytecode.
- minSdk 34, compile/target = latest stable API.
- Jetpack Compose (companion app), Coroutines/Flow, DataStore (settings).
- No DI framework, no unnecessary libraries.
- Package: `dev.nicodiprospero.glyphwave`.
- Modules: `:app` (Android) + `:core` (pure Kotlin/JVM: DSP, modes, gestures, mask, canvas, font).
  `:core` must never depend on Android — that is the reason it exists (see DECISIONS.md D-003).

## Glyph Matrix SDK — facts
- AAR: `glyph-matrix-sdk-2.0.aar` → `app/libs/GlyphMatrixSDK.aar` (file dependency).
  **Never commit it** (Nothing's licence forbids redistribution). `app/libs/*.aar` is gitignored;
  `app/libs/README.md` explains where to download it.
- Permission: `com.nothing.ketchum.permission.ENABLE`.
- Toy = exported Service, intent-filter action `com.nothing.glyph.TOY`, meta-data:
  `com.nothing.glyph.toy.name` (string res, required), `.image` (preview drawable, required),
  `.summary` (optional), `.introduction` (activity class → companion app),
  `.longpress = "1"` (REQUIRED here), `.aod_support` (leave off in v1).
- Lifecycle: system binds when the toy is selected. `onBind` → `GlyphMatrixManager.getInstance(ctx).init(cb)`;
  in `onServiceConnected` → `register(Glyph.DEVICE_23112)`; `onBind` returns a `Messenger` binder.
  `onUnbind` → `turnOff()`, `unInit()`, stop everything (render loop, coroutines).
- Events: `msg.what == GlyphToy.MSG_GLYPH_TOY` (=1), string in bundle key `GlyphToy.MSG_GLYPH_TOY_DATA` (="data"):
  `EVENT_CHANGE`="change" (long press), `EVENT_ACTION_DOWN`="action_down", `EVENT_ACTION_UP`="action_up",
  `EVENT_AOD`="aod" (not used in v1).
- **Short press belongs to the system** (cycles toys). Never rely on it.
- Rendering from a toy: `setMatrixFrame(IntArray)` (or GlyphMatrixFrame builders, max 3 layers).
  `setAppMatrixFrame` is only for normal apps. Both `setMatrixFrame` overloads throw `GlyphException`
  if called before the service is connected — always guard.
- Matrix length: `Common.getDeviceMatrixLength()` at runtime. The literal 25 exists in ONE constant only:
  `MatrixSpec.DEFAULT_LENGTH`.
- Circular matrix: 489 physical LEDs on Phone (3). Mask = cells with `(2dx)²+(2dy)² ≤ n²`
  (dx, dy from the center cell) — verified cell-by-cell against the kit's LED allocation diagram.
  Apply `CircleMask` to every frame.
- Pixel value range: SDK's own bitmap renderer uses 0..4095 (`MAX_BRIGHTNESS = 4095`, 255×16).
  `MatrixSpec.MAX_VALUE = 4095`. Status of on-device confirmation: see DECISIONS.md D-004.
- Glyph Toys manager: `ComponentName("com.nothing.thirdparty",
  "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")` (system ≥ 20250829).

## Architecture (single process)
Shared state through singleton `Engine` (StateFlows):
1. `AudioEngineService` — foreground service, captures playback audio, publishes `AudioFrame` ~60 Hz
   (FFT bands, RMS, bass energy, beat flag + strength, waveform). Started only from foreground
   (app button / QS tile). The toy never starts capture.
2. `MediaBridge` — NotificationListenerService + `MediaSessionManager.getActiveSessions()`; tracks
   `com.google.android.apps.youtube.music` (title, artist, art, PlaybackState, position, customActions),
   exposes transport controls.
3. `GlyphWaveToyService` — the toy. Owns the render loop (30 fps default, 60 optional), runs the
   active `MatrixMode`, applies mask + gamma + brightness cap, pushes `setMatrixFrame`. Glyph Button
   events → `GestureRecognizer` → actions. Engine not running → idle animation + "start engine" hint.
Plus `MainActivity` (Compose companion) and a Quick Settings tile (start/stop engine).

Audio sources behind `interface AudioSource { fun start(); fun stop(); val frames: Flow<RawAudio> }`:
A) `Visualizer` on session 0 (detect all-zeros > 2 s while YT Music PLAYING → surface in UI + logs);
B) MediaProjection + `AudioPlaybackCaptureConfiguration` → AudioRecord 48 kHz PCM16 → own FFT.

## DSP / rendering rules
- Hann window, FFT 1024 (configurable), magnitude → dB, 24–32 log bands 40 Hz–16 kHz.
- Auto-gain vs slowly decaying running peak. Per-band fast attack / slow release + peak-hold with gravity.
- Beat: bass energy vs ~1 s rolling avg, threshold ~1.4×, ~250 ms refractory, strength 0..1.
- Gamma (~2.2) + global brightness cap, configurable.
- **Zero allocations per frame** in the render path. Warn in logs if a frame takes > 8 ms.

## Modes
`interface MatrixMode { val id; val name; fun render(audio, media, tMs, out: IntArray) }`
Radial spectrum, Classic bars, Pulse, Album art, Oscilloscope, Particles, Now playing (own 5x7 font,
never SDK setText). Idle: breathing dot / rotating arc at very low brightness.
**Visual design rule (owner-approved, D-029/D-030):** every mode uses anti-aliased geometry
(`MatrixCanvas.dot/splat`, coverage-based rings and edges), a smoothed + interpolated spectrum, and a
mirrored/balanced structure. Hard on/off edges and random per-LED noise look bad on 25×25.

## Gestures
Recognizer is pure Kotlin, unit-tested, all timings configurable, built on the OBSERVED event sequence
(DECISIONS.md). Emits LONG_PRESS, DOUBLE_LONG_PRESS, TRIPLE_LONG_PRESS, HOLD_MEDIUM (~2 s), HOLD_LONG (~4 s).
Default map: long → next mode; double → next track; triple → prev track; hold medium → play/pause;
hold long → Now playing. Every gesture: distinct haptic + 400–600 ms icon; hold shows a progress ring.

## Battery & robustness
Stop capture after YT Music paused 30 s (configurable), resume on play (only if user-started).
onUnbind stops the loop; never leak threads; always release Visualizer/AudioRecord.

## Testing
JVM tests in `:core` (SpectrumProcessor, BeatDetector, GestureRecognizer, CircleMask, ...).
`./gradlew :core:renderPreviews` → PNGs (round dots on black, masked) in `core/build/matrix-previews/`.

## Phases
0 scaffold + toy test pattern + raw event logger · 1 MediaBridge · 2 audio sources A+B + report ·
3 DSP + modes 1–3 + idle + live preview + PNGs · 4 gestures/actions/haptics/icons/ring ·
5 remaining modes, settings, QS tile · 6 robustness, README, release config, own GitHub repo.
