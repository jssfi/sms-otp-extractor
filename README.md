# SMS OTP Extractor

An Android Kotlin prototype for detecting one-time passwords from SMS messages locally, without cloud APIs or network access.

App tries to imitate the similar built-in feature on iOS but on Android.

Planned and debugged by me but built by Codex.

## The app has two different versions:

### 1. LiteRT-LM model bundled in
- In this version the tested LiteRT-LM model is bundled into the app.
- Download size is bigger, but setup is much faster.

### 2. No model bundled in
- The APK size is much smaller, but you need to find and download a suitable LiteRT-LM model yourself.
- The app lets you import any `.litertlm` file through Android's file picker.
- The app tests the imported model locally and warns if it looks slow or unreliable, but still lets you use it.

## What It Does

The app listens for incoming SMS messages, extracts possible OTP candidates, scores them and only asks a small LiteRT-LM model when the result is not clear. The final selected code must exactly match one regex-extracted candidate. The model is not allowed to hallucinate codes.

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
- Codes split with spaces like "1 2 3 4"
- Messages with no obvious code (duh)
- Messages with a link to approve (duh)
- App-specific approval links with no actual code in the SMS
- Long alphanumeric tokens that look like session IDs instead of short OTPs

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

GitHub release uploads are named `sms-otp-<tag>-bundled.apk` and `sms-otp-<tag>-lite-experimental.apk`.

Build both release APKs locally:

```powershell
.\gradlew.bat :app:assembleAllRelease --console=plain
```

If signing environment variables are not present, release APKs are built unsigned.

## Notes

This is a prototype, not production security software. SMS permissions are sensitive and may require special handling for app-store distribution.
I made this app for myself to fix daily problems I have. No guarantees of it working or functioning properly are made. Provided "AS IS"... No future guarantees of updates, only if i find stuff that dont work :D
