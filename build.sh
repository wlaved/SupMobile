#!/bin/bash
set -e
rm -rf obj bin
mkdir -p obj bin
echo "1. Compiling Resources..."
aapt2 compile --dir res -o obj/resources.zip
aapt2 link -I ../android.jar --manifest AndroidManifest.xml --java src -o bin/resources.apk obj/resources.zip --auto-add-overlay
echo "2. Compiling Java..."
ecj -d obj -cp ../android.jar -sourcepath src $(find src -name "*.java")
echo "3. Converting to DEX..."
dx --dex --output=bin/classes.dex obj
echo "4. Packaging APK..."
cd bin
unzip -q resources.apk -d unzipped_apk
cp classes.dex unzipped_apk/
cd unzipped_apk
zip -r ../SupMobile.unsigned.apk * > /dev/null
cd ..
rm -rf unzipped_apk
cd ..
echo "5. Signing APK..."
if [ ! -f debug.keystore ]; then
    keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
fi
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out SupMobile.apk bin/SupMobile.unsigned.apk
echo "SUCCESS! APK is at SupMobile/SupMobile.apk"
