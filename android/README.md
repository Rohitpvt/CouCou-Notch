# Cocoa Android Companion App

Native Android companion app for **Cocoa** (CouCou Notch), bringing the full real-time agent monitoring, live Mochi animations, lock-screen approvals, and microservices management to Android devices.

Designed to match the iOS companion experience (`CocoaPhone`) using modern Android standards: **Kotlin 2.1**, **Jetpack Compose**, **Material Design 3**, and **Ktor WebSocket Client**.

---

## 🌟 Key Features

1. **🐱 Live Mochi Avatar**:
   - Breathing, pulsing, and mood-reactive animations (Idle, Thinking, Coding, Warning, Happy, Sleeping).
   - Visual step-by-step progress tracking for all agent actions (`READ`, `EDIT`, `BASH`, `SEARCH`, `IMAGE`).

2. **🔔 Dynamic Status Bar & Lock Screen Activity**:
   - Uses an Android Foreground Service to display ongoing agent tasks in the notification drawer.
   - Quick action buttons directly on the lock screen (`Allow`, `Deny`, `Pause`).

3. **🛡️ Biometric Approvals**:
   - Approve sensitive terminal commands and file edits using fingerprint or Face Unlock via `BiometricPrompt`.

4. **⚡ Remote Prompt Dispatcher**:
   - Send new instructions to your desktop workspace directly from your phone.
   - Select models (*Claude 3.7 Sonnet Thinking*, *GPT-4.5*, *Gemini 2.0 Flash*, *DeepSeek R1*) and adjust thinking token budgets.

5. **💬 Clarification Questions & Feedback**:
   - Answer interactive questions from the agent with multi-select chips or freeform custom responses.

6. **🛠️ Local Services Dashboard**:
   - Monitor dev servers (Vite, Rust backend, Docker, DBs) running on your PC with live CPU/RAM metrics and restart buttons.

7. **📡 Wi-Fi Zero-Config Discovery & QR Pairing**:
   - Auto-discovers Cocoa instances running on your local network using mDNS (`_cocoa-companion._tcp.`).
   - Deep-link support (`cocoa://pair?host=...&port=...&token=...`) for instant QR code scanning.

---

## 🏗️ Architecture & Tech Stack

| Component | Technology |
| :--- | :--- |
| **Language** | Kotlin 2.1.0 |
| **UI Framework** | Jetpack Compose (BOM 2025.01.00) + Material 3 |
| **Networking** | Ktor Client (CIO) with WebSockets & Kotlinx Serialization |
| **Local Discovery** | Android `NsdManager` (mDNS / DNS-SD) |
| **Biometrics** | `androidx.biometric:biometric:1.2.0-alpha05` |
| **Minimum SDK** | Android 8.0 (API Level 26) |
| **Target SDK** | Android 15 (API Level 35) |

---

## 🚀 How to Build and Install on Android

### Option A: Using Android Studio
1. Open **Android Studio** (Hedgehog, Iguana, Ladybug, or newer).
2. Select **Open** and choose the `android/` directory inside this repository.
3. Allow Gradle to sync dependencies automatically.
4. Connect your Android phone via USB (with **USB Debugging** enabled in Developer Options) or start an Android Emulator.
5. Click the green **Run** ▶️ button (or press `Shift + F10`).

### Option B: Using Command Line (Gradle)
```bash
# Navigate to the android directory
cd android

# Build debug APK
./gradlew assembleDebug   # (or gradlew.bat assembleDebug on Windows)

# The APK will be generated at:
# android/app/build/outputs/apk/debug/app-debug.apk

# Install directly to a connected USB device:
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔗 How to Connect to Cocoa Desktop

1. Ensure your Android device and PC are on the **same Wi-Fi network**.
2. Launch Cocoa on Windows / Mac.
3. Open the Cocoa Companion App on your Android phone.
4. The app will automatically search and display your PC under **Discovered on Wi-Fi (mDNS)**.
5. Tap **Connect** (or manually enter your PC's local IP address e.g. `192.168.1.100` and port `47910`).
