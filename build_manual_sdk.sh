#!/bin/bash
set -e

# ==============================================================================
# build_manual_sdk.sh
# Manual Android SDK Toolchain Pipeline (aapt2, d8, zipalign, apksigner)
# ==============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}    Manual SDK Toolchain Build Pipeline              ${NC}"
echo -e "${BLUE}    (aapt2 -> d8 -> zipalign -> apksigner)           ${NC}"
echo -e "${BLUE}=====================================================${NC}"

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android/sdk}}"
if [ ! -d "$SDK_DIR" ] && [ -d "/usr/local/lib/android/sdk" ]; then
    SDK_DIR="/usr/local/lib/android/sdk"
fi

BUILD_TOOLS_DIR=$(find "$SDK_DIR/build-tools" -maxdepth 1 -mindepth 1 2>/dev/null | sort -V | tail -n 1)
PLATFORM_JAR=$(find "$SDK_DIR/platforms" -name "android.jar" 2>/dev/null | sort -V | tail -n 1)

if [ -z "$BUILD_TOOLS_DIR" ] || [ -z "$PLATFORM_JAR" ]; then
    echo -e "${RED}[!] Android SDK Build Tools atau android.jar tidak ditemukan di $SDK_DIR${NC}"
    exit 1
fi

AAPT2="$BUILD_TOOLS_DIR/aapt2"
D8="$BUILD_TOOLS_DIR/d8"
ZIPALIGN="$BUILD_TOOLS_DIR/zipalign"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"

echo -e "${YELLOW}[1/5] Verifikasi Toolchain Android SDK...${NC}"
for TOOL in "$AAPT2" "$D8" "$ZIPALIGN" "$APKSIGNER"; do
    if [ ! -x "$TOOL" ]; then
        echo -e "${RED}[!] Tool tidak ditemukan / executable: $TOOL${NC}"
        exit 1
    fi
done
echo -e "${GREEN}[✓] Toolchain siap: AAPT2, D8, ZIPALIGN, APKSIGNER terdeteksi.${NC}"

WORK_DIR="tmp/manual_sdk_build"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/compiled_res" "$WORK_DIR/apk_unaligned" "$WORK_DIR/dex"

# 1. Kompilasi Resource menggunakan AAPT2
echo -e "${YELLOW}[2/5] Mengompilasi dan mengemas resource aplikasi via AAPT2...${NC}"
"$AAPT2" compile --dir app/src/main/res -o "$WORK_DIR/compiled_res.zip"

mkdir -p "$WORK_DIR/manifest"
# Pastikan manifest memiliki package valid com.example agar activity component terpeta dengan benar
sed 's/<manifest/<manifest package="com.example"/' app/src/main/AndroidManifest.xml > "$WORK_DIR/manifest/AndroidManifest.xml"

"$AAPT2" link \
    -I "$PLATFORM_JAR" \
    --manifest "$WORK_DIR/manifest/AndroidManifest.xml" \
    --min-sdk-version 26 \
    --target-sdk-version 35 \
    --version-code 1 \
    --version-name "3.8.7" \
    --custom-package com.example \
    --auto-add-overlay \
    -o "$WORK_DIR/base_res.apk" \
    "$WORK_DIR/compiled_res.zip"

echo -e "${GREEN}[✓] Resource APK berhasil ditautkan oleh AAPT2.${NC}"

# 2. Dexing Bytecode menggunakan D8
echo -e "${YELLOW}[3/5] Memproses bytecode / JAR menjadi Dalvik Executable (classes.dex) via D8...${NC}"

# Cari DEX lengkap yang sudah dioptimasi atau build via toolchain
if [ -f "app/build/intermediates/dex/release/minifyReleaseWithR8/classes.dex" ]; then
    echo -e "${GREEN}[*] Menggunakan release optimized DEX artifacts...${NC}"
    cp app/build/intermediates/dex/release/minifyReleaseWithR8/classes*.dex "$WORK_DIR/dex/"
elif [ -f "app/build/intermediates/dex/debug/minifyDebugWithR8/classes.dex" ]; then
    echo -e "${GREEN}[*] Menggunakan optimized DEX artifacts...${NC}"
    cp app/build/intermediates/dex/debug/minifyDebugWithR8/classes*.dex "$WORK_DIR/dex/"
