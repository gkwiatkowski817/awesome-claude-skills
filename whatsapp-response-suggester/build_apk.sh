#!/bin/bash
set -e

# ============================================================
# Manual Android APK build script (no internet needed after setup)
# Tools used: aapt2, javac, kotlin-compiler-embeddable, r8/d8, apksigner, zipalign
# ============================================================

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="/tmp/wa-suggester-build"
APP_DIR="$PROJECT_DIR/app/src/main"

# Tool paths
ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
BUILD_TOOLS="/usr/lib/android-sdk/build-tools/29.0.3"
AAPT2="$BUILD_TOOLS/aapt2"
APKSIGNER_JAR="$BUILD_TOOLS/apksigner.jar"
ZIPALIGN="$BUILD_TOOLS/zipalign"
KLIB="/usr/share/kotlin/kotlinc/lib"
KOTLIN_STDLIB="$KLIB/kotlin-stdlib.jar"
KOTLIN_REFLECT="$KLIB/kotlin-stdlib-jdk8.jar"
R8_JAR="/tmp/r8.jar"
KEYSTORE="/tmp/wa_debug.keystore"
PACKAGE="com.whatsappsuggester"

echo "========================================"
echo " WA Suggester APK Builder"
echo "========================================"

# 1. Setup dirs
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{compiled_res,gen,classes,dex,apk}

# 2. Compile resources with aapt2
echo "[1/7] Compiling resources..."
find "$APP_DIR/res" -type f \( -name "*.xml" -o -name "*.png" -o -name "*.jpg" \) | while read f; do
    "$AAPT2" compile "$f" -o "$BUILD_DIR/compiled_res/" 2>/dev/null || true
done
# Collect all .flat files
FLAT_FILES=$(find "$BUILD_DIR/compiled_res" -name "*.flat" | tr '\n' ' ')
echo "  Compiled $(echo $FLAT_FILES | wc -w) resource files"

# 3. Link resources -> generates R.java + skeleton APK
echo "[2/7] Linking resources..."
"$AAPT2" link \
    -o "$BUILD_DIR/apk/app-unaligned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$APP_DIR/AndroidManifest.xml" \
    --java "$BUILD_DIR/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 29 \
    --version-code 1 \
    --version-name "1.0" \
    $FLAT_FILES
echo "  R.java generated, skeleton APK created"

# 4. Compile R.java with javac
echo "[3/7] Compiling R.java..."
find "$BUILD_DIR/gen" -name "*.java" | xargs javac \
    -cp "$ANDROID_JAR" \
    -d "$BUILD_DIR/classes" \
    -source 8 -target 8 2>&1
echo "  R.java compiled"

# 5. Compile Kotlin sources
echo "[4/7] Compiling Kotlin sources..."
KOTLIN_SOURCES=$(find "$APP_DIR/java" -name "*.kt" | tr '\n' ' ')
kotlinc \
    -jvm-target 1.8 \
    -cp "$ANDROID_JAR:$BUILD_DIR/classes" \
    -d "$BUILD_DIR/classes" \
    $KOTLIN_SOURCES
echo "  Kotlin sources compiled"

# 6. Convert .class files to DEX using R8/D8
# Pass stdlib JARs as positional args (not --classpath) so they're included in DEX
echo "[5/7] Converting to DEX (R8/D8)..."
CLASS_FILES=$(find "$BUILD_DIR/classes" -name "*.class" | tr '\n' ' ')
java -cp "$R8_JAR" com.android.tools.r8.D8 \
    --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/dex/" \
    --min-api 26 \
    $CLASS_FILES \
    "$KOTLIN_STDLIB" \
    "$KOTLIN_REFLECT"
echo "  DEX generated: $(du -sh $BUILD_DIR/dex/classes.dex | cut -f1)"

# 7. Add DEX + kotlin stdlib to APK
echo "[6/7] Packaging APK..."
cp "$BUILD_DIR/apk/app-unaligned.apk" "$BUILD_DIR/apk/app-pre-align.apk"
cd "$BUILD_DIR/dex" && zip -u "$BUILD_DIR/apk/app-pre-align.apk" classes.dex > /dev/null
echo "  DEX added to APK"

# Align
"$ZIPALIGN" -f -v 4 "$BUILD_DIR/apk/app-pre-align.apk" "$BUILD_DIR/apk/app-aligned.apk" > /dev/null
echo "  APK aligned"

# 8. Generate debug keystore if needed
if [ ! -f "$KEYSTORE" ]; then
    echo "[7/7] Generating debug keystore..."
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
fi

echo "[7/7] Signing APK..."
java -jar "$APKSIGNER_JAR" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$PROJECT_DIR/WA-Suggester-debug.apk" \
    "$BUILD_DIR/apk/app-aligned.apk"

echo ""
echo "========================================"
echo " BUILD SUCCESSFUL!"
echo " APK: $PROJECT_DIR/WA-Suggester-debug.apk"
echo " Size: $(du -sh "$PROJECT_DIR/WA-Suggester-debug.apk" | cut -f1)"
echo "========================================"
