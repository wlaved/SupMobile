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

## Setup & Build
1.  **Install Dependencies in Termux**:
    ```bash
    pkg install gradle openjdk-17 ipfs
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
4.  **Build the App**:
    ```bash
    gradle build
    ```
5.  **Install**:
    The APK is located at `app/build/outputs/apk/debug/app-debug.apk`.

## Usage
1.  Open **SupMobile**.
2.  Click **Select Storage Folder** and navigate to your `SUP` folder on the USB drive.
3.  Click **Start Node** to begin syncing headers (`.spvchain`).
4.  Use **Switch Network** to toggle between Testnet and Mainnet.