else
    CLASSES_SRC="app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes"
    KOTLIN_SRC="app/build/tmp/kotlin-classes/release"
    
    if [ ! -d "$KOTLIN_SRC" ]; then
        echo -e "${YELLOW}[i] Menyiapkan class files bytecode untuk pemrosesan D8...${NC}"
        if command -v gradle >/dev/null 2>&1; then
            gradle :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --no-daemon -q
        elif [ -f "./gradlew" ]; then
            chmod +x ./gradlew
            ./gradlew :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --no-daemon -q
        fi
    fi

    # Eksekusi D8 compiler
    echo -e "${YELLOW}[*] Menjalankan D8 Compiler ke $WORK_DIR/dex...${NC}"
    "$D8" --release --min-api 26 \
        --lib "$PLATFORM_JAR" \
        --output "$WORK_DIR/dex" \
        $(find "$KOTLIN_SRC" "$CLASSES_SRC" -name "*.class" 2>/dev/null)
fi

echo -e "${GREEN}[✓] File DEX berhasil dihasilkan oleh D8.${NC}"

# 3. Penggabungan DEX dan Java ServiceLoader Resources ke dalam Base APK
echo -e "${YELLOW}[4/5] Menggabungkan classes.dex dan ServiceLoader resources ke dalam package APK...${NC}"
cp "$WORK_DIR/base_res.apk" "$WORK_DIR/unaligned.apk"

# Ekstrak merged java resources jika ada (khususnya META-INF/services/ untuk Coroutines MainDispatcher)
JAVA_RES_JAR=$(find app/build/intermediates/merged_java_res -name "base.jar" 2>/dev/null | head -n 1)
mkdir -p "$WORK_DIR/java_res/META-INF/services"
echo "kotlinx.coroutines.android.AndroidDispatcherFactory" > "$WORK_DIR/java_res/META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory"

if [ -f "$JAVA_RES_JAR" ]; then
    cd "$WORK_DIR/java_res"
    jar xf "$(pwd)/../../$JAVA_RES_JAR" 2>/dev/null || true
    cd - > /dev/null
fi

python3 -c "
import zipfile, os, glob
apk_path = os.path.abspath('$WORK_DIR/unaligned.apk')
with zipfile.ZipFile(apk_path, 'a') as apk:
    # 1. Tambahkan seluruh classes.dex
    for dex_file in glob.glob('$WORK_DIR/dex/classes*.dex'):
        apk.write(dex_file, os.path.basename(dex_file))
    # 2. Tambahkan META-INF/services dan kotlin builtins
    for root, dirs, files in os.walk('$WORK_DIR/java_res'):
        for file in files:
            full_path = os.path.join(root, file)
            arcname = os.path.relpath(full_path, '$WORK_DIR/java_res')
            if arcname.startswith('META-INF/services') or arcname.endswith('.kotlin_builtins'):
                apk.write(full_path, arcname)
"

# 4. Zipalign (Optimasi 4-byte boundary)
echo -e "${YELLOW}[5/5] Menjalankan Zipalign & Apksigner...${NC}"
"$ZIPALIGN" -f -p 4 "$WORK_DIR/unaligned.apk" "$WORK_DIR/aligned.apk"

# 5. Apksigner dengan debug.keystore
KEYSTORE="debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    KEYSTORE="app/debug.keystore"
fi

"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "Chrotium-SDK-Manual.apk" \
    "$WORK_DIR/aligned.apk"

"$APKSIGNER" verify "Chrotium-SDK-Manual.apk"
cp -f "Chrotium-SDK-Manual.apk" "Chrotium.apk"
mkdir -p "app/build/outputs/apk/release"
cp -f "Chrotium-SDK-Manual.apk" "app/build/outputs/apk/release/Chrotium-release.apk"
cp -f "Chrotium-SDK-Manual.apk" "app/build/outputs/apk/release/Chrotium-v3.8.7.apk"

echo -e "${GREEN}[✓] APK Berhasil ditandatangani dan diverifikasi oleh apksigner!${NC}"
echo -e "${BLUE}Output File: Chrotium-SDK-Manual.apk & Chrotium.apk ($(du -sh Chrotium-SDK-Manual.apk | cut -f1))${NC}"
