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

### D-004 · Pixel value range of `setMatrixFrame(IntArray)` — **0..4095 (pending on-device confirmation)**
Evidence:
- `GlyphMatrixManager.setMatrixFrame(int[])` passes the array straight to `IGlyphService.setMatrixColors`
  with no scaling (bytecode of glyph-matrix-sdk-2.0.aar).
- The SDK's own bitmap → matrix converter (`GlyphMatrixUtils`) has `MAX_BRIGHTNESS = 4095` and
  `BRIGHTNESS_MULTIPLIER = 16`. It maps 0..255 grey to 0..4080/4095, and text ON = 4095.
- The example project is inconsistent: its animation demo uses 255 and its random demo uses 0..2047. With a
  12-bit range, its "255" pixels are only about 6% brightness.

Decision: `MatrixSpec.MAX_VALUE = 4095`. Modes always draw normalised 0..1 floats through `MatrixCanvas`, so the
range is a single constant if the phone proves otherwise.
**Confirmation test:** the toy's `VALUE_RANGE` pattern (see the Phase 0 checklist). Result: _TBD by owner_.

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
