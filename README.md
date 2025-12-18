# SupMobile

An Android application combining a Social Feed ("Sup!") and an Object Browser ("P2FK"), powered by a hybrid Web+Native architecture.

## Requirements
- Android SDK (or Termux with Gradle)
- Java 11+
- Gradle 8.0+

## Building in Termux
1.  **Install Gradle**:
    ```bash
    pkg install gradle openjdk-17
    ```
2.  **Build the APK**:
    ```bash
    gradle build
    ```
3.  **Install**:
    The resulting APK will be in `app/build/outputs/apk/debug/app-debug.apk`.

## Features
- **Hybrid Architecture**: Uses `WebView` to render the rich UI (`SupThread.html`) while keeping native performance for crypto and file I/O.
- **Bitcoin Integration**: Uses `bitcoinj` (via Gradle) to handle wallet keys and transaction construction (OP_RETURN).
- **File Access**: Exposes a bridge (`SupApp.listFiles`) to allow the web interface to browse local storage.
