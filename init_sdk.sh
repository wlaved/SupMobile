#!/bin/bash
set -e

# Check if running in restricted storage
CURRENT_DIR=$(pwd)
if [[ "$CURRENT_DIR" == *"/storage/"* ]] || [[ "$CURRENT_DIR" == *"/sdcard/"* ]]; then
    echo "⚠️  WARNING: You seem to be running this from External Storage."
    echo "    Android often blocks script execution here."
    echo "    If the build fails with 'Permission denied', please move the folder to Termux home:"
    echo "    cp -r . ~/SupMobile && cd ~/SupMobile"
    echo "---------------------------------------------------"
fi

# Auto-detect Android SDK if not set
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/android-sdk" ]; then
        export ANDROID_HOME="$HOME/android-sdk"
        echo "✅ Using Android SDK at $ANDROID_HOME"
    elif [ -d "/data/data/com.termux/files/home/android-sdk" ]; then
        export ANDROID_HOME="/data/data/com.termux/files/home/android-sdk"
        echo "✅ Using Android SDK at $ANDROID_HOME"
    else
        echo "⚠️  ANDROID_HOME not set. Assuming default or system configuration."
    fi
fi

# Create local.properties
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Ensure Gradle Wrapper is executable
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
else
    echo "❌ Error: ./gradlew not found. Please ensure you are in the project root."
    exit 1
fi

# Run build with gradle wrapper
echo "🚀 Starting Gradle build (clean assembleDebug)..."
./gradlew clean assembleDebug
