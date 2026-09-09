# Third-party notices

## AndroidLiquidGlass / Backdrop

- Project: https://github.com/Kyant0/AndroidLiquidGlass
- Reference commit: `65ab177e90e5c1d8c62e70cf7755841982da65f6`
- Reference only: the upstream project was inspected for optical and interaction ideas; no AndroidLiquidGlass runtime dependency is bundled in this build.
- Copyright: Kyant and contributors
- License: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0

No source files from AndroidLiquidGlass are copied into the application. The
settings surface uses Material 3 Android Views and opaque system-color surfaces.
No Apple restricted fonts or SF Symbols are bundled.

The unmodified Apache 2.0 license is stored in
`licenses/AndroidLiquidGlass-LICENSE.txt`, packaged in APK assets, and readable
inside the app's About page. Effect reference attribution and noncommercial
terms are documented separately in `NOTICE.md` and `LICENSE`.

## sherpa-onnx

- Runtime: sherpa-onnx Android AAR `1.13.7`, official release:
  https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.7
- Source project: https://github.com/k2-fsa/sherpa-onnx
- License: Apache License 2.0. The AAR is included unchanged at
  `app/libs/sherpa-onnx-1.13.7.aar`.
- Model: official `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01`
  release from the sherpa-onnx model catalogue:
  https://k2-fsa.github.io/sherpa/onnx/pretrained_models/small-online-models.html

Only the model's `model.int8.onnx` and `tokens.txt` are packaged. Recognition
is local and uses CPU execution with one inference thread; the app does not
send recorded audio or transcripts to a remote service.
