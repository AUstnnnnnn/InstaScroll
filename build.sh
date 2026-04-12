#!/bin/bash
set -e

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

SDK="$HOME/Library/Android/sdk"
BT="$SDK/build-tools/36.1.0"
PLATFORM="$SDK/platforms/android-36/android.jar"
SRC="app/src/main"
BUILD="build"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/obj" "$BUILD/apk"

echo "==> Compiling resources (aapt2)..."
"$BT/aapt2" compile --dir "$SRC/res" -o "$BUILD/res.zip"

echo "==> Linking resources..."
"$BT/aapt2" link \
    -o "$BUILD/apk/app.unaligned.apk" \
    -I "$PLATFORM" \
    --manifest "$SRC/AndroidManifest.xml" \
    --java "$BUILD/gen" \
    "$BUILD/res.zip" \
    --auto-add-overlay

echo "==> Compiling Java..."
find "$SRC/java" "$BUILD/gen" -name "*.java" > "$BUILD/sources.txt"
javac \
    -source 11 -target 11 \
    -classpath "$PLATFORM" \
    -d "$BUILD/obj" \
    @"$BUILD/sources.txt"

echo "==> Creating DEX..."
"$BT/d8" \
    --release \
    --lib "$PLATFORM" \
    --output "$BUILD/classes.zip" \
    $(find "$BUILD/obj" -name "*.class")

echo "==> Adding DEX to APK..."
# Extract the dex from d8 output and add to APK
cd "$BUILD"
unzip -o classes.zip classes.dex
cp apk/app.unaligned.apk apk/app.apk
zip -j apk/app.apk classes.dex
cd ..

echo "==> Aligning APK..."
"$BT/zipalign" -f 4 "$BUILD/apk/app.apk" "$BUILD/apk/app.aligned.apk"

echo "==> Signing APK..."
# Create a debug keystore if it doesn't exist
KEYSTORE="debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -storepass android \
        -keypass android \
        -alias debug \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Debug"
fi

"$BT/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias debug \
    --out "$BUILD/InstaScroll.apk" \
    "$BUILD/apk/app.aligned.apk"

echo ""
echo "==> BUILD SUCCESSFUL"
echo "    APK: $BUILD/InstaScroll.apk"
echo ""
echo "Install with: adb install $BUILD/InstaScroll.apk"
