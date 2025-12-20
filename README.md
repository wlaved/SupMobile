# SupMobile

An Android application acting as a decentralized "Sup" node, combining a Social Feed, Object Browser, and Bitcoin SPV Node.

## Features
- **Bitcoin SPV Node**: Runs `bitcoinj` to connect to the P2P network (Testnet/Mainnet).
- **Hybrid Storage**: Connects to a user-selected external folder (USB/SD) to read existing blockchain/IPFS data. Supports standard paths like `/mnt/media_rw/ID/Sup`.
- **Auto-Pinning**: Monitors watched profiles for new content and automatically pins IPFS hashes to the local node.
- **Web Interface**: Uses `SupThread.html` and `index.html` for a rich viewer experience.

## Prerequisites
- **Termux** on Android.
- **IPFS** (running in Termux).
- **Gradle**.

## Setup & Build (Termux)

1.  **Environment Setup**:
    Update packages and install JDK 17 (required for Gradle 8.2) and Android tools.
    ```bash
    pkg update
    pkg install openjdk-17 gradle wget tar
    ```

2.  **Configure Storage for IPFS**:
    Use the path to your external drive (e.g., `5D51-1410`).
    ```bash
    echo 'export IPFS_PATH=/mnt/media_rw/5D51-1410/SUP' >> ~/.bashrc
    source ~/.bashrc
    ```

3.  **Start IPFS Daemon**:
    ```bash
    ipfs init  # (first time only)
    ipfs config Addresses.API /ip4/127.0.0.1/tcp/5001
    ipfs daemon &
    ```

4.  **Build the APK**:
    Make the wrapper executable and build:
    ```bash
    chmod +x gradlew
    ./gradlew assembleDebug
    ```
    *If `gradlew` fails, use the installed gradle:*
    ```bash
    gradle assembleDebug
    ```

5.  **Install**:
    The APK is located at `app/build/outputs/apk/debug/app-debug.apk`.

## First Run & Permissions
1.  **Grant Storage Access**: On Android 11+, the app will ask for "All Files Access". This is required to read/write to specific folders on your USB drive (`/mnt/media_rw/...`) which strict Scoped Storage would otherwise block.
2.  **Select Storage**: Click "Select Storage Folder" and pick your `SUP` folder. Or use the "Manual Path" box if the picker is restricted.
3.  **Start Node**: Click "Start Node" to begin syncing.

## Usage
- **Social Feed**: Browse Sup threads.
- **Object Browser**: View P2FK objects.
- **Node Status**: Check peer count and block height in the Dashboard.
