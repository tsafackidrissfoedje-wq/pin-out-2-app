#!/bin/bash
set -e

APP_DIR="/data/data/com.termux/files/home/pin_out_2_project/android_app"
ANDROID_JAR="/data/data/com.termux/files/usr/share/java/android.jar"
FRAMEWORK_RES="/system/framework/framework-res.apk"
BUILD_DIR="$APP_DIR/build"
OBJ_DIR="$BUILD_DIR/obj"
APK_DIR="$BUILD_DIR/apk"
GEN_DIR="$BUILD_DIR/gen"

rm -rf "$OBJ_DIR" "$APK_DIR" "$GEN_DIR"
mkdir -p "$OBJ_DIR" "$APK_DIR" "$GEN_DIR"

echo "=== 1. Generating R.java with aapt ==="
aapt package -f -m \
    -J "$GEN_DIR" \
    -M "$APP_DIR/AndroidManifest.xml" \
    -S "$APP_DIR/res" \
    -I "$FRAMEWORK_RES"

echo "=== 2. Compiling Java sources with javac ==="
javac -source 8 -target 8 -Xlint:-options \
    -cp "$ANDROID_JAR" \
    -d "$OBJ_DIR" \
    $(find "$APP_DIR/src" "$GEN_DIR" -name "*.java")

echo "=== 3. Dexing with d8 ==="
d8 --output "$APK_DIR" \
    --lib "$ANDROID_JAR" \
    $(find "$OBJ_DIR" -name "*.class")

echo "=== 4. Packaging APK with aapt ==="
aapt package -f \
    -M "$APP_DIR/AndroidManifest.xml" \
    -S "$APP_DIR/res" \
    -A "$APP_DIR/assets" \
    -I "$FRAMEWORK_RES" \
    -F "$BUILD_DIR/PinOut2_unsigned.apk"

cd "$APK_DIR"
aapt add "$BUILD_DIR/PinOut2_unsigned.apk" classes.dex
cd "$APP_DIR"

echo "=== 5. Generating Signing Key if needed ==="
KEYSTORE="$BUILD_DIR/pinout2_release.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias pinout2 \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass pinout2026 -keypass pinout2026 \
        -dname "CN=AutoMind, OU=Automotive, O=PinOut2, L=Paris, ST=IDF, C=FR"
fi

echo "=== 6. Signing APK with apksigner ==="
FINAL_APK="/data/data/com.termux/files/home/pin_out_2_project/PinOut2.apk"
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias pinout2 \
    --ks-pass pass:pinout2026 \
    --key-pass pass:pinout2026 \
    --out "$FINAL_APK" \
    "$BUILD_DIR/PinOut2_unsigned.apk"

# Also copy to /storage/emulated/0/ for easy installation on Android device
mkdir -p "/storage/emulated/0/PinOut2" 2>/dev/null || true
cp "$FINAL_APK" "/storage/emulated/0/PinOut2/PinOut2.apk" 2>/dev/null || true
cp "$FINAL_APK" "/storage/emulated/0/PinOut2.apk" 2>/dev/null || true
cp "$FINAL_APK" "/storage/emulated/0/Download/PinOut2.apk" 2>/dev/null || true

echo "=== SUCCESS! APK GENERATED AT: $FINAL_APK ==="
ls -lh "$FINAL_APK"
