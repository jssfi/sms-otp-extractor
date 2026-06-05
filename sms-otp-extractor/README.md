# SMS OTP Extractor

Android Kotlin prototype for extracting OTP codes from live SMS messages without network access.

Pipeline:

```text
SMSReceiver -> CandidateExtractor -> OtpPrefilter -> HeuristicScorer -> AiOtpSelector -> strict validation -> notification/copy
```

The app bundles `SmolLM2_135M_Instruct.litertlm` and uses LiteRT-LM on-device when regex/heuristics are not enough. The model may only choose one existing candidate from regex-extracted candidates.

Build:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

Unit tests:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```
