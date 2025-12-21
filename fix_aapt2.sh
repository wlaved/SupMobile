#!/bin/bash

echo "🔧 Termux AAPT2 Fixer"

# 1. Install Android Tools (contains working aapt2)
echo "📦 Installing android-tools..."
pkg install android-tools -y

# 2. Find the path
AAPT2_PATH=$(which aapt2)

if [ -z "$AAPT2_PATH" ]; then
    echo "❌ Error: aapt2 not found even after installation."
    exit 1
fi

echo "✅ Found aapt2 at: $AAPT2_PATH"

# 3. Inject into gradle.properties
echo "📝 Configuring Gradle to use system AAPT2..."
# We append/overwrite the property
echo "android.aapt2FromMavenOverride=$AAPT2_PATH" > gradle.properties

echo "✅ Done. You can now run 'bash init_sdk.sh' or 'gradle assembleDebug'"
