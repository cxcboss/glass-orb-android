# Task 1 report: physical rotation-space geometry and touch mapping

## Implementation

- Added the pure `RotationCoordinateSpace` helper with deterministic Android 0/90/180/270 point transforms, inverse transforms, portrait reference dimensions, inset conversion, and axis-aligned clamped placement.
- Added `RotationDisplayGeometry` as the shared physical-display snapshot for layout consumers.
- Updated `OverlayLayout` to derive collapsed, expanded, capsule-touch, orb-touch, and dim-gradient geometry from the mapped portrait anchor. Rectangles remain axis-aligned; configured orb width and height remain independent. `expandBelowCapsule` is included in expanded orb touch/dim placement.
- Updated `OverlayWindowController` to pass one current physical display snapshot through initial layout, configuration/rotation relayout, accessibility/touch proxy bounds, shape input region, and background dim geometry. No orientation lock was added.
- Preserved the existing status-bar/fullscreen/landscape hiding policy and animation state flow. Touch direction remains current-screen direction because gesture deltas continue to use `MotionEvent.rawY`; upward remains collapse and downward remains restore/expand.

## TDD and tests

### RED

Command:

```text
./gradlew testDebugUnitTest --tests com.cxcboss.glassorb.overlay.RotationCoordinateSpaceTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Real result: `:app:compileDebugUnitTestKotlin FAILED` with unresolved references for the not-yet-created `FloatPoint`, `RotationCoordinateSpace`, and `RotationDisplayGeometry` APIs.

### Focused rotation/layout tests

Command:

```text
./gradlew testDebugUnitTest --tests '*RotationCoordinateSpaceTest' --tests '*OverlayLayoutTest' --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Real result:

```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 19s
31 actionable tasks: 31 executed
```

The run emitted only the existing Kotlin/Java deprecation warnings for `Display.getRealSize`, `MainActivity.statusBarColor`, and `MainActivity.navigationBarColor`.

### Full debug unit-test suite

Command:

```text
./gradlew testDebugUnitTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Real result:

```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 19s
31 actionable tasks: 31 executed
```

The same existing deprecation warnings were emitted; there were no test failures or compilation errors.

## Changed files

- `app/src/main/java/com/cxcboss/glassorb/overlay/RotationCoordinateSpace.kt`
- `app/src/main/java/com/cxcboss/glassorb/overlay/OverlayLayout.kt`
- `app/src/main/java/com/cxcboss/glassorb/overlay/OverlayWindowController.kt`
- `app/src/test/java/com/cxcboss/glassorb/overlay/RotationCoordinateSpaceTest.kt`

## Self-review

- Rotation tests cover all four rotations, inverse round trips, both physical landscape sides, inset conversion, and edge clamping.
- Layout regression verifies that landscape placement moves to the physical side while preserving the capsule dimensions; additional tests cover left/right portrait anchors, independent orb dimensions, and `expandBelowCapsule` spacing.
- The controller's current-screen gesture axis was intentionally left unchanged and no orientation-lock or unrelated rendering changes were introduced.
- `git diff --check` completed with no whitespace errors.

## Concerns

- No Android emulator or physical-device visual acceptance was available in this run. OEM-specific `WindowMetrics`/cutout/navigation inset behavior, especially during a live 180-degree transition, should still be checked on target hardware. Unit tests cover the deterministic coordinate and clamping rules.
- The build retains the pre-existing deprecation warnings noted above.

## Reviewer repair report

Addressed the three Important findings:

1. `RotationCoordinateSpace.portraitSafeBounds()` now preserves the mapped portrait `left`, `top`, `right`, and `bottom` insets for all four rotations. Tests assert the complete edge mapping for 0/90/180/270, including non-zero top/cutout values.
2. `OverlayWindowController.displayGeometry()` now captures the `Display` object, rotation, physical bounds, and insets as one immutable `RotationDisplayGeometry` snapshot. API 29 still uses `getRealSize()` plus the existing root-window/system-dimension inset fallback. Configuration/rotation updates compute one `ExpandedFrameGeometry` and reuse the same display/frame through bounds, touch, and dim updates; initial creation also reuses the snapshot for dim parameters and accessibility touch bounds.
3. Added `OverlayLayout.expandedFrame()`, which returns the final clamped/top-room-adjusted expanded bounds together with its clamped visual dim endpoint. `expandedBounds()`, `expandedVisualBottomPx()`, and controller dim updates reuse this final frame result.
4. Added direct mapped-display regressions for capsule touch bounds, enlarged edge touch behavior, expanded/orb touch dimensions, and the final expanded-frame/dim endpoint relationship.

### Repair verification

Focused command:

```text
./gradlew testDebugUnitTest --tests '*RotationCoordinateSpaceTest' --tests '*OverlayLayoutTest' --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Real result:

```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 19s
31 actionable tasks: 31 executed
```

Full command:

```text
./gradlew testDebugUnitTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Real result:

```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 17s
31 actionable tasks: 31 executed
```

Both repair runs emitted only the existing deprecation warnings for
`Display.getRealSize`, `MainActivity.statusBarColor`, and
`MainActivity.navigationBarColor`; no test failures or compilation errors remained.

## Follow-up repair: same-frame presentation and stable rotation reads

### Implementation

- Added `PresentationGeometry` so one frame owns its `RotationDisplayGeometry`
  and `ExpandedFrameGeometry`. `RenderSnapshot` carries that immutable context;
  the frame input-region update and background-dim update reuse it, with no
  second `displayGeometry()` read after snapshot construction.
- API 30+ display reads now pair `currentWindowMetrics` with rotation reads,
  retry three times, accept the newest sample whose before/after rotation and
  geometry rotation agree, and retain the previous coherent snapshot while a
  rotation transition remains unstable.
- API 29 continues to use `Display.getRealSize()` and the existing root-window
  or system-dimension inset fallback; the already-read rotation is passed into
  that fallback instead of being read again.
- Added pure tests for stable-sample rejection/fallback, same-frame
  presentation context, all rotation edge-inset mappings, and axis-aligned
  mapped bounds.

### Follow-up verification

Focused rotation/layout tests:

```text
./gradlew testDebugUnitTest --tests '*RotationCoordinateSpaceTest' --tests '*OverlayLayoutTest' --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Real result: `BUILD SUCCESSFUL in 19s` (`31 actionable tasks: 31 executed`).

Full debug unit-test suite:

```text
./gradlew testDebugUnitTest --no-daemon --project-cache-dir /tmp/glass-orb-gradle-project-cache
```

Real result: `BUILD SUCCESSFUL in 18s` (`31 actionable tasks: 31 executed`).

Both runs emitted only the existing deprecation warnings for
`Display.getRealSize`, `MainActivity.statusBarColor`, and
`MainActivity.navigationBarColor`.

### Follow-up concerns

- No Android emulator or physical-device visual acceptance was available in
  this run; live OEM rotation and inset behavior remains a hardware check.
- The pre-existing deprecation warnings remain.
