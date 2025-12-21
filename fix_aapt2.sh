#!/bin/bash

echo "🔧 Termux AAPT2 Fixer"

# 1. Install Android Tools (contains working aapt2)
echo "📦 Installing android-tools..."
pkg install android-tools -y

# 2. Find the path (Hardcoded for Termux robustness)
AAPT2_PATH="/data/data/com.termux/files/usr/bin/aapt2"

if [ ! -f "$AAPT2_PATH" ]; then
    echo "❌ Error: aapt2 not found at $AAPT2_PATH"
    echo "Please check if android-tools installed correctly."
    exit 1
fi

echo "✅ Found aapt2 at: $AAPT2_PATH"

# 3. Inject into gradle.properties
echo "📝 Configuring Gradle to use system AAPT2..."
echo "android.aapt2FromMavenOverride=$AAPT2_PATH" > gradle.properties

echo "✅ Done. You can now run 'bash init_sdk.sh'"
