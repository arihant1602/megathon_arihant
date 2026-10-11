# Sentinel AI: on-device voice incident assistant

Report a workplace incident by talking. The app transcribes speech (Whisper, encoder on the NPU), works out the
incident type (fine-tuned Laya classifier on the NPU), asks only for what is missing, extracts the details (Qwen3-0.6B)
and speaks its questions (Piper). Everything runs on the device; no network is used.

**Supported device: Qualcomm Snapdragon 8 Elite (SM8750) with Android 15**, e.g. the SM8750 HDK / dev kit used at
Megathon. The NPU models are compiled for the SM8750's Hexagon v79 HTP and will not run on other chips.

## How to run it

Tested on Linux (Arch) with the versions below. The helper scripts are bash. On Windows, use WSL.

### 1. Prerequisites

| What | Version | Notes |
|---|---|---|
| JDK | **17** (exactly) | One module requires a Java 17 toolchain. Point `JAVA_HOME` at it. |
| Android SDK | platform **34**, build-tools **34.0.0** | Gradle installs these by itself if the SDK licenses are accepted. |
| Android NDK | **27.2.12479018** | Or any installed NDK via `-PndkVersion=<version>` (also built with 30.0.16248370). |
| CMake | **3.22.1** (from the SDK) | Or a system CMake: `-PcmakeVersion=<version>` plus `cmake.dir=<prefix>` in `local.properties` (also built with 4.4.3). |
| Qualcomm AI Runtime SDK (QAIRT / SNPE) | **2.47.0.260601** | From Qualcomm (free account). Provides the NPU runtime libraries and headers. The model files were compiled with this version. |
| git, curl, python3, adb | any recent | `adb` from [Android platform-tools](https://developer.android.com/tools/releases/platform-tools). |
| Device | SM8750, Android 15 | USB debugging on (Settings → About → tap *Build number* 7 times → Developer options → *USB debugging*). About 2.5 GB free storage. |

Install the SDK parts with the SDK manager if you don't use Android Studio:
```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0" "ndk;27.2.12479018" "cmake;3.22.1"
```

### 2. Clone and configure

```bash
git clone https://github.com/arihant1602/megathon_arihant.git
cd megathon_arihant
echo "sdk.dir=$HOME/Android/Sdk" > local.properties        # your Android SDK path
```

Tell Gradle where the QAIRT SDK is, either on every command line (`-PsnpeSdk=...`, used below) or once in
`~/.gradle/gradle.properties`:
```properties
snpeSdk=/path/to/qairt/2.47.0.260601
```
The folder must contain `lib/aarch64-android/libSNPE.so`, `lib/hexagon-v79/unsigned/libSnpeHtpV79Skel.so` and
`include/SNPE/`. Without `snpeSdk` the app builds but cannot run its NPU models.

### 3. Fetch the native sources and the voice runtime

```bash
scripts/prepare_native.sh          # whisper.cpp + llama.cpp at the pinned commits -> third_party/
scripts/fetch_sherpa_onnx.sh       # sherpa-onnx 1.13.8 Android runtime (voice) -> third_party/sherpa-onnx/
```

### 4. Build

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug -PsnpeSdk=/path/to/qairt/2.47.0.260601
```
The APK is `app/build/outputs/apk/debug/app-debug.apk` (about 70 MB, without models). The first build compiles
whisper.cpp and llama.cpp and takes several minutes.

Optional checks: `./gradlew :app:testDebugUnitTest :core:inference:test`. These are the Kotlin-vs-Python parity tests,
the endpointing tests and the speech-filter tests.

### 5. Download the models

```bash
scripts/download_models.sh models
```
This downloads about 2.1 GB into `models/` and verifies every checksum. If interrupted, run it again; it resumes:

```
models/
├── laya_w8a16_htp.dlc                      fine-tuned Laya incident classifier, W8A16, SM8750 HTP    (GitHub release models-v1)
├── whisper_base_encoder_fp16_htp.dlc       Whisper encoder on the NPU, 30 s window                   (GitHub release models-v1)
├── whisper_base_encoder_10s_fp16_htp.dlc   Whisper encoder on the NPU, 10 s window                   (GitHub release models-v1)
├── ggml-base.bin                           Whisper base (multilingual), whisper.cpp format           (Hugging Face, pinned)
├── Qwen3-0.6B-Q8_0.gguf                    Qwen3-0.6B, llama.cpp format                              (Hugging Face, pinned)
└── voice/                                  Piper en_US lessac-medium for sherpa-onnx                 (sherpa-onnx release)
```

The release files are at https://github.com/arihant1602/megathon_arihant/releases/tag/models-v1. The two encoder files
can also be rebuilt from `ggml-base.bin` with `scripts/whisper_npu/build_encoder_dlc.sh` (needs the QAIRT tools and
Python with `torch` and `onnx`).

### 6. Install the app and copy the models to the device

```bash
adb devices                                          # note the serial
adb uninstall org.seedhecode.incidentassistant      # only if another build of this app is installed
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
ANDROID_SERIAL=<serial> scripts/push_models_kit.sh models/*
```
`-g` grants the microphone permission up front. `push_models_kit.sh` copies the files into the app's private storage
(`files/models/`) through `run-as`, which is why this uses the **debug** build. It prints the folder listing at the
end; all five files and `voice/` must be there.

### 7. Open the app

Open **Sentinel AI** on the device. After a couple of seconds the home screen badges read **Runs offline on this
device · Incident type on NPU · Speech on NPU · Natural voice**. Then it's ready.

---

## Using the app

1. Choose **Report an incident** (or **Facilities desk** to see filed reports) → **Continue**.
2. Tap **Start talking** (or the **Talk** tab, then the mic button) and say what happened and where, e.g.
   *"There's water dripping from the ceiling near the pantry, N building, fourth floor."* Then just stop talking:
   a short pause ends your turn.
3. The assistant speaks its question and the mic re-opens by itself. Answer out loud. It first makes sure it knows
   **what** happened and **where** (building and floor), then says your report is ready and asks for optional details.
4. Say **"done"** (or tap **File report now**) to file. The report shows its priority, the team it went to and a
   respond-by time.
5. **See the form** shows everything extracted so far (tap a field to correct it). **History** lists your reports.
   **Facilities desk** shows all reports, most urgent first. A report's page can be read aloud (**Listen to report**)
   and marked *in progress* or *resolved*.

Buildings known to the demo site: **N** and **QRC** (from `app/src/main/assets/incident_taxonomy.json`).

## Troubleshooting

| Symptom | Fix |
|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Another build is installed: `adb uninstall org.seedhecode.incidentassistant`, then install again. |
| "The on-device models didn't load: Missing model files …" | Step 6 didn't copy everything; run `push_models_kit.sh` again and check the listing. |
| `push_models_kit.sh` says to install the debug APK first | `run-as` only works with the debug build from step 4; install it, then push again. |
| Error mentioning SNPE, DSP or `ADSP` | Not an SM8750 device, or the app was built without `-PsnpeSdk`. |
| Gradle: "Cannot find a Java installation … languageVersion=17" | `JAVA_HOME` must point at a JDK 17. |
| CMake / NDK version not found | Install them with `sdkmanager` (step 1) or pass `-PndkVersion` / `-PcmakeVersion` for installed ones. |
| The mic never hears you (the caption stays empty) | Check that the device's microphone records in another app. Some SM8750 HDK units ship with a broken microphone route that records silence. |
| It answers background noise | Speak close to the device; turns quieter than about −35 dBFS and music/noise transcripts are ignored. |
| No voice | Check the media volume. The top-bar **Voice on/off** toggle must be on. |

## Optional: an all-in-one APK

`scripts/build_full_apk.sh models sentinel-ai.apk /path/to/scratch` builds a release APK with every model inside
(about 1.7 GB; needs about 5 GB free scratch space and 8 GB of RAM). Install it with `adb install -r -g sentinel-ai.apk`
and skip step 6; the app unpacks the models on first launch (about 10–30 seconds).
