#!/bin/bash

echo "🚀 SupMobile SDK Setup"
echo "This script links your existing Android SDK to the project."

# Default Termux locations
SDK_DEFAULT_1="$HOME/android-sdk"
SDK_DEFAULT_2="/data/data/com.termux/files/home/android-sdk"

SDK_PATH=""

if [ -d "$SDK_DEFAULT_1" ]; then
    SDK_PATH="$SDK_DEFAULT_1"
elif [ -d "$SDK_DEFAULT_2" ]; then
    SDK_PATH="$SDK_DEFAULT_2"
else
    echo "⚠️  Could not auto-detect SDK."
    read -p "Please enter the full path to your Android SDK: " INPUT_PATH
    if [ -d "$INPUT_PATH" ]; then
        SDK_PATH="$INPUT_PATH"
    else
        echo "❌ Directory not found. Exiting."
        exit 1
    fi
fi

echo "✅ SDK Found at: $SDK_PATH"
echo "sdk.dir=$SDK_PATH" > local.properties
echo "📄 Created local.properties"

echo "🔨 Starting Build..."
# Ensure gradlew has execute permission if it exists
if [ -f "./gradlew" ]; then
    chmod +x gradlew
    ./gradlew assembleDebug
else
    # Fallback to system gradle
    echo "⚠️  gradlew not found, using system gradle..."
    gradle assembleDebug
fi

if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "🎉 SUCCESS! APK built at: app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ Build failed. Check output above."
fi
