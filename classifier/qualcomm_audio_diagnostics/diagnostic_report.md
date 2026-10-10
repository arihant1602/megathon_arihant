# Qualcomm Kit Microphone Diagnostic Report

> **UPDATE (2026-10-10): FIXED.** With `adb root`, `sentinel_mictest/kitfix/apply.sh` bind-mounts a patched `mixer_paths_sun_qrd.xml` and restarts the audio HAL. The `amic1` (MIC1) and `amic5` (MIC4) record paths now use **DEC0**, and MIC4 also uses the loopback's SWR_MIC5 / SWRM_TX2_CH2 wiring. The DMA channel mask becomes `0x1` (it was `0x5`/`0x9`), and the ADSP graph starts. **This is not persistent: run it again after every reboot** (`apply.sh revert` undoes it).
>
> | Capture (speech) | Graph failures | RMS | Peak | Exact zeros |
> |---|---|---|---|---|
> | MIC4 / back, MIC source, 10 s | 0 | -21.7 dBFS | 25781 | 0.44% |
> | MIC1 / bottom, MIC source, 10 s | 0 | -31.3 dBFS | 13401 | 0.8% |
> | MIC4 / back, VOICE_RECOGNITION, 3 s | 0 | -9.6 dBFS | 32768 (clipping) | — |
> | MIC1 / bottom, VOICE_RECOGNITION, 3 s | 0 | -28.1 dBFS | 7399 | — |
>
> `microphone_test.wav` is the MIC4 capture (`fix_bottom_MIC.wav` is MIC1). **Final classification: CONFIRMED WORKING.** The user played back `microphone_test.wav` (MIC4) and heard their own voice. For Sentinel, use the bottom mic with VOICE_RECOGNITION, or the back mic with the MIC source; back mic + VOICE_RECOGNITION clips. The original findings below describe the state before the fix.

**Date:** 2026-10-10 · **Device:** Snapdragon SM8750P HDK ("sun", USB `05c6:901d` SUNP-HDK SN B755977A) · Android 15 (API 35), userdebug build `AQ3A.240801.002`

## Final classification: **DETECTED BUT NOT VERIFIED**

The microphone hardware exists and at least one mic (AMIC5, the "MIC4" in QMMI) picks up sound. QMMI's MIC4 loopback caused acoustic feedback. But **no standard Android capture path delivers any audio**. Every `AudioRecord` capture returns 100% digital zeros because the ADSP refuses to start the capture graph. Speech capture has not been shown.

## Answers

| # | Question | Answer | Evidence |
|---|---|---|---|
| 1 | Detected by Linux? | **Yes** | `lsusb`: `05c6:901d Qualcomm, Inc. SUNP-HDK`, 10 Gbps, two vendor-specific interfaces (ADB + usbfs). |
| 2 | ADB accessible? | **Yes, authorized** | `adb devices -l` → `fe7980db device product:sun model:Sun_for_arm64`. |
| 3 | Android declares a mic? | **Yes, two** | `getMicrophones()`: `built_in_mic` (bottom, id 22) and `back_mic` (back, id 24), both MAINBODY, -37 dBFS sensitivity. Also listed in `media.audio_policy` and in the vendor audio policy XML. |
| 4 | Evidence of a physical mic? | **Yes, for AMIC5 (QMMI MIC4)** | QMMI tc230 "MIC4 to SpkrRight AFE loopback" caused loud acoustic feedback when you spoke near the kit. Sound reached the mic and came out of the speaker. This is your observation, not a recording. MIC1 (AMIC1) and MIC2 (AMIC3) loopbacks were silent. |
| 5 | Captures actual audio (to a file / app)? | **No** | 12 on-device probes plus 6 host-analysed WAVs: valid PCM containers, peak 0, 100% exact-zero samples. |
| 6 | Can speech be recorded clearly? | **Not demonstrated** | Capture fails before any mic sample reaches the app. |
| 7 | MIC4 reachable through standard Android APIs? | **Selectable, but not working** | Selecting `back_mic` routes to `PAL_DEVICE_IN_SPEAKER_MIC` → `speaker-mic` → `amic5`, the same codec input as QMMI MIC4. The ADSP graph start still fails. |
| 8 | Usable for an on-device voice assistant? | **Not in the current state** | Every Android route (MIC, VOICE_RECOGNITION, UNPROCESSED, CAMCORDER, VOICE_COMMUNICATION at 16 kHz and 48 kHz, on bottom and back) returns zeros. A Sentinel app would hear only silence, with no error raised. |
| 9 | What's needed? | See "Recommended next steps" | Fix the board audio config (vendor/BSP), or use an external USB / Bluetooth mic. |

## What was tested

### Android input topology

| Android input | PAL device | Mixer path | Codec input / SoundWire routing | QMMI name |
|---|---|---|---|---|
| `built_in_mic` (bottom, default) | HANDSET_MIC | `handset-mic` | AMIC1 · DEC2 · SWR_MIC4 · SWRM_TX2_CH1 | MIC1 |
| `back_mic` (back) | SPEAKER_MIC | `speaker-mic` | AMIC5 · DEC3 · SWR_MIC9 · **SWRM_TX3_CH2** | MIC4 (record, tc104) |
| headset (not attached) | WIRED_HEADSET | `headset-mic` | AMIC2 | Headset MIC |
| — | — | — | AMIC3, AMIC4 | MIC2, MIC3 (not exposed to Android) |

