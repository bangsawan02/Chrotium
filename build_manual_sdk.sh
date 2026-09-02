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

SDK_DIR="${ANDROID_SDK_ROOT:-/opt/android/sdk}"
BUILD_TOOLS_DIR="$SDK_DIR/build-tools/36.0.0"
PLATFORM_JAR="$SDK_DIR/platforms/android-35/android.jar"

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
sed 's/<manifest/<manifest package="org.matrix.chromext"/' app/src/main/AndroidManifest.xml > "$WORK_DIR/manifest/AndroidManifest.xml"

"$AAPT2" link \
    -I "$PLATFORM_JAR" \
    --manifest "$WORK_DIR/manifest/AndroidManifest.xml" \
    -o "$WORK_DIR/base_res.apk" \
    --auto-add-overlay \
    "$WORK_DIR/compiled_res.zip"

echo -e "${GREEN}[✓] Resource APK berhasil ditautkan oleh AAPT2.${NC}"

# 2. Dexing Bytecode menggunakan D8
echo -e "${YELLOW}[3/5] Memproses bytecode / JAR menjadi Dalvik Executable (classes.dex) via D8...${NC}"
# Kumpulkan semua class files / JAR hasil kompilasi
CLASSES_SRC="app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes"
KOTLIN_SRC="app/build/tmp/kotlin-classes/release"

if [ ! -d "$KOTLIN_SRC" ]; then
    echo -e "${YELLOW}[i] Menyiapkan class files bytecode untuk pemrosesan D8...${NC}"
    # Jalankan kompilasi bytecode Kotlin/Java
    gradle :app:compileReleaseKotlin :app:compileReleaseJavaWithJavac --no-daemon -q
fi

# Cari seluruh runtime JAR dependency
JAR_LIST="$WORK_DIR/jars.txt"
find /opt/gradle/.gradle/caches/modules-2/files-2.1 -name "*.jar" > "$JAR_LIST" 2>/dev/null || true

# Eksekusi D8 compiler
echo -e "${YELLOW}[*] Menjalankan D8 Compiler ke $WORK_DIR/dex...${NC}"
"$D8" --release --min-api 26 \
    --lib "$PLATFORM_JAR" \
    --output "$WORK_DIR/dex" \
    $(find "$KOTLIN_SRC" "$CLASSES_SRC" -name "*.class" 2>/dev/null)

echo -e "${GREEN}[✓] File DEX berhasil dihasilkan oleh D8.${NC}"

# 3. Penggabungan DEX ke dalam Base APK
echo -e "${YELLOW}[4/5] Menggabungkan classes.dex ke dalam package APK...${NC}"
cp "$WORK_DIR/base_res.apk" "$WORK_DIR/unaligned.apk"
cd "$WORK_DIR/dex"
if command -v zip >/dev/null 2>&1; then
    zip -u -q "../unaligned.apk" classes*.dex
else
    python3 -c "import zipfile, glob; z = zipfile.ZipFile('../unaligned.apk', 'a'); [z.write(f, f) for f in glob.glob('classes*.dex')]; z.close()"
fi
cd - > /dev/null

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
echo -e "${GREEN}[✓] APK Berhasil ditandatangani dan diverifikasi oleh apksigner!${NC}"
echo -e "${BLUE}Output File: Chrotium-SDK-Manual.apk ($(du -sh Chrotium-SDK-Manual.apk | cut -f1))${NC}"
