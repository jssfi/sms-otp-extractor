# SMS OTP Extractor

An Android Kotlin prototype for detecting one-time passwords from SMS messages locally, without cloud APIs or network access. The app brings the similar built-in feature on iOS to Android. Combined with Link to Windows/Galaxy Connect, this app auto-copies the clipboard straight to your PC so there is no need to find your phone or take it out of your pocket.

I made this app cause i was mad at Google's implementation where I need to physically open my phone if i was using my PC or click "copy" on the notification when a 2FA code shows up. This bypasses that and immediately copies it to the clipboard, which can be synced using Galaxy Connect to your PC, for example.

Planned and debugged by me but built by Codex.

## Platform

- Android app
- Minimum supported version: Android 8.0 / API 26 (Tested working on Android 16; Samsung S25 Ultra and in emulators)
- No backend or account needed

## Install

Download one of the APKs from the latest GitHub Release:

- `sms-otp-<tag>-bundled.apk` includes the LiteRT-LM model.
- `sms-otp-<tag>-lite.apk` is the small heuristics-only build. (I recommend using this version for the best compatibility)

Releasing this to the Play Store is unfeasible since the app needs SMS permissions to work.

To install:

1. Download the APK on your Android device.
2. Open it from your downloads/files app.
3. Allow installing apps from that source if Android asks.
4. Open SMS OTP Extractor and grant the SMS permission during onboarding.
5. And you're now set up! Try logging in to something that has 2FA through SMS.

If Android blocks the install, make sure you downloaded the APK from the GitHub Release assets and not a source file or build log.

## Screenshots

Demo video: [README_Contents/20260606-1932-26.5062138.mp4](README_Contents/20260606-1932-26.5062138.mp4)

| Onboarding | Demo | Copied |
| --- | --- | --- |
| <img src="README_Contents/Screenshot%202026-06-06%20221659.png" width="220" alt="Onboarding screen explaining SMS access"> | <img src="README_Contents/Screenshot%202026-06-06%20221708.png" width="220" alt="Demo animation showing a new SMS being scanned"> | <img src="README_Contents/Screenshot%202026-06-06%20221717.png" width="220" alt="Demo animation showing code copied to clipboard"> |

| Main screen | Recent activity |
| --- | --- |
| <img src="README_Contents/Screenshot%202026-06-06%20221725.png" width="220" alt="Main app screen in the heuristics-only build"> | <img src="README_Contents/Screenshot%202026-06-06%20222020.png" width="220" alt="Recent activity showing recognized and skipped SMS messages"> |

## The app has two different versions:

### 1. LiteRT-LM model bundled in
- In this version the tested LiteRT-LM model is bundled into the app.
- Download size is bigger, but setup is much faster.
- Model should work out some of the problems heuristics only can't, but is experimental.

### 2. Lite / heuristics only
- No LiteRT-LM model is bundled or imported.
- Ambiguous messages are handled by local rules only.
- It is simpler, faster and more power efficient, but less flexible than the bundled AI version.
- It has the highest chance of getting tricky codes wrong.

## What It Does

The app listens for incoming SMS messages, extracts possible OTP candidates, scores them and only asks a small LiteRT-LM model when the result is not clear. In the lite build, that model step is disabled. The final selected code must exactly match one regex-extracted candidate. The model is not allowed to hallucinate codes.

### What Works:

- Common English OTPs
- Common sign-in, 2FA, MFA, password reset and phone verification SMS templates
- 4-6 digit codes near words like "code", "verification", "OTP", "PIN" or "passcode"
- Some transaction/card OTP messages, like "your OTP for purchase ... is 123456"
- A focused set of Finnish, Swedish, Spanish, Italian, German and French OTP wording
- Accented keyword variants like "código" / "verificación"
- Avoiding many non-codes like dates, receipts, balances, invoices, references, coupons, appointments, tracking numbers and URL/query IDs
- Avoiding sender names with numbers
- Avoiding most codes that only appear inside links or URL query parameters
- Auto-copying detected OTPs
- Fully offline processing
- Fast processing, usually a few ms for heuristic-only matches or 1-2 s with LiteRT-LM

### Kinda works

- Non-English OTPs outside the focused keyword set
- Multiple candidate OTP messages
- Messages with URLs or other miscellaneous stuff
- Alphanumeric OTPs
- Payment/transaction OTPs with a lot of extra card, amount or merchant context

### Most likely does NOT work

- Weirdly formatted OTPs
- Non-English messages with bad context
- Codes split with unusual separators like "1.2.3.4"
- Messages with no obvious code (duh)
- App-specific approval links with no actual code in the SMS
- Long alphanumeric tokens that look like session IDs instead of short OTPs

## Permissions

- `RECEIVE_SMS`: needed so the app can detect incoming SMS messages automatically.
- No `INTERNET` permission: the app does not call any cloud API or send messages off-device.

## Privacy

- No `INTERNET` permission.
- No cloud dependency.
- The model runs on-device through LiteRT-LM.

## Tested Model

The bundled release includes `SmolLM2-135M-Instruct` converted for LiteRT-LM.

- Model: https://huggingface.co/litert-community/SmolLM2-135M-Instruct
- Base model: https://huggingface.co/HuggingFaceTB/SmolLM2-135M-Instruct
- License: Apache-2.0

## Build

From the repo root:

```powershell
.\gradlew.bat :app:assembleAllDebug --console=plain
```

Run unit tests:

```powershell
.\gradlew.bat testLiteDebugUnitTest --console=plain
```

The debug APKs will be generated at:

```text
app/build/outputs/apk/bundled/debug/app-bundled-debug.apk
app/build/outputs/apk/lite/debug/app-lite-debug.apk
```

GitHub release uploads are named `sms-otp-<tag>-bundled.apk` and `sms-otp-<tag>-lite.apk`.

Build all release APKs locally:

```powershell
.\gradlew.bat :app:assembleAllRelease --console=plain
```

If signing environment variables are not present, release APKs are built unsigned.

## Notes

This is a prototype, not production security software. SMS permissions are sensitive and may require special handling for app-store distribution.
I made this app for myself to fix daily problems I have. No guarantees of it working or functioning properly are made. Provided "AS IS"... No future guarantees of updates, only if i find stuff that dont work :D
