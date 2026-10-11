# Sentinel : an on-device voice incident assistant

Report a workplace incident by talking. The app transcribes speech (Whisper, encoder on the NPU), works out the
incident type (fine-tuned Laya classifier on the NPU), asks only for what is missing, extracts the details (Qwen3-0.6B)
and speaks its questions (Piper). Everything runs on the device; no network is used.

**Supported device: Qualcomm Snapdragon 8 Elite (SM8750) with Android 15**, e.g. the SM8750 HDK / dev kit used at
Megathon. The NPU models are compiled for the SM8750's Hexagon v79 HTP and will not run on other chips.

There are two ways to run it:

- **[Option A: install the ready-made APK](#option-a-install-the-ready-made-apk)**: about 5 minutes; app and all models in one file.
- **[Option B: build from source](#option-b-build-from-source)**: for developers.

---

## Option A: install the ready-made APK

> **Download:** `sentinel-ai-v1.0.apk` from the Google Drive folder
> **https://drive.google.com/drive/folders/1Tx3F5fRod3SHJhxnrQC0kRt-FFjUindL?usp=sharing**
> (1.68 GB, contains the app and every model)
> SHA-256: `c21fb2086531994bd1e714a28cd0548d72074193c1ca35d0229ef5c42b8a3195`

**You need:** the SM8750 device with USB debugging on (Settings → About → tap *Build number* 7 times → Developer
options → *USB debugging*), a computer with `adb` ([Android platform-tools](https://developer.android.com/tools/releases/platform-tools)),
and **about 5 GB free on the device** (the APK plus the 2.1 GB of models it unpacks on first launch).

1. Download `sentinel-ai-v1.0.apk` from the Drive folder above (open the folder, select the file, *Download*; Drive may
   warn that it can't scan a file this large for viruses, so choose *Download anyway*). Optionally check it:
   `sha256sum sentinel-ai-v1.0.apk` must print the SHA-256 above.
2. Connect the device and check it is listed (if several are, add `-s <serial>` after `adb` below):
   ```bash
   adb devices
   ```
3. Remove any other build of this app (for example a teammate's). Android refuses to install over an app signed with
   a different key. "Unknown package" just means it wasn't installed:
   ```bash
   adb uninstall org.seedhecode.incidentassistant
   ```
4. Install. `-g` grants the microphone permission up front:
   ```bash
   adb install -r -g sentinel-ai-v1.0.apk
   ```
5. Open **Sentinel AI** on the device. The **first launch unpacks the models** (about 10–30 seconds). The home screen
   shows *"Preparing on-device models (first launch only)… NN%"*, then *"Loading on-device models…"*. It is ready when
   the badges read **Runs offline on this device · Incident type on NPU · Speech on NPU · Natural voice**. Later
   launches take a few seconds.

Then see [Using the app](#using-the-app). No other downloads are needed for this option.

---

## Option B: build from source

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

The app needs six model files (about 2.1 GB). **The fine-tuned Laya incident classifier is our own model and is
hosted on this repository's GitHub release:**

> **Laya model (required):** [laya_w8a16_htp.dlc](https://github.com/arihant1602/megathon_arihant/releases/download/models-v1/laya_w8a16_htp.dlc)
> (901 MB). All our model files are on the release page: https://github.com/arihant1602/megathon_arihant/releases/tag/models-v1

| File | Size | Download link | Source |
|---|---|---|---|
| `laya_w8a16_htp.dlc` | 901 MB | [download](https://github.com/arihant1602/megathon_arihant/releases/download/models-v1/laya_w8a16_htp.dlc) | ours: fine-tuned Laya, W8A16, compiled for the SM8750 NPU |
| `whisper_base_encoder_fp16_htp.dlc` | 262 MB | [download](https://github.com/arihant1602/megathon_arihant/releases/download/models-v1/whisper_base_encoder_fp16_htp.dlc) | ours: Whisper encoder for the SM8750 NPU (30 s window) |
| `whisper_base_encoder_10s_fp16_htp.dlc` | 90 MB | [download](https://github.com/arihant1602/megathon_arihant/releases/download/models-v1/whisper_base_encoder_10s_fp16_htp.dlc) | ours: Whisper encoder for the SM8750 NPU (10 s window) |
| `ggml-base.bin` | 148 MB | [download](https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.bin) | Whisper base, Hugging Face (pinned revision) |
| `Qwen3-0.6B-Q8_0.gguf` | 639 MB | [download](https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/23749fefcc72300e3a2ad315e1317431b06b590a/Qwen3-0.6B-Q8_0.gguf) | Qwen3-0.6B, Hugging Face (pinned revision) |
| `voice/` | 79 MB | [download (.tar.bz2)](https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2) | Piper en_US lessac-medium, sherpa-onnx release (unpack and rename the folder to `voice`) |

**Option 1: one command (recommended).** Downloads everything into `models/`, verifies every checksum, and resumes if
interrupted (just run it again):
```bash
scripts/download_models.sh models
```

**Option 2: by hand.** From the repository root:
```bash
mkdir -p models && cd models
R=https://github.com/arihant1602/megathon_arihant/releases/download/models-v1
curl -L -O $R/laya_w8a16_htp.dlc
curl -L -O $R/whisper_base_encoder_fp16_htp.dlc
curl -L -O $R/whisper_base_encoder_10s_fp16_htp.dlc
curl -L -O https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.bin
curl -L -O https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/23749fefcc72300e3a2ad315e1317431b06b590a/Qwen3-0.6B-Q8_0.gguf
curl -L https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2 | tar -xj
mv vits-piper-en_US-lessac-medium voice
sha256sum -c - <<'SUMS'
07de147df64fb7fea5eca2224b453d90eaca68d8036ef53beb564cc6894153a6  laya_w8a16_htp.dlc
9b214fcbaca8f40246a99d94792dfee34608f6f53d82d2533dd076032b4d2a0d  whisper_base_encoder_fp16_htp.dlc
73f9cd370569d13945c0fc9eacf607a4779b3ddc4e1c02ec49eeb93456618add  whisper_base_encoder_10s_fp16_htp.dlc
60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe  ggml-base.bin
9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031  Qwen3-0.6B-Q8_0.gguf
4ba07d8549906668ee855fd9abf9faf66c5db74742712ff026a159f7277fca9f  voice/en_US-lessac-medium.onnx
SUMS
cd ..
```
Every line must print `OK`. If you downloaded with a browser instead, move the files into `models/` with exactly these
names.

Either way, you should end up with exactly this layout (names matter; the app looks for these files):
```
models/
├── laya_w8a16_htp.dlc
├── whisper_base_encoder_fp16_htp.dlc
├── whisper_base_encoder_10s_fp16_htp.dlc
├── ggml-base.bin
├── Qwen3-0.6B-Q8_0.gguf
└── voice/
    ├── en_US-lessac-medium.onnx
    ├── en_US-lessac-medium.onnx.json
    ├── tokens.txt
    └── espeak-ng-data/
```
The two encoder files can also be rebuilt from `ggml-base.bin` with `scripts/whisper_npu/build_encoder_dlc.sh` (needs
the QAIRT tools and Python with `torch` and `onnx`). The Laya file can only be downloaded; it comes from our training
and export pipeline.

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
| `INSTALL_FAILED_INSUFFICIENT_STORAGE`, or "Not enough free storage to unpack the models" (Option A) | Free about 5 GB on the device. |
| "The on-device models didn't load: Missing model files …" | Step 6 didn't copy everything; run `push_models_kit.sh` again and check the listing. |
| `push_models_kit.sh` says to install the debug APK first | `run-as` only works with the debug build from step 4; install it, then push again. |
| Error mentioning SNPE, DSP or `ADSP` | Not an SM8750 device, or the app was built without `-PsnpeSdk`. |
| Gradle: "Cannot find a Java installation … languageVersion=17" | `JAVA_HOME` must point at a JDK 17. |
| CMake / NDK version not found | Install them with `sdkmanager` (step 1) or pass `-PndkVersion` / `-PcmakeVersion` for installed ones. |
| The mic never hears you (the caption stays empty) | Check that the device's microphone records in another app. Some SM8750 HDK units ship with a broken microphone route that records silence. |
| It answers background noise | Speak close to the device; turns quieter than about −35 dBFS and music/noise transcripts are ignored. |
| No voice | Check the media volume. The top-bar **Voice on/off** toggle must be on. |

## Optional: build the all-in-one APK yourself

`scripts/build_full_apk.sh models sentinel-ai.apk /path/to/scratch` (with the `models/` folder from Option B, step 5)
builds the same kind of APK as Option A, with every model inside (about 1.7 GB; needs about 5 GB free scratch space and
8 GB of RAM). Install it as in Option A.
