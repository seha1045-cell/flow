# FLOW 8 USB Diagnostic Beta 0.3

Android Galaxy Tab S7 USB-only diagnostic application. Enumerates USB host interfaces, Android USB MIDI devices, and Android USB audio routes. **Read-only**; never sends mixer commands or records audio. Does not use Bluetooth.

## Cloud build (GitHub Actions)
Upload the contents of this `Flow8Control` folder to the root of a GitHub repository, including `.github/workflows/build-apk.yml`. Run Actions > Build Android APK > Run workflow. Download the `Flow8Control-debug-apk` artifact, unzip it, and install `app-debug.apk` on your tablet. Build requires online Gradle/Android dependencies. Build has not been verified in this environment.

## Test
Connect the FLOW 8 via USB, open the app, tap SCAN USB / MIDI / AUDIO, and inspect the device list. Do not assume USB audio implies USB MIDI control support. No mixer settings are changed.
