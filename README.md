# 🔘 Action Button for Android

A sleek, native Android utility that maps physical volume keys to an iPhone 16 Pro-style **Action Button** when the screen is off or on Always-On Display (AOD).

Designed with a modern OLED dark theme, Jetpack Compose, Material 3, and realistic haptic feedback.

---

## ✨ Features

- 🔦 **Flashlight:** Instant torch toggle from your pocket without waking the screen.
- 🔕 **Sound Mode:** Fast switch between Ring, Vibrate, and Silent modes.
- 📷 **Camera:** Secure lockscreen camera launch (`INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE`).
- 🎵 **Music Playback:** Play / Pause active media with hardware key gestures.
- 🎙️ **Quick Voice Recorder:** Direct background audio recording (`.m4a` / AAC) without unlocking or lighting up the display. Haptic pulses confirm start and save.
- 🌙 **Do Not Disturb (DND):** Toggle priority interruption filter.
- ⚙️ **Flexible Gestures:** Choose between Double-Click (Volume Down / Up) or Long-Press.

---

## 🛡️ Security & Privacy First

- **Zero Accessibility Service:** Unlike traditional button remap utilities, this app does **not** register an `AccessibilityService`. It will never trigger security blocks in banking apps (TPBank, Vietcombank, Techcombank, MBBank, etc.).
- **Zero Internet Permission:** Operates entirely offline with zero network connectivity (`android.permission.INTERNET` is not requested).
- **Zero Volume Slider Interference:** Single key presses adjust media volume normally without slider jumps or jitter. The app only triggers on valid double-click or long-press gestures.

---

## 📐 Architecture & Tech Stack

- **UI:** 100% Jetpack Compose + Material 3 with custom Apple Action Carousel Wheel.
- **Service:** Lightweight `ForegroundService` with `SilentAudioLoopPlayer` maintaining the hardware volume key broadcast pipeline when the screen turns off.
- **Detector:** `VolumeKeyDetector` timing state machine distinguishing single click, double click (550ms window), and long press.
- **Target OS:** Android 8.0+ (API 26 to API 35), tested on Android 14 and Android 15 (Realme UI 6.0 / ColorOS).

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug | 2024.2+ or newer
- JDK 17 or JDK 19+
- Android SDK API 34+

### Clone & Build
```bash
git clone https://github.com/<your-username>/action-button-android.git
cd action-button-android
./gradlew assembleDebug
```
Or open the folder directly in Android Studio and click **Run (Shift + F10)**.

---

## ⚙️ Background Setup on OEM ROMs (ColorOS / Realme UI / HyperOS)

To prevent aggressive OEM background killing:
1. **Battery Optimization:** Go to *Settings -> Battery -> App Battery Management -> Action Button* -> Choose **Allow background activity (Don't optimize)**.
2. **Auto-start:** Go to *Settings -> Apps -> Auto-launch* -> Enable **Action Button**.

---

## 📄 License

MIT License. Free to use, modify, and distribute.
