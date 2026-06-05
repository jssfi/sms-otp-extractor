This app is to test if your device can reliably (and quickly) extract contents out of an SMS message.
This is also a "is this idea possible?" -type of prototype app before the "real deal".

It bundles `SmolLM2_135M_Instruct.litertlm` and runs it locally through LiteRT-LM on CPU. It does not use a cloud API key or the Internet permission.

Prompt:

```text
Return JSON only:
{"ok": true, "code": "123456"}

SMS: "Your login code is 123456. Ref 20260605"
Candidates: ["123456", "20260605"]
```

The expected result should be:

```json
{"ok": true, "code": "123456"}
```

Codex is used to make this app.
