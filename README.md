# Incident Assistant

## Phase 1-2 conversation engine

The Android app now includes a local model-bundle importer, native Whisper/Qwen
runtime adapters, CfC/Laya ONNX adapters, natural-pause conversation capture,
revision-safe extraction, logged SQLite fixture tools and diagnostic export.
Open **Incident conversation** from the main screen. Trained weights must be
supplied separately; no model predictions are fabricated when weights are absent.

The current `asr_llm` mode runs pretrained **Whisper Base + Qwen3 0.6B**, with
CfC/Laya deferred. Run `python scripts/fetch_reference_models.py` to prepare the
pinned, verified bundle, then import `tmp/reference-models/incident-asr-llm.zip`
in the app. Native models are enabled by default for Android Studio Run.
See [integration and live phone testing](docs/reference-model-integration.md) and
[automated check results](docs/reference-model-validation.md).

Build the ARM64 native-enabled APK with `./gradlew.bat :app:assembleDebug -PnativeModels=true`
after running `powershell -File scripts/prepare_native.ps1`.
Android **7.0 / API 24** is required. All current inference adapters are explicitly
CPU reference implementations; Hexagon/QNN execution and physical-kit benchmarks
are pending. Android offline TTS supplies reference speech output.

See [implementation, model ABI and merge instructions](docs/phase1-phase2.md).
The original recording feature described below is also available.

Kotlin Android starter for SeedheCode's on-device incident reporting app.
First target: a regular Android phone. Later target: HDK8750 with QAIRT/QNN.

## What works in this starter

- Microphone permission, including denial and settings recovery.
- Foreground mono PCM16 recording with a live level meter and timer.
- Private WAV recordings, capped at two minutes; replay and confirmed deletion.
- Recordings persist across app restarts. Capture stops when the app leaves the foreground.
- An audio-frame interface for future local ASR/LNN/QNN implementations.
- No Internet permission, cloud API, fabricated transcription or model download.

The recording screen remains capture-only. The separate conversation screen uses
supplied local models for transcription, filtering, classification, extraction
and tool calling. Full forms/review/submission await the Phase 3-4 code merge.

## Run on your Android through Android Studio

1. Open the repository root (the folder containing `settings.gradle.kts`).
2. Set the Gradle JDK to **17** in Settings > Build Tools > Gradle. Android
   Studio's embedded compatible JDK can also be used.
3. Install **Android SDK Platform 34**, **NDK 27.2.12479018**, **CMake 3.22.1** and
   the SDK Build Tools using Tools > SDK Manager. Run
   `powershell -File scripts/prepare_native.ps1`, then sync the project.
4. Enable Developer options and USB debugging on your phone. Connect it by
   USB, approve the debugging prompt, and select the device in Android Studio.
5. Run the `app` configuration, grant microphone access, and tap **Record incident**.
6. Speak, tap **Stop and save**, and replay the recording. Test with airplane
   mode enabled: capture and playback remain local.

Android 7.0 / API 24 or newer with a microphone is required. This intentionally
uses native Android views to keep the starter small and dependencies minimal.
Pinned build stack: AGP 8.5.0, Kotlin 1.9.22, Gradle 8.11.1, JDK 17, SDK 34.
These are compatibility-oriented baseline versions, not a claim to use the
latest Android toolchain. Raise SDK/toolchain versions before a Play release.

CLI on Windows: `./gradlew.bat :app:assembleDebug :core:audio:testDebugUnitTest :app:lintDebug`
CLI on macOS/Linux: `sh gradlew :app:assembleDebug :core:audio:testDebugUnitTest :app:lintDebug`
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Directory structure

```text
app/                         Application, screen, permissions and playback
  src/main/java/.../
    feature/report/          Recording UI state; future reporting feature
  src/main/res/              Layouts, strings, theme and vector artwork
core/
  audio/                     AudioRecord capture, WAV writer and private library
  inference/                 Platform-neutral PCM and local engine contracts
docs/                        Architecture and physical-device smoke checks
gradle/wrapper/              Reproducible Gradle entry point
```

Local ideation, supplied PDF, taxonomy, QNN installer and existing scratch
files are ignored. They stay on the workstation and are not repository inputs.
No SDK path, signing credential, audio capture or model weight is committed.

## Next integration point

Replace `CaptureOnlyVoiceEngine` at composition time with a local engine. It
receives 100 ms PCM frames on the recording worker; enqueue bounded inference
work rather than blocking that callback. Default capture is 16 kHz, with device
fallback to 48/44.1 kHz. Read `AudioFrame.sampleRateHz` and resample explicitly
when a model requires 16 kHz. Session identity and transcript revisions are
part of the contract to support future corrections and streaming output.

See [architecture](docs/architecture.md) and [device checks](docs/device-testing.md).
