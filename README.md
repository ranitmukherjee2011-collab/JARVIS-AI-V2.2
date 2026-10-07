# JARVIS V2.2

Android personal assistant prototype with phone actions and **Hey JARVIS** wake mode.

## V2.2 features
- Voice input and text-to-speech
- Contacts, call and SMS actions with confirmation
- Web search and selected app launching
- Calendar and alarm intents
- **Hey JARVIS** microphone foreground service using Android SpeechRecognizer
- Android 14/15 microphone foreground-service declarations
- Version 2.2 / versionCode 4
- GitHub Actions release/debug APK workflow

## Hey JARVIS behavior
The wake service must be enabled from the visible app after microphone permission is granted. It displays a persistent notification while listening. Android 14+ restricts starting microphone foreground services from the background.

This is a prototype wake detector based on SpeechRecognizer, not a dedicated low-power hardware hotword engine.

## Release signing
The GitHub workflow builds a signed release APK when these repository Actions secrets are configured:

- `JARVIS_KEYSTORE_BASE64`
- `JARVIS_KEYSTORE_PASSWORD`
- `JARVIS_KEY_ALIAS`
- `JARVIS_KEY_PASSWORD`

Without those secrets, the workflow intentionally falls back to the debug APK.

### Important Play Protect note
A signed APK distributed directly outside Google Play can still be warned about by Play Protect because it is sideloaded and requests sensitive permissions. For normal distribution, use Google Play Console internal testing/closed testing. Do not disable Play Protect just to install an untrusted APK.

## Current AI limitation
The app still uses a local command engine. A secure AI backend is required for live general-purpose AI answers. Never embed a production AI API key directly in the APK.
