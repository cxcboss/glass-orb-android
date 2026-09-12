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
