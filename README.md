# rokid-style-translate

Real-time voice translation for the **Rokid AI Glasses** (YodaOS / Android 12).

The app runs standalone on the glasses — no phone or companion app required.
It listens for spoken Spanish or Italian, translates the speech to English (and vice-versa), and speaks the result aloud through the open-ear speakers.
No display is needed: all interaction is audio-only.

---

## Architecture

```
Microphone
    │
    ▼
SpeechManager          (wraps android.speech.SpeechRecognizer, auto-restarts every 45 s)
    │  onFinalResult
    ▼
LanguageDetector       (lexicon-based: detects es / it / en; resolves translation direction)
    │
    ▼
GeminiClient           (HTTP POST → Gemini 2.0 Flash generateContent; temperature 0.3)
    │
    ▼
TtsManager             (wraps android.speech.tts.TextToSpeech, locale-aware)
    │
    ▼
Open-ear speakers
```

The pipeline runs inside **TranslationService**, a `ForegroundService` that stays alive as long as the glasses are on.  A persistent "Translator listening…" notification is shown while active.

---

## Setup

### 1. Get a Gemini API key

1. Go to [Google AI Studio](https://aistudio.google.com/).
2. Sign in and click **Get API key**.
3. Copy the key — it looks like `AIza…`.

### 2. Install the APK

Build with Android Studio (or `./gradlew assembleDebug`) and sideload the APK onto the glasses over ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Enter the API key

On the glasses (or via `adb shell am start`):

1. Launch **Rokid Style Translate**.
2. Tap **Settings**.
3. Paste your Gemini API key.
4. Choose your language pair: **Spanish ↔ English** or **Italian ↔ English**.
5. Tap **Save Settings**.

### 4. Start translating

Tap **Start Listening**.  The service moves to the foreground and the microphone becomes active.  Speak in Spanish (or Italian, depending on your setting) — the English translation will be spoken back.  Speak in English and the foreign-language translation will be spoken back.

The service persists across screen-off and auto-starts on boot once a key has been saved.

---

## Requirements

| Field | Value |
|---|---|
| minSdk | 26 (Android 8.0) |
| targetSdk | 34 |
| compileSdk | 34 |
| Language | Kotlin |
| Async | Kotlin Coroutines |
| HTTP | OkHttp 4.12 |

### Permissions

| Permission | Purpose |
|---|---|
| `RECORD_AUDIO` | Microphone input for speech recognition |
| `INTERNET` | Gemini API calls |
| `FOREGROUND_SERVICE` | Keep service alive on glasses |
| `FOREGROUND_SERVICE_MICROPHONE` | Android 12+ foreground service microphone type |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on device boot |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |

---

## File map

```
app/src/main/java/com/rokid/style/translate/
  MainActivity.kt         – Start/stop toggle + settings link
  TranslationService.kt   – ForegroundService; orchestrates the full pipeline
  SpeechManager.kt        – SpeechRecognizer wrapper with 45 s restart loop
  GeminiClient.kt         – Gemini 2.0 Flash HTTP client (OkHttp)
  TtsManager.kt           – TextToSpeech wrapper (coroutine-friendly)
  LanguageDetector.kt     – Lexicon-based es/it/en detector + direction resolver
  SettingsActivity.kt     – API key + language pair UI
  Prefs.kt                – SharedPreferences helper + LanguagePair enum
  BootReceiver.kt         – Auto-start on boot
```

---

## Privacy

- The Gemini API key is stored in app-private `SharedPreferences` on the device.
- Speech audio is sent to Google's on-device speech recogniser and to the Gemini API.
- No data is stored beyond the current session.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Translation error: Gemini API key is not set" | Open Settings and paste your key |
| Silence after speech | Check that RECORD_AUDIO is granted (`adb shell dumpsys package com.rokid.style.translate \| grep permission`) |
| Service stops after ~1 min | Ensure battery optimisation is disabled for the app on the device |
| Wrong language spoken back | The detector uses stop-word scoring; avoid very short utterances (< 3 words) |
