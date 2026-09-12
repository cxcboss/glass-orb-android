# Task 2 report: dynamic caustic palette

## Result

Implemented dynamic caustic coloring from the live effect texture. The existing effect and scene paths remain intact; the only new render work is a reusable 1x1 RGBA8 palette reduction pass with a fixed 8x8 sample grid.

## Implementation

- Added `app/src/main/assets/shaders/palette.frag`.
  - Samples the rendered effect texture at 64 fixed, distributed locations.
  - Accumulates alpha-weighted linear RGB and stores the weighted RGB average plus mean coverage in the 1x1 target.
  - Luminance is recovered from the packed RGB in the glass shader, so no CPU readback or `glReadPixels` is used by production rendering.
- Added `DynamicPaletteReduction`, a pure CPU mirror used by focused tests.
  - Covers red, blue, alpha-weighted red/blue mixing, luminance changes, empty coverage fallback, and weak-coverage fallback.
  - The fallback is finite and hue-derived.
- Updated `GlOrbPipeline`.
  - Creates and binds `paletteTarget` after the effect pass and before the scene pass.
  - Binds the palette texture to an explicit glass sampler unit.
  - Keeps the last valid palette when `collapseEffectsFrozen` skips the animated off-screen passes.
  - Releases/recreates the palette target and program in the same lifecycle paths as the existing render targets/programs.
- Updated `glass.frag`.
  - Removed fixed warm/cool caustic colors.
  - Uses the live reduced RGB and brightness for both caustic and core glow, with a safe hue-derived fallback for empty/weak coverage.

No rotation, overlay geometry, background blur, independent caustic slider, or final performance refactor was added.

## Verification

RED check before implementation:

```text
./gradlew testDebugUnitTest --tests com.cxcboss.glassorb.render.PaletteReductionTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Failed at the expected missing `palette.frag` assertion.

Focused palette tests:

```text
./gradlew testDebugUnitTest --tests com.cxcboss.glassorb.render.PaletteReductionTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Passed.

Full requested JVM suite on the final tree:

```text
./gradlew testDebugUnitTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Passed. Existing deprecation warnings in `OverlayWindowController` and `MainActivity` remain unchanged.

Focused GLES/render instrumentation was also attempted:

```text
./gradlew connectedDebugAndroidTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache -Pandroid.testInstrumentationRunnerArguments.class=com.cxcboss.glassorb.render.GlassPixelsTest
```

The debug app and test APK compiled successfully, but execution stopped with `No connected devices!`. Therefore real-device shader compilation, pixel output, dynamic color response, and 120 fps behavior remain unverified in this environment.

## Remaining concern

The new shader/program wiring is compiled as part of the Android asset/build pipeline and covered by the existing GLES renderer test harness, but that harness could not run without a connected Android device. A device-side `GlassPixelsTest` pass is still required before claiming visual acceptance.

## Review follow-up on d703d4d

- Added the executable JVM wiring contract in `DynamicPaletteBindings` and `DynamicPalettePassPlan`. `GlOrbPipeline` now uses the shared binding objects for effect, palette, scene sampler units, sampler uniform names, and the palette resolution uniform. JVM tests assert non-conflicting consumer units, the `effect -> palette -> scene -> glass` pass order, and the palette/glass binding names.
- Aligned `DynamicPaletteReduction.recover()` with the exact `glass.frag` recovery constants and operations: fallback coverage `0.01`, full confidence coverage `0.06`, `smoothstep` confidence, brightness gain `3.0`, minimum brightness `0.12`, fallback luminance interpolation, chroma normalization, and clamping. Tests lock the empty, weak, partial-confidence, mixed, and brightness cases.
- The focused contract/recovery JVM tests and the complete `testDebugUnitTest` suite passed on the final tree. Android resource/asset compilation also completed successfully as part of the Gradle test/build tasks, including the new palette shader asset and its pipeline source wiring. This covers the executable contract and asset compilation that are measurable without a device; real GLES shader execution and pixel output remain pending a connected Android device.