Sound card: `sun-qrd-snd-card`, which uses `mixer_paths_sun_qrd.xml` and `resourcemanager_sun_qrd.xml`. The HDK runs the **QRD** audio configuration.

### Capture results (AudioRecord, PCM16 mono)

| Input | Source | Rate | Routed device (from AudioRecord) | Peak | Exact-zero samples |
|---|---|---|---|---|---|
| bottom | MIC / VOICE_RECOGNITION / UNPROCESSED / CAMCORDER | 16k | 22 BUILTIN_MIC bottom | 0 | 100% |
| back | MIC / VOICE_RECOGNITION / UNPROCESSED / CAMCORDER | 16k | 24 BUILTIN_MIC back | 0 | 100% |
| bottom | MIC, VOICE_COMMUNICATION | 48k, 16k | bottom | 0 | 100% |
| back | MIC, UNPROCESSED, VOICE_COMMUNICATION | 48k, 16k | back | 0 | 100% |

Software mute was ruled out. The sensor-privacy mic toggle is off, all AudioService mic-mute flags are false, and the app-op RECORD_AUDIO is `allow`.

### Root cause (from the HAL logs)

Each capture attempt in the HAL fails like this:

```
AGM: graph: print_graph_alias  GKV Alias 25 | DeviceTX_Speaker_Mic_StreamTX_RAW_Record
AGM: graph_open ... exit, ret 0          # open OK
AGM: graph_prepare ... exit, ret 0        # prepare OK
gsl: gsl_graph_start: Graph start failed:1
AGM: graph_start failed -131
PAL: SessionAlsaPcm: pcm_start failed -131  (CODEC_DMA-LPAIF_RXTX-TX-3)
AHAL_StreamIn_QTI: pal_stream_start failed!! ret:-131
```

The HAL retries about 70 times per 3-second capture and gives AudioFlinger zero-filled buffers meanwhile. A 3 s capture took 6.5–7.7 s of wall time. Each attempt also logs repeated `ACDB … No calibration found`. Even the minimal RAW record graph fails, so the effects modules (Fluence/ECNS) aren't the problem. The failure happens when the DSP starts the codec-DMA TX-3 hardware endpoint.

**Why QMMI MIC4 loopback works but recording doesn't.** Both use AMIC5, with different SoundWire routing:

| Path | Decimator | SWR mux | ADC4 port map | Result |
|---|---|---|---|---|
| QMMI tc230 MIC4→speaker loopback | DEC0 | SWR_MIC5 | **SWRM_TX2_CH2** | Audio flows (feedback) |
| QMMI tc104 MIC4 record **and** Android `speaker-mic` | DEC3 | SWR_MIC9 | **SWRM_TX3_CH2** | Graph start fails / "Testing" never completes |

This matches the QMMI symptoms. `AUDIO_MIC4_RECORD` hangs at "Testing" because it uses the same failing record route as Android. The loopback works because it uses a different SoundWire port.

**Likely cause (not confirmed):** the HDK runs the QRD audio configuration, whose record paths and ACDB calibration don't match this board's codec/SoundWire wiring. The bottom mic (AMIC1, on TX2) also fails, so the SoundWire port can't be the whole story. A vendor/BSP audio config mismatch is the most likely explanation. Confirming it needs root-level `tinymix`/`agmcap` experiments or Qualcomm support.

The QMMI "Headset MIC PASS" with no headset attached is a false pass. Android exposes no headset input, so that result means nothing.

## Recommended next steps

1. **Definitive physical-mic recording test (needs your approval).** This is a userdebug build. `adb root` restarts adbd as root until the next reboot, without flashing or modifying any partition. With root, `tinymix` + `agmcap` can record from AMIC5 using the *working* loopback routing (DEC0 / SWR_MIC5 / SWRM_TX2_CH2). That would prove whether the mic captures speech.
2. **For Sentinel right now:** use a **USB-C audio input** (a USB mic or USB-C headset). The vendor policy declares `USB Device In` and `USB Headset In`, which use the generic USB audio HAL instead of the failing codec-DMA path. A Bluetooth SCO headset is a second option. In the app, select the input with `AudioRecord.setPreferredDevice()`.
3. **Detect silent failure in the Sentinel app.** If the first ~500 ms of capture has peak == 0, treat the mic as unavailable instead of "no speech".
4. **Ask Qualcomm/the kit vendor** for the HDK-specific audio config (mixer_paths / resourcemanager / ACDB for the SM8750 HDK, not QRD). Include `logs/hal_graph_start_failure_back_mic.txt`.

## Artifacts

| File | Contents |
|---|---|
| `adb_devices.txt` | `adb devices -l` and the `lsusb` entry for the kit |
| `device_info.txt` | Build and SoC properties |
| `audio_devices.txt` | Android input devices, microphone metadata, the routing map, and host capture devices (the laptop's own mic, not the kit) |
| `microphone_analysis.json` | Host-side WAV analysis and on-device per-probe metrics |
| `failed_captures/*.wav` | Real but all-zero captures, kept as evidence of the failure. **These are not mic recordings.** |
| `logs/` | Filtered HAL/PAL/AGM logs for each capture attempt |
| `microphone_test.wav` | **Not created.** No successful speech recording exists. |

Test tool: `../sentinel_mictest/`, a Java AudioRecord app built without Gradle (`build.sh`, `run_test.sh`, `analyze_wav.py`). It's installed on the kit as `com.sentinel.mictest`; remove it with `adb uninstall com.sentinel.mictest`.
