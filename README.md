# SupMobile

An Android application acting as a decentralized "Sup" node, combining a Social Feed, Object Browser, and Bitcoin SPV Node.

## Features
- **Bitcoin SPV Node**: Runs `bitcoinj` to connect to the P2P network (Testnet) and download headers.
- **Hybrid Storage**: Connects to a user-selected external folder (USB/SD) to read existing blockchain/IPFS data.
- **Auto-Pinning**: Monitors watched profiles for new content and automatically pins IPFS hashes to the local node.
- **Web Interface**: Uses `SupThread.html` and `index.html` for a rich viewer experience.

## Prerequisites
- **Termux** on Android.
- **IPFS** (running in Termux).
- **Gradle**.

## Setup & Build
1.  **Install Dependencies in Termux**:
    ```bash
    pkg install gradle openjdk-17 ipfs
    ```
2.  **Start IPFS Daemon**:
    ```bash
    ipfs init  # (first time only)
    ipfs config Addresses.API /ip4/127.0.0.1/tcp/5001
    ipfs daemon &
    ```
3.  **Build the App**:
    ```bash
    gradle build
    ```
4.  **Install**:
    The APK is located at `app/build/outputs/apk/debug/app-debug.apk`.

## Usage
1.  Open **SupMobile**.
2.  Click **Select Storage Folder** and choose your "Sup" data folder (USB/SD).
3.  The node will start syncing headers (`.spvchain`).
4.  As it syncs, it will watch your profiles and command your local IPFS node to pin new content.
