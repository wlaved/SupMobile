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

# Ensure file exists
touch gradle.properties

# Check if line exists, if not append it
if ! grep -q "android.aapt2FromMavenOverride" gradle.properties; then
    echo "android.aapt2FromMavenOverride=$AAPT2_PATH" >> gradle.properties
    echo "✅ Appended AAPT2 override path."
else
    echo "⚠️  AAPT2 override already exists in gradle.properties."
fi

# Ensure AndroidX properties exist (just in case they were deleted or file was fresh)
if ! grep -q "android.useAndroidX" gradle.properties; then
    echo "android.useAndroidX=true" >> gradle.properties
    echo "android.enableJetifier=true" >> gradle.properties
    echo "✅ Appended AndroidX flags."
fi

echo "✅ Done. You can now run 'bash init_sdk.sh'"
