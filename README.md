# Sup! Mobile - Termux Edition

This is the Android mobile version of the Sup! Social Thread and Object Browser, optimized for building and running directly inside Termux.

## Features

*   **Hybrid Bridge:** Connects a WebView frontend (`SupThread.html` & `index.html`) to a native Java backend (`SupJSInterface`).
*   **Bitcoin SPV Node:** Runs a `bitcoinj` SPV node directly on the device.
*   **External Storage:** Supports storing the blockchain (`.spvchain`) on external USB drives (OTG).
*   **IPFS Integration:** Automatically pins IPFS hashes found in `OP_RETURN` transactions to a local IPFS node.
*   **Smart Search:** Detects IPFS hashes and Transaction IDs in the search bar.

## Prerequisites (Termux)

You need a fully set up Termux environment with Android build tools.

```bash
# 1. Update Termux
pkg update && pkg upgrade

# 2. Install Dependencies
pkg install openjdk-17 gradle android-tools ipfs git

# 3. Setup Android SDK (if not already done)
# Ensure ANDROID_HOME is set. If you don't have the SDK installed,
# you might need to install 'commandlinetools' manually or use a script.
# For this project, we provide a helper to detect standard locations.
```

## How to Build

**Important:** Do not run this from `/storage/downloads`. Move the folder to your Termux home directory first to avoid permission errors.

```bash
cp -r storage/downloads/SupMobile ~/SupMobile
cd ~/SupMobile
```

1.  **Configure Environment:**
    Run the fixer script to configure Gradle to use the system's `aapt2` (crucial for Termux compatibility).

    ```bash
    bash fix_aapt2.sh
    ```

2.  **Build the APK:**
    Use the included wrapper script to build the debug APK.

    ```bash
    bash init_sdk.sh
    ```

    *   This runs `./gradlew clean assembleDebug`.
    *   The output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## How to Run IPFS

The app expects a local IPFS daemon running at `127.0.0.1:5001`.

1.  **Initialize IPFS (if first time):**
    ```bash
    export IPFS_PATH=/storage/0000-0000/Android/media/com.termux/ipfs_data
    ipfs init
    ```
    *(Replace `/storage/0000-0000/...` with your actual external storage path if desired, or just use default `~/.ipfs`)*

2.  **Start Daemon:**
    ```bash
    ipfs daemon --enable-gc
    ```

3.  **Configure App:**
    *   Open the App.
    *   **Grant Permissions:** Click "Grant App Permissions" on the Dashboard.
    *   If using external storage for Bitcoin, set the "Manual Storage Path".
    *   Click "Start Node".

## Permissions

*   **Files:** Use the "Grant App Permissions" button to allow access to external USB drives (required for the blockchain file).

## Troubleshooting

*   **Permission denied `./gradlew`**:
    Run `chmod +x gradlew`. Ensure you are NOT in `/storage/emulated/0/...`. You must be in `~` (home).
*   **AAPT2 errors**:
    Re-run `bash fix_aapt2.sh`. Ensure `pkg install android-tools` was successful.

## Project Structure

*   `app/src/main/assets`: HTML/JS Frontend code.
*   `app/src/main/java`: Java Backend (Bridge, BitcoinJ).
*   `app/build.gradle`: Project dependencies.
