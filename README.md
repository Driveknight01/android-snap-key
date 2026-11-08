# Action Button for Android

An Android application that maps hardware volume button gestures (double-press and long-press) to configurable quick actions when the screen is off.

Built with Kotlin, Jetpack Compose, and Material 3.

---

## Overview

Most button-remapping tools on Android rely on an `AccessibilityService`. However, several banking and security-sensitive applications scan for active accessibility services and restrict access when detected.

This project uses an alternative approach:
- A foreground service maintains a low-priority silent audio session when the screen is turned off.
- Hardware volume button events (`android.media.VOLUME_CHANGED_ACTION`) are captured and processed by a timing state machine.
- Single presses pass through as normal volume adjustments, while double-presses and long-presses trigger the configured action.

The app requires no Accessibility permissions and does not declare `INTERNET` access.

---

## Supported Actions

- **Flashlight:** Toggles the camera flash via `CameraManager`.
- **Ringer Mode:** Cycles through Normal, Vibrate, and Silent modes via `AudioManager`.
- **Camera:** Launches the secure lockscreen camera intent (`MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE`).
- **Media Play/Pause:** Dispatches a `KEYCODE_MEDIA_PLAY_PAUSE` key event to the active media player.
- **Voice Recorder:** Records audio directly to `.m4a` using `MediaRecorder` in the app's private external storage.
- **Do Not Disturb:** Toggles priority interruption filter (`ACCESS_NOTIFICATION_POLICY` required).

---

## Gesture Detection

The gesture detector (`VolumeKeyDetector`) processes volume delta events with the following parameters:

- **Double-click window:** 550 ms
- **Debounce threshold:** 70 ms (filters hardware bounce and duplicate broadcasts)
- **Cooldown period:** 650 ms (prevents re-triggering during action execution or volume restoration)
- **Volume restoration:** When an action is triggered, the system volume is restored to its pre-gesture level silently.

Available triggers:
- Double-press Volume Down
- Double-press Volume Up
- Long-press Volume Down
- Long-press Volume Up

---

## Permissions

| Permission | Purpose |
|---|---|
| `FOREGROUND_SERVICE` | Keeps the background detection service running. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required for silent playback on Android 14+. |
| `FOREGROUND_SERVICE_MICROPHONE` | Required for background voice recording on Android 14+. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Declared for hardware key detection while the screen is off. |
| `CAMERA` | Controls the camera torch for the flashlight action. |
| `RECORD_AUDIO` | Required for the voice recorder action. |
| `VIBRATE` | Provides haptic confirmation when a gesture triggers. |
| `ACCESS_NOTIFICATION_POLICY` | Required to change Do Not Disturb states. |
| `WAKE_LOCK` | Acquires brief wake locks to process events and toggles. |

The app does not declare `android.permission.INTERNET`.

---

## Building

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or higher
- Android SDK with API 34 installed

### Build from CLI
```bash
# Clone the repository
git clone https://github.com/<your-username>/action-button-android.git
cd action-button-android

# Build debug APK
./gradlew assembleDebug
```
The output APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Device-Specific Configuration

Some OEM skins (such as ColorOS, Realme UI, HyperOS, and MIUI) enforce aggressive background restrictions. If the service is stopped by the system after the screen has been off for an extended period:

1. **Battery Optimization:** In system settings, find *Action Button* under battery management and set background activity to **Unrestricted** (or *Allow background activity*).
2. **Auto-Start:** Enable *Auto-start / Auto-launch* for Action Button in the system settings.

---

## License

This project is licensed under the [MIT License](LICENSE).
