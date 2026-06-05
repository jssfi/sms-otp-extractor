This if to test if my (or your) device has AICore (most phones dont?) and if its exposed to third party apps.

Simple Android app (must sideload the apk) that prompts on command the (hopefully) exposed endpoint of AICore for Gemini Nano 4 Fast on-device.

Prompt:
"Return JSON only:
{"ok": true, "code": "123456"}

SMS: "Your login code is 123456. Ref 20260605"
Candidates: ["123456", "20260605"]"

from which the expected result should be:
"{"ok": true, "code": "123456"}"

Reasons why this would fail could be;
- AICore unavailable
- Model unavailable
- Unsupported device
- Model not ready (not downloaded?)

Codex is used to make this app.
