# SwipeX: Cross-Platform Remote Touchpad System

SwipeX transforms an Android phone into a high-precision, wireless Windows touchpad. The system consists of an Android application and a lightweight Windows desktop tray server that communicate completely offline over a local network.

---

## 📦 Download & Release Artifacts

Both built packages are compiled and available in the `Release` folder:
* **Android Application**: [SwipeX.apk](file:///D:/projects/swipex/Release/SwipeX.apk) (approx. 21.5 MB)
* **Windows Desktop App**: [SwipeX.exe](file:///D:/projects/swipex/Release/SwipeX.exe) (approx. 30.2 MB)

No additional Python runtimes, Visual Studio components, or external archives are required. The executable and APK are completely standalone.

---

## 🛠️ Installation Guide

### Android App
1. Transfer the [SwipeX.apk](file:///D:/projects/swipex/Release/SwipeX.apk) to your Android device.
2. Tap the APK file to install it.
3. If prompted by Android security, enable **"Allow installation from unknown sources"** for your file manager.
4. Open SwipeX from your app drawer.

### Windows App
1. Copy [SwipeX.exe](file:///D:/projects/swipex/Release/SwipeX.exe) to your computer.
2. Double-click to run it.
3. The server starts instantly, running silently in the background. A mouse icon will appear in your **Windows System Tray** (bottom right of the taskbar, near the clock).

---

## 🔗 Pairing & Connection Guide

### Step 1: Open the Pairing Window
* On your Windows computer, locate the SwipeX icon in the system tray.
* **Double-click** the tray icon (or right-click and select **"Open SwipeX"**).
* A dashboard window will open, displaying the connection status and a **QR Code**.

### Step 2: Scan QR from the Phone
* Open the SwipeX app on your Android phone.
* Open the **Settings** menu by tapping the gear icon on the top right.
* Tap the blue **"Pair New Computer"** button. The camera scanner will slide over (delegated safely via Google Play Services without requiring manual camera permission configuration!).
* Point your camera at the QR code displayed on the Windows app.
* The phone will scan the QR code and connect within **1 second**. The connection status indicator on both apps will turn **Green (Connected)**.

### Step 3: Auto-Discovery & Auto-Connection (Hands-Free)
* Once paired, SwipeX stores the connection details.
* The next time you open the app on the same local network (Wi-Fi, Ethernet, or mobile hotspot), it will reconnect automatically.
* If your computer's IP address changes, the Windows server broadcasts its identity over UDP port `18889`. The phone automatically listens, captures the new IP address, and reconnects silently.

---

## 🖐️ Gesture Reference

SwipeX mimics a physical laptop trackpad with highly-responsive, low-latency, and smooth cursor movement:

| Gesture | Action | Description |
| :--- | :--- | :--- |
| **One Finger Move** | **Move Cursor** | Smoothly moves the cursor. Uses relative deltas for a natural feel. |
| **One Finger Tap** | **Left Click** | Quickly tap the touchpad area once to trigger a standard left mouse click. |
| **One Finger Hold** | **Left Click Hold (Drag)** | Hold a finger down on the pad for 400ms. A short vibration triggers, letting you drag-and-drop files or select text. |
| **Two Finger Scroll** | **Vertical Scroll** | Move two fingers up/down on the pad to scroll vertically in browsers and apps. |
| **Bottom Left Button** | **Left Click** | Press to select, click, or hold down (useful for dragging with a second finger). |
| **Bottom Right Button** | **Right Click** | Press to trigger standard Windows context menus. |

---

## 🔧 Developer Settings (Android App)

Tap the gear icon on the top right to access configuration sliders:
* **Sensitivity**: Multiplies touch motion deltas to adjust responsiveness.
* **Cursor Speed**: Adjusts overall cursor velocity.
* **Scroll Speed**: Controls the multiplier for vertical two-finger scrolling.
* **Dark Mode**: Switch between pure black OLED theme and slate theme.
* **Auto Connect**: Enable/disable automatic connection to the last paired PC.

---

## ⚙️ Technical Architecture & Build Guide

### Technical Specifications
* **Communication Protocol**: Lightweight, low-overhead string messages (`m,dx,dy`, `c,button,action`, `s,dy`) transmitted over **asynchronous WebSockets** for under 10ms latency.
* **Mouse Simulation**: Simulates Windows events using the native Win32 `SendInput` API, avoiding cursor jumps or jumps during bounds limits.
* **Smoothing**: Implements the **One Euro Filter** (adaptive low-pass filter) to dynamically smooth tracking noise at rest (high filtering) and eliminate lag during rapid movement (low filtering).
* **Sub-Pixel Precision**: Float coordinates are accumulated in a fractional remainder buffer before converting to integer mouse steps, preserving micro-adjustments.

### Building from Source

#### Android App (Gradle)
Requires JDK 21 and Android SDK Platform 34/36.
1. Open the directory `D:\projects\swipex\swipex android app` inside Android Studio.
2. Open terminal and run:
   ```powershell
   .\gradlew.bat assembleRelease
   ```
3. The release APK will be generated at `app/build/outputs/apk/release/app-release-unsigned.apk`.

#### Windows App (Python)
Requires Python 3.11 with `pyinstaller`, `customtkinter`, `qrcode`, and `pywin32`.
1. Open a command prompt inside `SwipeX Desktop`.
2. Run the build script:
   ```cmd
   python build_exe.py
   ```
3. PyInstaller packages the app, dynamic libraries, and assets into a single-file executable `dist/SwipeX.exe`.
