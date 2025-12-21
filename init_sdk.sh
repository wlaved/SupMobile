#!/bin/bash
set -e

# Auto-detect Android SDK if not set
if [ -z "$ANDROID_HOME" ]; then
    # In this sandbox, I will assume a mock location if not found,
    # OR rely on standard locations.
    # Since I cannot install the SDK, I will check if it exists in the home dir.
    if [ -d "$HOME/android-sdk" ]; then
        export ANDROID_HOME="$HOME/android-sdk"
        echo "Using Android SDK at $ANDROID_HOME"
    elif [ -d "/usr/lib/android-sdk" ]; then
        export ANDROID_HOME="/usr/lib/android-sdk"
        echo "Using Android SDK at $ANDROID_HOME"
    else
        # Fallback to create a fake one just to satisfy gradle configuration if possible?
        # No, that won't work for building.
        # But wait, the task is to FIX the build scripts, not necessarily succeed in building in this environment if SDK is missing.
        echo "Warning: ANDROID_HOME not found. Creating a dummy one for configuration check."
        mkdir -p $HOME/android-sdk
        export ANDROID_HOME="$HOME/android-sdk"
    fi
fi

# Create local.properties
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Run build with gradle wrapper
echo "Starting Gradle build (clean assembleDebug)..."
# We expect this to fail if SDK is missing, but it should validate the gradle scripts.
./gradlew clean assembleDebug
