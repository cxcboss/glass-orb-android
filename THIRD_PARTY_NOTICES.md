# Third-party notices

## AndroidLiquidGlass / Backdrop

- Project: https://github.com/Kyant0/AndroidLiquidGlass
- Reference commit: `65ab177e90e5c1d8c62e70cf7755841982da65f6`
- Published dependency: `io.github.kyant0:backdrop:2.0.1`
- Published dependency: `io.github.kyant0:shapes:1.2.1`
- Copyright: Kyant and contributors
- License: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

The following source files are vendored byte-for-byte from that commit and are
compiled directly by this application:

- `components/LiquidButton.kt`
- `components/LiquidSlider.kt`
- `components/LiquidToggle.kt`
- `components/LiquidBottomTabs.kt`
- `components/LiquidBottomTab.kt`
- `utils/DampedDragAnimation.kt`
- `utils/DragGestureInspector.kt`
- `utils/InteractiveHighlight.kt`

They live under `app/src/main/java/com/kyant/backdrop/catalog/` with their original
package names. `utils/Coroutines.kt` is the only platform adapter: the reference
project's Kotlin Multiplatform `expect/actual` frame wait is expressed as a regular
Android function for this single-platform app. The unmodified Apache 2.0 license is
stored in `licenses/AndroidLiquidGlass-LICENSE.txt` and packaged in the APK assets.
