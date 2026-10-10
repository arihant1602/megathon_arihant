# Starter architecture

The first milestone proves real microphone capture on a normal Android phone.
No LNN or ASR weights are included. The production direction retains the LNN.

The app owns permission handling, screen state and playback. `core:audio` owns
microphone resources, PCM frames and local WAV persistence. `core:inference`
is plain Kotlin with no Android/QNN dependency, so backend contracts can evolve
without coupling the UI to a chipset.

Capture state: idle -> starting -> recording -> saving -> idle. Audio capture
runs on a dedicated worker. UI changes happen on the main thread. Successful
WAVs are finalized and renamed from `.part` before the library discovers them.
Only the capture worker releases the microphone. Playback stops before capture.
On backgrounding or rotation, capture stops and any captured audio is saved;
reopening the screen discovers completed files. There is no foreground service.

## Subsequent milestones

1. Local ASR adapter, incremental captions, model lifecycle and bounded queues.
2. Trained LNN understanding path and local semantic-model adapter.
3. Schema-driven report state, missing-field questions, review and confirmation.
4. Interruptible local TTS, echo cancellation and adaptive turn detection.
5. An Android QNN backend module for HDK8750, compiled model assets and explicit
   memory/latency measurements on the kit.

QNN binaries and model assets must be added deliberately after checking device,
OS/firmware, ABI, SDK compatibility and redistribution terms. A backend will
need its own executor and session/revision cancellation; never do model inference
synchronously inside the microphone callback. `TranscriptUpdate` is a contract
for that milestone and is not emitted by the recording-only implementation.

## Current limits

This starter has no background capture, automatic endpointing, speech recognition,
form filling, LNN, upload, or account system. WAV captures are plaintext in
app-private storage; app backup is disabled. Production retention/encryption,
hardware interruption recovery, audio focus and enterprise access control need
their own implementation and validation.
