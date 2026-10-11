# Sentinel AI: on-device voice incident assistant

Report a workplace incident by talking. The app transcribes speech (Whisper, encoder on the NPU), works out the
incident type (fine-tuned Laya classifier on the NPU), asks only for what is missing, extracts the details (Qwen3-0.6B)
and speaks its questions (Piper). Everything runs on the device; no network is used.

**Supported device: Qualcomm Snapdragon 8 Elite (SM8750) with Android 15**, e.g. the SM8750 HDK / dev kit used at
Megathon. The NPU models are compiled for the SM8750's Hexagon v79 HTP and will not run on other chips.

There are two ways to run it:

- **[Option A: install the ready-made APK](#option-a-install-the-ready-made-apk)** (about 5 minutes; everything is inside).
- **[Option B: build from source](#option-b-build-from-source)** (for developers).

---

## Option A: install the ready-made APK

The APK (`sentinel-ai-v1.0.apk`, 1.68 GB) contains the app and all models.

SHA-256: `c21fb2086531994bd1e714a28cd0548d72074193c1ca35d0229ef5c42b8a3195`

**Download:** __APK_DOWNLOAD_LINK__

### What you need

- The SM8750 device, with **USB debugging** on (Settings → About → tap *Build number* 7 times → Developer options →
  *USB debugging*).
- A computer with `adb` ([Android platform-tools](https://developer.android.com/tools/releases/platform-tools)).
- **About 5 GB free on the device**: the APK plus the models it unpacks on first launch (2.1 GB).

### Steps

1. Connect the device and check that it is listed:
   ```bash
   adb devices
   ```
   If several devices are listed, add `-s <serial>` after `adb` in the commands below.
2. If any other build of this app is installed (for example a teammate's), remove it first. Android refuses to
   install over an app signed with a different key:
   ```bash
   adb uninstall org.seedhecode.incidentassistant
   ```
   ("Unknown package" just means it was not installed.)
3. Install the APK (it is large, so this takes a minute):
   ```bash
   adb install -r -g sentinel-ai-v1.0.apk
   ```
   `-g` grants the microphone permission up front. Without it, the app asks the first time you tap the mic.
4. Open **Sentinel AI** on the device. The **first launch unpacks the models** (about 10–30 seconds). The home
   screen shows *"Preparing on-device models (first launch only)… NN%"*, then *"Loading on-device models…"*.
   It is ready when the badges read **Runs offline on this device · Incident type on NPU · Speech on NPU ·
   Natural voice**. Later launches take a few seconds.

Then see [Using the app](#using-the-app).

---

## Option B: build from source

Tested on Linux (Arch) with the versions below. The helper scripts are bash. On Windows, use WSL, or
`scripts/prepare_native.ps1` for step 3.

### 1. Prerequisites

| What | Version | Notes |
|---|---|---|
| JDK | **17** (exactly) | One module requires a Java 17 toolchain. Point `JAVA_HOME` at it. |
| Android SDK | platform **34**, build-tools **34.0.0** | Gradle installs these by itself if the SDK licenses are accepted. |
| Android NDK | **27.2.12479018** | Or any installed NDK via `-PndkVersion=<version>` (also built with 30.0.16248370). |
| CMake | **3.22.1** (from the SDK) | Or a system CMake: `-PcmakeVersion=<version>` plus `cmake.dir=<prefix>` in `local.properties` (also built with 4.4.3). |
| Qualcomm AI Runtime SDK (QAIRT / SNPE) | **2.47.0.260601** | From Qualcomm (free account). Provides the NPU runtime libraries and headers. The model files were compiled with this version. |
| git, curl, python3, unzip, adb | any recent | |

Install the SDK parts with the SDK manager if you don't use Android Studio:
```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0" "ndk;27.2.12479018" "cmake;3.22.1"
```

### 2. Clone and configure

```bash
git clone https://github.com/IIIT-ECell/megathon26-seedhecode.git
cd megathon26-seedhecode
git checkout integration
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
bash ./gradlew :app:assembleDebug -PsnpeSdk=/path/to/qairt/2.47.0.260601
```
The APK is `app/build/outputs/apk/debug/app-debug.apk` (about 77 MB, without models). The first build compiles
whisper.cpp and llama.cpp and takes several minutes.

Optional checks: `bash ./gradlew :app:testDebugUnitTest :core:inference:test`. These are the Kotlin-vs-Python parity
tests, the endpointing tests and the speech-filter tests.

### 5. Get the model files

Put them in one folder, laid out exactly like this (it mirrors the app's `files/models/`):

```
models/
├── laya_w8a16_htp.dlc                      901,310,747 bytes  fine-tuned Laya incident classifier, W8A16, SM8750 HTP
├── ggml-base.bin                           147,951,465 bytes  Whisper base (multilingual), whisper.cpp format
├── whisper_base_encoder_fp16_htp.dlc       261,897,167 bytes  Whisper encoder on the NPU, 30 s window
├── whisper_base_encoder_10s_fp16_htp.dlc    89,926,975 bytes  Whisper encoder on the NPU, 10 s window
├── Qwen3-0.6B-Q8_0.gguf                    639,446,688 bytes  Qwen3-0.6B, llama.cpp format
└── voice/                                                     Piper en_US lessac-medium (sherpa-onnx)
    ├── en_US-lessac-medium.onnx
    ├── en_US-lessac-medium.onnx.json
    ├── tokens.txt
    └── espeak-ng-data/
```

**Easiest: take them out of the ready-made APK** (Option A's download). The APK is a zip:
```bash
unzip sentinel-ai-v1.0.apk 'assets/models/*' -d apk_models
mv apk_models/assets/models models && rm -r apk_models models/manifest.json
```

**Or get each one yourself:**

| File | Where it comes from |
|---|---|
| `ggml-base.bin` | `curl -L -o models/ggml-base.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.bin` |
| `Qwen3-0.6B-Q8_0.gguf` | `curl -L -o models/Qwen3-0.6B-Q8_0.gguf https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/23749fefcc72300e3a2ad315e1317431b06b590a/Qwen3-0.6B-Q8_0.gguf` |
| `voice/` | `scripts/fetch_sherpa_onnx.sh models` (fetches the runtime again and the voice into `models/voice/`) |
| the two `whisper_base_encoder_*.dlc` | `scripts/whisper_npu/build_encoder_dlc.sh models/ggml-base.bin models`. Needs the QAIRT SDK environment (`qairt-converter`, `snpe-dlc-graph-prepare` on `PATH`) and Python with `torch` + `onnx`. Delete the intermediate `encoder_*` files it leaves in `models/`. |
| `laya_w8a16_htp.dlc` | Our fine-tuned model; take it from the APK. It is produced by the training/export pipeline on the `classifier-model` branch (`classifier/`): LoRA fine-tune → ONNX with bounded attention masks and one concatenated output → QAIRT W8A16 → HTP graph prepared for SM8750. |

Check that you have the right files (`cd models && sha256sum laya_w8a16_htp.dlc ggml-base.bin whisper_base_encoder_*.dlc Qwen3-0.6B-Q8_0.gguf voice/*.onnx`):
```text
07de147df64fb7fea5eca2224b453d90eaca68d8036ef53beb564cc6894153a6  laya_w8a16_htp.dlc
60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe  ggml-base.bin
73f9cd370569d13945c0fc9eacf607a4779b3ddc4e1c02ec49eeb93456618add  whisper_base_encoder_10s_fp16_htp.dlc
9b214fcbaca8f40246a99d94792dfee34608f6f53d82d2533dd076032b4d2a0d  whisper_base_encoder_fp16_htp.dlc
9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031  Qwen3-0.6B-Q8_0.gguf
4ba07d8549906668ee855fd9abf9faf66c5db74742712ff026a159f7277fca9f  voice/en_US-lessac-medium.onnx
```
Encoder DLCs you build yourself will have different checksums; that's fine.

### 6. Install and copy the models to the device

```bash
adb uninstall org.seedhecode.incidentassistant      # only if another build is installed
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
ANDROID_SERIAL=<serial from adb devices> scripts/push_models_kit.sh models/*
```
`push_models_kit.sh` copies the files into the app's private storage (`files/models/`) through `run-as`, which is why
this route uses the **debug** build. It prints the folder listing at the end; all five files and `voice/` must be there.
Then open the app as in Option A, step 4 (no unpacking step this time).

### Optional: build the all-in-one APK yourself

With the `models/` folder from step 5:
```bash
scripts/build_full_apk.sh models sentinel-ai-v1.0.apk /path/to/scratch
```
This builds the release APK with every model inside (the same as Option A). It needs about 5 GB free in the scratch
folder and about 8 GB of RAM.

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
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` or "Not enough free storage to unpack the models" | Free about 5 GB on the device. |
| "The on-device models didn't load: Missing model files …" (Option B) | Step 6 didn't copy everything; run `push_models_kit.sh` again and check the listing. |
| Error mentioning SNPE, DSP or `ADSP` | Not an SM8750 device, or the app was built without `-PsnpeSdk`. |
| The mic never hears you (the caption stays empty) | Check that the device's microphone records in another app. Some SM8750 HDK units ship with a broken microphone route that records silence. |
| It answers background noise | Speak close to the device; turns quieter than about −35 dBFS and music/noise transcripts are ignored. |
| No voice | Check the media volume. The top-bar **Voice on/off** toggle must be on. |
