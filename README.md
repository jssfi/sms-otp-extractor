# SMS OTP Extractor

An Android Kotlin prototype for detecting one-time passwords from SMS messages locally, without cloud APIs or network access.

App tries to imitate the built-in functionality on iOS but on Android.

Planned and debugged by me but built by Codex.


## What It Does

The app listens for incoming SMS messages, extracts possible OTP candidates, scores them and only asks a small bundled LiteRT-LM model when the result is not clear. The final selected code must exactly match one regex-extracted candidate. The model is not allowed to hallucinate codes.

### What Works:

- Common English OTPs
- 4-6 digit codes near words like "code", "verification" or "OTP"
- Avoiding some non-codes like references or (some) tracking numbers
- Avoiding sender names with numbers
- Auto-copying detected OTPs
- Fully offline processing
- Fast processing, few ms for heuristic or 1-2 s with LiteRT-LM

### Kinda works
- Non english OTPs
- Multiple candidate OTP messages
- Messages with URLs or other miscellaneous stuff

### Most likely does NOT work

- Alphanumeric OTPs
- Weirdly formatted OTPs
- Non-English messages with bad context
- Codes splitted with spaces like "1 2 3 4"
- Messages with no obvious code (duh)
- Messages with a link to approve (duh)

## Privacy

- No `INTERNET` permission.
- No cloud Gemini/OpenAI/API-key dependency.
- The bundled model runs on-device through LiteRT-LM.

## Bundled Model

This project bundles `SmolLM2-135M-Instruct` converted for LiteRT-LM.

- Model: https://huggingface.co/litert-community/SmolLM2-135M-Instruct
- Base model: https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct
- License: Apache-2.0

## Build

From the repo root:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

Run unit tests:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
```

The debug APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

This is a prototype, not production security software. SMS permissions are sensitive and may require special handling for app-store distribution.
I made this app for myself to fix daily problems I have. No guarantees of it working or functioning properly are made. Provided "AS IS"... No future guarantees of updates, only if i find stuff that dont work :D
