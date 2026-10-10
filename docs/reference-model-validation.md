# Two-model update validation

These results cover the previous extraction screen. The subsequent voice
interview and department-form changes have not been built or tested here;
see [voice-interview.md](voice-interview.md) for the Android Studio handoff.

## Completed on the workstation

- Native ARM64 debug APK builds successfully with Whisper and llama runtimes.
- Android lint completes with no errors; existing prototype warnings remain.
- 26 JVM tests pass: 3 WAV, 3 audio endpoint/resampling, 5 existing full-pipeline,
  7 two-model pipeline and 8 Qwen response/export checks.
- 13 Python tests pass: 8 bundle contract and 5 resumable download checks.
- All tests run with assertions enabled. Existing tests were retained.
- The ordinary Gradle/Android Studio build now enables native models by default.
- Real Whisper Base and Qwen3 0.6B Q8_0 weights were downloaded at the pinned
  revisions and verified against their SHA-256 hashes. The two-model ZIP is ready
  at `tmp/reference-models/incident-asr-llm.zip`; ZIP integrity and manifest hashes
  were checked. The weights stay outside git and are not embedded in the APK.

Commands:

```powershell
.\gradlew.bat :core:inference:test :core:audio:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
python -m unittest discover -s scripts -p 'test_*.py' -v
```

JVM results are in each module's `build/test-results` and `build/reports/tests`.
Lint results are in `app/build/reports/lint-results-debug.html`.

## Physical execution still to evaluate

Read-only ADB checks found an attached CPH2835 phone: ARM64, API 36, about 7.4 GiB
total RAM. This update has not replaced its installed app or run model inference
on it. Device attachment/compatibility is not evidence of model accuracy or speed.

Unit tests use injected model responses or parser fixtures. They establish
orchestration and validation behavior, not real Whisper recognition, Qwen field
recall, tool-call quality or live response latency. The pretrained bundle must be
imported and evaluated using [the phone test plan](reference-model-integration.md).
CfC/Laya are disabled in this bundle; no classifier accuracy claim is made.

The earlier full-pipeline/emulator checks remain documented in
[the original validation record](phase1-phase2-validation.md).
