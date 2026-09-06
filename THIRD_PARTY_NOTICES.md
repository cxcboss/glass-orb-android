# Third-party notices

## AndroidLiquidGlass / Backdrop

- Project: https://github.com/Kyant0/AndroidLiquidGlass
- Reference commit: `65ab177e90e5c1d8c62e70cf7755841982da65f6`
- Dependencies: `io.github.kyant0:backdrop:2.0.1`, `io.github.kyant0:shapes:1.2.1`
- Copyright: Kyant and contributors
- License: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

Vendored files retain their original package names under
`app/src/main/java/com/kyant/backdrop/catalog/`.

### Application modifications in 1.5.0-demo

- `components/LiquidSlider.kt`: latest value/callback holders; animation recreation when range or threshold changes; one 44dp-high press-and-drag target; clamped absolute coordinate mapping with RTL support; cancellation and multi-pointer cleanup; accessible range/set-progress semantics; default marker drawn beneath the thumb; cached capsule and iOS semantic accent colors. Original lens, highlight, damping, velocity stretch, shadows, and track backdrop composition remain.
- `components/LiquidToggle.kt`: latest selection/callback holders; standard Switch toggle semantics and cancellation through Compose toggleable; 51×31dp track / 27dp thumb within a 51×44dp touch target; current-state synchronization; cached capsule. Original lens, highlight, damping and stretch remain; the custom drag gesture is replaced with a standard tap switch.
- `components/LiquidButton.kt`: cached capsule shape and 44dp height. Original optical and interactive highlight implementation remains.
- `utils/Coroutines.kt`: existing single-platform adapter expressing the upstream Kotlin Multiplatform frame wait as a regular Android function.

These modified files are derived from the reference commit and are no longer
claimed to be byte-for-byte copies.

The following vendored files remain unmodified:
`components/LiquidBottomTabs.kt`, `components/LiquidBottomTab.kt`,
`utils/DampedDragAnimation.kt`, `utils/DragGestureInspector.kt`,
`utils/InteractiveHighlight.kt`.
The two bottom-tab components have no runtime usage in the settings interface.

The app uses one screen-level Backdrop recording layer plus small control-local
track layers. Settings groups are ordinary opaque system-color surfaces, not
glass cards. No Apple restricted fonts or SF Symbols are bundled.

The unmodified Apache 2.0 license is stored in
`licenses/AndroidLiquidGlass-LICENSE.txt`, packaged in APK assets, and readable
inside the app's About page. Effect reference attribution and noncommercial
terms are documented separately in `NOTICE.md` and `LICENSE`.
